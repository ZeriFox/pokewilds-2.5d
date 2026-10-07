package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared visual and habitat definitions; never changes persisted Tile/Route data. */
public final class BiomeProfiles {
    private static Map<String, Profile> profiles;
    private static Map<String, JsonValue> speciesRules;
    private BiomeProfiles() {}

    private static synchronized void load() {
        if (profiles != null) return;
        JsonValue data = new JsonReader().parse(Gdx.files.internal("visual/biomes/profiles.json"));
        if (data.getInt("version") != 1) throw new IllegalStateException("Unsupported biome profile version");
        Map<String, Profile> loaded = new LinkedHashMap<>();
        for (JsonValue value = data.get("profiles").child; value != null; value = value.next) {
            for (String section : new String[]{"terrain", "cliff", "height", "fluid", "decor", "ambient", "spawn", "transition"})
                if (!value.has(section)) throw new IllegalStateException("Missing biome section " + value.name + "." + section);
            loaded.put(value.name, new Profile(value));
        }
        if (!loaded.containsKey("plains")) throw new IllegalStateException("Missing default plains biome");
        Map<String, JsonValue> rules = new LinkedHashMap<>();
        for (JsonValue value = data.get("speciesRules").child; value != null; value = value.next)
            for (String species : value.get("species").asStringArray()) {
                if (rules.put(lower(species), value) != null)
                    throw new IllegalStateException("Duplicate species habitat rule: " + species);
            }
        speciesRules = Collections.unmodifiableMap(rules);
        profiles = Collections.unmodifiableMap(loaded);
    }

    public static Profile named(String id) { load(); return profiles.getOrDefault(id, profiles.get("plains")); }
    public static List<Profile> all() { load(); return Collections.unmodifiableList(new ArrayList<>(profiles.values())); }
    static JsonValue speciesRule(String species) { load(); return speciesRules.get(lower(species)); }

    /** Material identity survives generic ledges/ramps, shore autotiling and old saves. */
    public static Profile forTile(Tile tile) {
        if (tile == null) return named("plains");
        String ground = lower(tile.name), upper = lower(tile.nameUpper), biome = lower(tile.biome);
        String route = tile.routeBelongsTo == null ? "" : lower(tile.routeBelongsTo.name);
        // Explicit structural cells retain their identity even at a biome boundary.
        if (ground.contains("floor") || ground.contains("rug") || ground.contains("carpet")
            || ground.startsWith("pkmnmansion") || ground.startsWith("building") || ground.startsWith("house"))
            return named(ground.contains("cave") ? "cave" : "interior");
        if (ground.startsWith("cave") || ground.equals("black1") || ground.equals("blank1")
            || route.equals("regi_cave1")) return named("cave");
        if (tile.isLava || ground.contains("lava") || ground.startsWith("volcano") || upper.contains("volcano")
            || biome.equals("volcano") || route.equals("volcano1")) return named("volcano");
        if (ground.contains("graveyard") || upper.contains("gravestone") || biome.equals("graveyard")
            || route.equals("graveyard1")) return named("graveyard");
        if (ground.contains("snow") || ground.startsWith("ice") || upper.contains("snow") || upper.contains("ledges3ice")
            || biome.equals("tundra") || biome.equals("snow") || route.equals("snow1")) return named("snow");
        if (route.contains("oasis")) return named("oasis");
        if (biome.equals("desert") || ground.startsWith("desert") || upper.contains("desert")
            || route.startsWith("desert") || route.startsWith("ruins") || route.equals("sand_pit1")) return named("desert");
        if (biome.equals("beach") || route.contains("beach") || route.equals("ocean1") || route.equals("sea1")) return named("beach");
        if (biome.equals("wooded_lake") || route.contains("wooded_lake") || route.equals("river1")) return named("wetland");
        if (biome.equals("deep_forest") || route.equals("deep_forest")) return named("deep_forest");
        if (biome.equals("savanna") || route.startsWith("savanna")) return named("savanna");
        if (biome.equals("mountain") || route.startsWith("mountain") || ground.startsWith("mountain")) return named("mountain");
        if (biome.contains("forest") || route.contains("forest")) return named("forest");
        if (ground.startsWith("sand")) return named("beach");
        return named("plains");
    }

