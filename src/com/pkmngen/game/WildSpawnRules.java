package com.pkmngen.game;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Habitat gate for newly generated wild encounters, never for saved/owned Pokemon. */
public final class WildSpawnRules {
    private WildSpawnRules() {}

    public static final class Habitat {
        public final BiomeProfiles.Profile profile;
        public final Set<String> tags;
        public final String time;
        /** Fraction of the 5x5 neighborhood that is water; depth 0=dry, 1=edge, 2=enclosed. */
        public final float wetness;
        public final int depth;
        public final boolean water, lava;
        private Habitat(Tile tile, Map<Vector2, Tile> map, String time) {
            this(tile, map, time, BiomeProfiles.forTile(tile));
        }
        private Habitat(Tile tile, Map<Vector2, Tile> map, String time, BiomeProfiles.Profile profile) {
            this.profile = profile;
            this.time = time == null ? "day" : time.toLowerCase(Locale.ROOT);
            water = isWater(tile);
            lava = tile != null && tile.isLava;
            Set<String> found = new HashSet<>(profile.tags());
            found.add(water ? "water" : "dry");
            if (lava) found.add("lava");
            String route = tile == null || tile.routeBelongsTo == null ? "" : tile.routeBelongsTo.name;
            if (route.contains("ruins")) found.add("ruins");
            // Coastal profiles explicitly contain saltwater. Mountain pools and oasis ponds are fresh.
            if (water && !found.contains("saltwater")) found.add("freshwater");
            int waterCells = water ? 1 : 0, neighbors = 0;
            if (tile != null && tile.position != null && map != null) {
                Vector2 at = new Vector2();
                for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) {
                    if (x == 0 && y == 0) continue;
                    Tile other = map.get(at.set(tile.position.x + x * 16, tile.position.y + y * 16));
                    if (isWater(other)) { waterCells++; if (Math.abs(x) + Math.abs(y) == 1) neighbors++; }
                }
            }
            // On water itself wetness=1; dry shore wetness expresses water proximity.
            wetness = water ? 1f : waterCells / 25f;
            depth = !water ? 0 : neighbors == 4 ? 2 : 1;
            if (wetness > 0) found.add("near_water");
            tags = Collections.unmodifiableSet(found);
        }
    }

    public static Habitat habitat(Tile tile, Map<Vector2, Tile> map, String time) { return new Habitat(tile, map, time); }
    /** Fishing pools explicitly distinguish sea, fresh water and the authored sand-fishing mechanic. */
    public static Habitat fishingHabitat(Tile tile, Map<Vector2, Tile> map, String time, String route) {
        BiomeProfiles.Profile profile = route.equals("sand_fishing1") ? BiomeProfiles.named("desert")
            : route.equals("sea1") || route.equals("ocean1") ? BiomeProfiles.named("beach")
            : BiomeProfiles.named("wetland");
        return new Habitat(tile, map, time, profile);
    }
    public static boolean isWater(Tile tile) { return tile != null && (tile.isWater || tile.isWaterfall) && !tile.isLava; }

    /** The legacy swim-only list omitted Milotic and later species. Owned companions keep their controls. */
    public static boolean mustStayInWater(Pokemon pokemon) {
        if (pokemon == null || pokemon.previousOwner != null || pokemon.specie == null) return false;
        JsonValue rule = BiomeProfiles.speciesRule(pokemon.specie.name);
        if (rule == null || rule.get("requires") == null) return false;
        for (String tag : rule.get("requires").asStringArray()) if (tag.equals("water")) return true;
        return false;
    }

    public static boolean allows(String species, Habitat habitat) {
        if (species == null || habitat.lava) return false;
        if (!ExpansionDex.allowsHabitat(species, habitat)) return false;
        JsonValue rule = BiomeProfiles.speciesRule(species);
        if (rule == null) return true; // Route's existing species pool still defines unlisted habitats.
        if (!containsAll(habitat.tags, rule.get("requires"))) return false;
        if (containsAny(habitat.tags, rule.get("forbids"))) return false;
        if (rule.has("anyBiomeTags") && !containsAny(habitat.tags, rule.get("anyBiomeTags"))) return false;
        if (rule.has("nightUnlessTags") && !"night".equals(habitat.time)
            && !containsAny(habitat.tags, rule.get("nightUnlessTags"))) return false;
        return habitat.wetness >= rule.getFloat("minWetness", 0f) && habitat.depth >= rule.getInt("minDepth", 0);
    }

    /** Keeps duplicate route entries as their intentional relative rarity. No retry-loop bias. */
    public static String choose(Tile tile, Map<Vector2, Tile> map, String time, List<String> candidates, Random random) {
        if (tile == null || candidates.isEmpty()) return null;
        // These authored progression pools are also used by scripted interiors;
        // ordinary surface rules must not rewrite their intentional encounters.
        if (tile.routeBelongsTo != null && tile.routeBelongsTo.isDungeon)
            return candidates.get(random.nextInt(candidates.size()));
        Habitat habitat = habitat(tile, map, time);
        return chooseEligible(habitat, candidates, random);
    }

    public static String chooseFishing(Tile tile, Map<Vector2, Tile> map, String time, String route, List<String> candidates, Random random) {
        if (tile == null || candidates.isEmpty()) return null;
        Habitat habitat = fishingHabitat(tile, map, time, route);
        ArrayList<String> eligible = new ArrayList<>();
        // Fishing originally sampled rod-filtered entries uniformly. Preserve
        // duplicate entries and that distribution; land spawn weights do not apply.
        for (String name : candidates) if (allows(name, habitat)) eligible.add(name);
        return eligible.isEmpty() ? null : eligible.get(random.nextInt(eligible.size()));
    }

    private static String chooseEligible(Habitat habitat, List<String> candidates, Random random) {
        if (habitat.lava) return null;
        ArrayList<String> eligible = new ArrayList<>();
        ArrayList<Integer> weights = new ArrayList<>();
        int total = 0;
        for (String name : candidates) {
            if (!allows(name, habitat)) continue;
            JsonValue rule = BiomeProfiles.speciesRule(name);
            int weight = rule == null ? (int)habitat.profile.number("spawn", "defaultWeight", 20) : rule.getInt("weight", 20);
            if (weight <= 0) continue;
            eligible.add(name);
            weights.add(weight);
            total += weight;
        }
        if (total > 0) {
            int roll = random.nextInt(total);
            for (int index = 0; index < eligible.size(); index++) {
                roll -= weights.get(index);
                if (roll < 0) return eligible.get(index);
            }
        }
        // A filtered-empty pool means no encounter at this cell. Introducing a
        // different species here would change authored route membership/rarity.
        return null;
    }

    /** Preserve the unique oasis Milotic, correcting only its final generated cell after coast/topology passes. */
    public static void anchorOasisEncounter(Map<Vector2, Tile> tiles, Map<Vector2, Pokemon> generated) {
        for (Map.Entry<Vector2, Pokemon> entry : new ArrayList<>(generated.entrySet())) {
            Pokemon pokemon = entry.getValue();
            if (!"milotic".equals(pokemon.specie.name)) continue;
            Tile current = tiles.get(entry.getKey());
            if (isWater(current)) continue;
            Tile best = null;
            float distance = Float.POSITIVE_INFINITY;
            for (Tile tile : tiles.values()) {
                if (!isWater(tile) || tile.routeBelongsTo == null || !tile.routeBelongsTo.name.contains("oasis")
                    || generated.containsKey(tile.position)) continue;
                float next = tile.position.dst2(entry.getKey());
                if (next < distance || next == distance && best != null
                    && (tile.position.x < best.position.x || tile.position.x == best.position.x && tile.position.y < best.position.y)) {
                    best = tile; distance = next;
                }
            }
            if (best != null) {
                generated.remove(entry.getKey());
                pokemon.position = best.position.cpy();
                generated.put(pokemon.position.cpy(), pokemon);
                System.out.println("HABITAT: oasis Milotic anchored to water at " + pokemon.position);
            } else {
                // Keep the authored encounter identity; generation must fix the oasis, never erase it.
                System.err.println("HABITAT: oasis Milotic has no available water cell at " + entry.getKey());
            }
        }
    }

    private static boolean containsAll(Set<String> tags, JsonValue required) {
        if (required == null) return true;
        for (String value : required.asStringArray()) if (!tags.contains(value)) return false;
        return true;
    }
    private static boolean containsAny(Set<String> tags, JsonValue options) {
        if (options == null) return false;
        for (String value : options.asStringArray()) if (tags.contains(value)) return true;
        return false;
    }
}
