package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.pkmngen.game.util.SpriteProxy;
import com.pkmngen.game.util.TextureCache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Canonical data and permanent fallback sprites for additional exact PMD species. */
public final class ExpansionDex {
    private static Map<String, JsonValue> entries;
    private static final List<Texture> ownedTextures = new ArrayList<>();
    private ExpansionDex() {}

    private static synchronized Map<String, JsonValue> entries() {
        if (entries != null) return entries;
        entries = new LinkedHashMap<>();
        FileHandle file = Gdx.files.internal("visual/dex/expansion-dex.json");
        if (!file.exists()) return entries;
        JsonValue species = new JsonReader().parse(file).get("species");
        for (JsonValue value = species.child; value != null; value = value.next) entries.put(value.name, value);
        for (Map.Entry<String, JsonValue> item : entries.entrySet()) {
            String name = item.getKey();
            JsonValue value = item.getValue();
            Pokemon.baseSpecies.put(name, value.getString("baseSpecies"));
            Pokemon.weights.put(index(value), value.getFloat("weightKg"));
            Pokemon.eggMoves.put(name, new ArrayList<>(Arrays.asList(value.get("eggMoves").asStringArray())));
            Map<String, String> evolutions = new TreeMap<>();
            for (JsonValue evolution = value.get("evolutions").child; evolution != null; evolution = evolution.next)
                evolutions.put(evolution.name, evolution.asString());
            Specie.gen2Evos.put(name, evolutions);
        }
        return entries;
    }

    public static boolean contains(String name) { return name != null && entries().containsKey(name.toLowerCase(Locale.ROOT)); }
    public static List<String> names() { return Collections.unmodifiableList(new ArrayList<>(entries().keySet())); }
    /** Pinned PokeAPI habitat metadata: zero means absent, never an inferred habitat. */
    public static int habitatId(String name) {
        JsonValue value = entries().get(name.toLowerCase(Locale.ROOT));
        return value == null ? 0 : value.getInt("habitatId", 0);
    }

    public static boolean allowsHabitat(String name, WildSpawnRules.Habitat habitat) {
        switch (habitatId(name)) {
            case 1: return !habitat.water && habitat.tags.contains("cave");
            case 2: return !habitat.water && habitat.tags.contains("woodland");
            case 3: return !habitat.water && (habitat.profile.id.equals("plains") || habitat.profile.id.equals("savanna"));
            case 4: return !habitat.water && (habitat.profile.id.equals("mountain") || habitat.profile.id.equals("snow"));
            case 5: return false; // Rare authored encounters are not ordinary random spawns.
            case 6: return !habitat.water && (habitat.tags.contains("arid") || habitat.tags.contains("rocky"));
            case 7: return habitat.tags.contains("saltwater") && (habitat.water || habitat.wetness > 0);
            case 8: return !habitat.water && habitat.tags.contains("interior");
            case 9: return habitat.water || habitat.wetness > 0;
            default: return true; // Upstream/unknown data keeps its authored route defaults.
        }
    }

    private static boolean habitatRoute(int habitat, String route) {
        switch (habitat) {
            case 2: return route.equals("forest1") || route.equals("wooded_lake1") || route.equals("deep_forest");
            case 3: return route.equals("savanna2");
            case 4: return route.equals("mountain1");
            case 6: return route.equals("desert1") || route.equals("mountain1");
            case 7: return route.equals("beach2_water") || route.equals("ocean1");
            case 9: return route.equals("wooded_lake1") || route.equals("wooded_lake_water1");
            // Cave, urban and rare pools are authored special/dungeon encounters.
            // Do not populate their empty or progression-sensitive route lists.
            default: return false;
        }
    }
    public static String dexNumber(String name) {
        JsonValue value = entries().get(name);
        return value == null ? null : index(value);
    }
    private static String index(JsonValue value) { return String.format(Locale.ROOT, "%03d", value.getInt("dex")); }
    public static String cryPath(String name) {
        JsonValue value = entries().get(name);
        return value == null ? null : value.getString("cry", null);
    }