    /** Visible materials only: never use this accessor for habitat or elevation.
     * Identity guard prevents inactive maps and generation fixtures borrowing the
     * active world's neighbors merely because their tile coordinates coincide. */
    public static Profile visualForTile(Tile tile) {
        Game game = Game.staticGame;
        if (tile != null && tile.position != null && game != null && game.map != null && game.map.tiles != null
            && game.map.tiles.get(tile.position) == tile) return forTile(tile, game.map.tiles);
        return forTile(tile);
    }

    /** Presentation-only context for legacy mansion approaches. The generator used
     * snow1 as their encounter route even when the mansion stood on a volcano.
     * Keep that saved encounter route intact and infer only the visible material.
     * Habitat selection deliberately uses the context-free forTile overload. */
    public static Profile forTile(Tile tile, Map<com.badlogic.gdx.math.Vector2, Tile> tiles) {
        Profile original = forTile(tile);
        if (tile == null || tile.position == null || tiles == null || !legacyMansionGround(tile)) return original;
        com.badlogic.gdx.math.Vector2 position = new com.badlogic.gdx.math.Vector2();
        for (int radius = 1; radius <= 3; radius++) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (int y = -radius; y <= radius; y++) for (int x = -radius; x <= radius; x++) {
                if (Math.max(Math.abs(x), Math.abs(y)) != radius) continue;
                Tile next = tiles.get(position.set(tile.position.x + x * 16, tile.position.y + y * 16));
                if (next == null || legacyMansionGround(next) || lower(next.nameUpper).contains("pkmnmansion")) continue;
                Profile profile = forTile(next);
                if (profile.id.equals("interior") || profile.id.equals("cave")) continue;
                counts.merge(profile.id, 1, Integer::sum);
            }
            int support = 0; String selected = null; boolean tied = false;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > support) { selected = entry.getKey(); support = entry.getValue(); tied = false; }
                else if (entry.getValue() == support) tied = true;
            }
            if (support >= 2 && !tied) return named(selected);
        }
        return original;
    }
    private static boolean legacyMansionGround(Tile tile) {
        String ground = lower(tile.name);
        return tile.routeBelongsTo != null && "snow1".equals(tile.routeBelongsTo.name)
            && lower(tile.biome).isEmpty()
            && (ground.equals("path1") || ground.equals("sand1") || ground.equals("green1") || ground.equals("flower4"));
    }

    private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }

    public static final class Profile {
        public final String id, label;
        private final JsonValue data;
        private final List<String> tags;
        private Profile(JsonValue data) {
            this.data = data;
            id = data.name;
            label = data.getString("label");
            List<String> values = new ArrayList<>();
            for (String value : data.get("tags").asStringArray()) values.add(value);
            tags = Collections.unmodifiableList(values);
        }
        public String terrain(String role) { return string("terrain", role, string("terrain", "ground", "grass_light")); }
        public String cliff(String role) { return string("cliff", role, "cliff"); }
        public String decor(String role) { return string("decor", role, role); }
        public String string(String section, String key, String fallback) {
            JsonValue value = data.get(section);
            return value == null ? fallback : value.getString(key, fallback);
        }
        public float number(String section, String key, float fallback) {
            JsonValue value = data.get(section);
            return value == null ? fallback : value.getFloat(key, fallback);
        }
        public List<String> tags() { return tags; }
        public List<String> strings(String section, String key) {
            JsonValue value = data.get(section);
            if (value == null || value.get(key) == null) return Collections.emptyList();
            List<String> values = new ArrayList<>();
            for (String item : value.get(key).asStringArray()) values.add(item);
            return Collections.unmodifiableList(values);
        }
    }
}