    public static boolean initialize(Specie specie, String name, Pokemon.Generation generation) {
        JsonValue value = entries().get(name.toLowerCase(Locale.ROOT));
        if (value == null) return false;
        specie.name = name.toLowerCase(Locale.ROOT);
        specie.generation = generation;
        specie.dexNumber = index(value);
        specie.types = new ArrayList<>(Arrays.asList(value.get("types").asStringArray()));
        if (specie.types.size() == 1) specie.types.add(specie.types.get(0));
        int[] stats = value.get("stats").asIntArray();
        String[] keys = {"hp", "attack", "defense", "specialAtk", "specialDef", "speed"};
        for (int i = 0; i < keys.length; i++) specie.baseStats.put(keys[i], stats[i]);
        specie.baseStats.put("catchRate", value.getInt("catchRate"));
        specie.baseStats.put("baseExp", value.getInt("baseExp"));
        specie.baseHappiness = value.getInt("baseHappiness");
        specie.eggCycles = value.getInt("eggCycles");
        specie.eggGroups = value.get("eggGroups").asStringArray();
        if (specie.eggGroups.length == 1) specie.eggGroups = new String[]{specie.eggGroups[0], specie.eggGroups[0]};
        specie.growthRateGroup = value.getString("growthRate");
        String[] ratios = {"GENDER_F0", "GENDER_F12_5", "GENDER_F25", "GENDER_F37_5", "GENDER_F50",
            "GENDER_F62_5", "GENDER_F75", "GENDER_F87_5", "GENDER_F100"};
        int gender = value.getInt("genderRate");
        specie.genderRatio = gender < 0 ? "GENDER_UNKNOWN" : ratios[gender];
        specie.cryLengthInFrames = value.getInt("cryFrames", 1);
        specie.learnSet = new TreeMap<>();
        for (JsonValue moves = value.get("learnset").child; moves != null; moves = moves.next)
            specie.learnSet.put(Integer.parseInt(moves.name), moves.asStringArray());
        Specie.gen2Attacks.put(specie.name, specie.learnSet);
        loadSprites(specie);
        Map<String, String> fieldMoves = new LinkedHashMap<>();
        fieldMoves.put("GROUND", "DIG"); fieldMoves.put("ELECTRIC", "POWER"); fieldMoves.put("GRASS", "CUT");
        fieldMoves.put("ROCK", "SMASH"); fieldMoves.put("FIGHTING", "BUILD"); fieldMoves.put("FAIRY", "CHARM");
        fieldMoves.put("POISON", "REPEL"); fieldMoves.put("FIRE", "FLASH"); fieldMoves.put("DARK", "ATTACK");
        fieldMoves.put("PSYCHIC", "TELEPORT");
        for (Map.Entry<String, String> field : fieldMoves.entrySet()) if (specie.types.contains(field.getKey())) specie.hms.add(field.getValue());
        if (specie.types.contains("WATER") && Specie.gen2Evos.get(specie.name).isEmpty()) specie.hms.add("SURF");
        specie.initHabitatValues();
        return true;
    }

    private static void loadSprites(Specie specie) {
        PmdPokemonSprites assets = PmdPokemonSprites.get();
        PmdPokemonSprites.Frame front = assets.frame(specie.name, "down", "Idle", 0);
        if (front == null) throw new IllegalStateException("Missing exact PMD sprite for expansion species " + specie.name);
        specie.sprite = proxy(front, 56);
        PmdPokemonSprites.Frame back = assets.frame(specie.name, "up", "Idle", 0);
        if (back == null) throw new IllegalStateException("Missing exact PMD back sprite for " + specie.name);
        specie.backSprite = proxy(back, 48);
        PmdPokemonSprites.Frame shinyFront = assets.frame(specie.name + "#shiny", "down", "Idle", 0);
        PmdPokemonSprites.Frame shinyBack = assets.frame(specie.name + "#shiny", "up", "Idle", 0);
        // Missing shiny art preserves the correct species' normal art, never another species.
        specie.spriteShiny = shinyFront == null ? specie.sprite : proxy(shinyFront, 56);
        specie.backSpriteShiny = shinyBack == null ? specie.backSprite : proxy(shinyBack, 48);
        specie.introAnim = new ArrayList<>(Collections.singletonList(specie.sprite));
        specie.introAnimShiny = new ArrayList<>(Collections.singletonList(specie.spriteShiny));
        for (String direction : new String[]{"up", "down", "left", "right"}) {
            Sprite idle = sprite(assets.frame(specie.name, direction, "Idle", 0), 16);
            Sprite walk = sprite(assets.frame(specie.name, direction, "Walk", .10f), 16);
            Sprite alternate = sprite(assets.frame(specie.name, direction, "Walk", .23f), 16);
            for (String key : new String[]{direction, direction + "_running"}) {
                specie.standingSprites.put(key, idle);
                specie.movingSprites.put(key, walk);
                specie.altMovingSprites.put(key, alternate);
            }
        }
        specie.avatarSprites.add(specie.standingSprites.get("down"));
        specie.avatarSprites.add(specie.movingSprites.get("down"));
        specie.avatarSprites.add(specie.standingSprites.get("down"));
        specie.avatarSprites.add(specie.altMovingSprites.get("down"));
        Texture eggs = TextureCache.get(Gdx.files.internal("pokemon/egg1.png"));
        for (String direction : new String[]{"up", "down", "left", "right"}) {
            specie.standingSpritesEgg.put(direction, new Sprite(eggs, 0, 0, 16, 16));
            specie.movingSpritesEgg.put(direction, new Sprite(eggs, 0, 32, 16, 16));
            specie.altMovingSpritesEgg.put(direction, new Sprite(eggs, 0, 48, 16, 16));
        }
        specie.avatarSpritesEgg.add(specie.standingSpritesEgg.get("down"));
        specie.avatarSpritesEgg.add(new Sprite(eggs, 0, 16, 16, 16));
        specie.avatarSpritesEgg.add(specie.standingSpritesEgg.get("down"));
        specie.avatarSpritesEgg.add(new Sprite(eggs, 0, 16, 16, 16));
    }

    private static SpriteProxy proxy(PmdPokemonSprites.Frame frame, int size) {
        return new SpriteProxy(copy(frame, size), 0, 0, size, size);
    }
    private static Sprite sprite(PmdPokemonSprites.Frame frame, int size) {
        return new Sprite(copy(frame, size), 0, 0, size, size);
    }
    private static Texture copy(PmdPokemonSprites.Frame frame, int size) {
        if (frame == null) throw new IllegalStateException("Incomplete PMD directional sprite");
        TextureRegion region = frame.region;
        TextureData data = region.getTexture().getTextureData();
        if (!data.isPrepared()) data.prepare();
        Pixmap source = data.consumePixmap();
        Pixmap result = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        result.setFilter(Pixmap.Filter.NearestNeighbour);
        result.setBlending(Pixmap.Blending.None);
        float scale = Math.min((size - 2f) / region.getRegionWidth(), (size - 2f) / region.getRegionHeight());
        int width = Math.max(1, Math.round(region.getRegionWidth() * scale));
        int height = Math.max(1, Math.round(region.getRegionHeight() * scale));
        result.drawPixmap(source, region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(),
            (size - width) / 2, size - height - 1, width, height);
        if (data.disposePixmap()) source.dispose();
        Texture texture = new Texture(result);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        ownedTextures.add(texture);
        // Pixmap remains owned by this TextureData for SpriteProxy's effect construction.
        return texture;
    }

    /** One-time route registration. Legends/mythicals and unavailable exact assets never spawn. */
    public static void appendSpawns(String route, List<String> target) {
        for (Map.Entry<String, JsonValue> item : entries().entrySet()) {
            JsonValue value = item.getValue();
            if (!value.getBoolean("spawnable") || value.getInt("evolvesFrom") != 0) continue;
            List<String> types = Arrays.asList(value.get("types").asStringArray());
            int habitat = value.getInt("habitatId", 0);
            // Habitat records are incomplete for later generations. Keep the old
            // type-derived route defaults only for those explicitly unknown records.
            boolean matches = habitat != 0 ? habitatRoute(habitat, route)
                : route.equals("volcano1") ? types.contains("FIRE")
                : route.equals("snow1") ? types.contains("ICE")
                : route.equals("graveyard1") ? types.contains("GHOST")
                : route.equals("desert1") ? types.contains("GROUND") || types.contains("ROCK")
                : route.equals("mountain1") ? types.contains("ROCK") || types.contains("STEEL") || types.contains("FIGHTING")
                : route.equals("deep_forest") ? types.contains("DARK") || types.contains("GHOST")
                : route.equals("forest1") || route.equals("wooded_lake1") ? types.contains("GRASS") || types.contains("BUG") || types.contains("FAIRY")
                : route.equals("savanna2") ? types.contains("NORMAL") || types.contains("ELECTRIC")
                : route.equals("wooded_lake_water1") || route.equals("beach2_water") ? types.contains("WATER") : false;
            if (matches && !target.contains(item.getKey())) target.add(item.getKey());
        }
    }

    /** Exact canonical growth for new species, leaving the existing species' rules untouched. */
    public static int experience(String name, int level) {
        JsonValue value = entries().get(name);
        if (value == null) return -1;
        long cube = (long)level * level * level;
        switch (value.getString("growthRate")) {
            case "GROWTH_FAST": return (int)(cube * 4 / 5);
            case "GROWTH_MEDIUM_FAST": return (int)cube;
            case "GROWTH_MEDIUM_SLOW": return Math.max(0, (int)(cube * 6 / 5 - 15 * level * level + 100 * level - 140));
            case "GROWTH_SLOW": return (int)(cube * 5 / 4);
            case "GROWTH_ERRATIC":
                if (level <= 50) return (int)(cube * (100 - level) / 50);
                if (level <= 68) return (int)(cube * (150 - level) / 100);
                if (level <= 98) return (int)(cube * ((1911 - 10 * level) / 3) / 500);
                return (int)(cube * (160 - level) / 100);
            case "GROWTH_FLUCTUATING":
                if (level <= 15) return (int)(cube * ((level + 1) / 3 + 24) / 50);
                if (level <= 36) return (int)(cube * (level + 14) / 50);
                return (int)(cube * (level / 2 + 32) / 50);
            default: throw new IllegalStateException("Unknown canonical growth group");
        }
    }

    public static void dispose() {
        for (Texture texture : ownedTextures) {
            Texture[] effects = TextureCache.effectsTextMap.remove(texture);
            if (effects != null) for (Texture effect : effects) disposeTexture(effect);
            TextureCache.colorsTextMap.remove(texture);
            disposeTexture(texture);
        }
        ownedTextures.clear();
    }
    private static void disposeTexture(Texture texture) {
        if (texture == null) return;
        TextureData data = texture.getTextureData();
        if (!data.isPrepared()) data.prepare();
        Pixmap pixmap = data.consumePixmap();
        texture.dispose(); pixmap.dispose();
    }
}
