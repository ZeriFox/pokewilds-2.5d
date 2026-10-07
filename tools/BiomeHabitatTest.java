package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.math.Vector2;
import com.pkmngen.leaks.LeakTracer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Real Tile, PlayerStanding and Pokemon construction, not a duplicate spawn implementation. */
public final class BiomeHabitatTest {
    private static Throwable failure;
    public static void main(String[] args) {
        Game.leakTracer = LeakTracer.NoOp.INSTANCE;
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false); config.setWindowedMode(320, 288); config.setForegroundFPS(30);
        new Lwjgl3Application(new TestGame(), config);
        if (failure != null) { failure.printStackTrace(); System.exit(1); }
        System.out.println("BIOME PASS: 14 profiles, native resolution, 20000 weighted choices, water/depth/time gates, actual PlayerStanding encounters, unique oasis identity preserved");
        System.exit(0);
    }
    private static final class TestGame extends Game {
        TestGame() { super(new String[0], 2); }
        @Override public void create() {
            try {
                super.create();
                actionStack.clear();
                map = new PkmnMap("habitat-fixture");
                map.rand = new Random(391);
                levelScalingEnabled = false;
                profiles(); habitat(); actualEncounters(); oasis(); waterMovement();
            } catch (Throwable error) { failure = error; }
            Gdx.app.exit();
        }
        @Override public void render() {}
        private void profiles() {
            check(BiomeProfiles.all().size() == 14, "Profile catalog incomplete");
            for (BiomeProfiles.Profile profile : BiomeProfiles.all()) {
                check(!profile.terrain("ground").isEmpty() && !profile.cliff("front").isEmpty(), "Missing materials " + profile.id);
                check(profile.number("height", "cliffHeight", -1) > 0, "Missing height " + profile.id);
                check(!profile.strings("spawn", "fallbackLand").isEmpty(), "No safe fallback " + profile.id);
            }
            Tile desert = tile("mountain3", "desert1"); desert.nameUpper = "ledges3_N";
            check(BiomeProfiles.forTile(desert).id.equals("desert"), "Generic desert cliff lost identity");
            desert.nameUpper = "ledges3volcano_N";
            check(BiomeProfiles.forTile(desert).id.equals("volcano"), "Volcano upper ledge ignored");
            desert.nameUpper = "ledges3snow_N";
            check(BiomeProfiles.forTile(desert).id.equals("snow"), "Snow upper ledge ignored");
            check(BiomeProfiles.forTile(tile("grass_graveyard1", "graveyard1")).id.equals("graveyard"), "Graveyard identity");
            check(BiomeProfiles.forTile(tile("water1", "oasis1")).id.equals("oasis"), "Oasis sub-biome");
            check(BiomeProfiles.named("unknown-future-biome").id.equals("plains"), "Unknown profile safe default");
            Map<Vector2, Tile> surroundings = new HashMap<>();
            Tile approach = tile("path1", "snow1");
            surroundings.put(approach.position.cpy(), approach);
            for (int dx : new int[]{-16, 16}) {
                Tile basalt = new Tile("volcano1", new Vector2(dx, 0), true, new Route("volcano1", 40));
                surroundings.put(basalt.position.cpy(), basalt);
            }
            check(BiomeProfiles.forTile(approach, surroundings).id.equals("volcano"), "Mansion route snow leaked into volcanic approach");
            map.tiles.clear(); map.tiles.putAll(surroundings);
            check(BiomeProfiles.visualForTile(approach).id.equals("volcano"), "Active material missed context");
            check(BiomeProfiles.visualForTile(tile("path1", "snow1")).id.equals("snow"), "Inactive tile borrowed active map neighbors");
            map.tiles.clear();

            check(BiomeProfiles.forTile(approach).id.equals("snow") && approach.routeBelongsTo.name.equals("snow1"), "Presentation changed encounter route");
            for (int dx : new int[]{-16, 16}) {
                Tile snow = new Tile("snow1", new Vector2(dx, 0), true, new Route("snow1", 40));
                surroundings.put(snow.position.cpy(), snow);
            }
            check(BiomeProfiles.forTile(approach, surroundings).id.equals("snow"), "Real snowy path lost snow");
            surroundings.clear();
            check(BiomeProfiles.forTile(approach, surroundings).id.equals("snow"), "Ambiguous approach lost saved context");
            Tile explicitSnow = tile("snow1", "snow1"); explicitSnow.biome = "snow";
            check(BiomeProfiles.forTile(explicitSnow, surroundings).id.equals("snow"), "Explicit snowy surface replaced");

        }
        private void habitat() {
            Tile dry = tile("desert2", "desert1"), water = tile("water1", "oasis1"), sea = tile("water1", "ocean1");
            WildSpawnRules.Habitat arid = WildSpawnRules.habitat(dry, map.tiles, "day");
            check(!WildSpawnRules.allows("milotic", arid) && !WildSpawnRules.allows("feebas", arid), "Fish on dry sand");
            check(WildSpawnRules.allows("trapinch", arid) && WildSpawnRules.allows("numel", arid), "Arid wildlife lost");
            check(!WildSpawnRules.allows("milotic", WildSpawnRules.habitat(sea, map.tiles, "day")), "Freshwater fish in ocean");
            map.tiles.put(water.position.cpy(), water);
            WildSpawnRules.Habitat edge = WildSpawnRules.habitat(water, map.tiles, "day");
            check(edge.depth == 1 && edge.wetness == 1 && WildSpawnRules.allows("milotic", edge), "Oasis water gate");
            for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++) {
                Tile next = new Tile("water1", new Vector2(x * 16, y * 16), true, water.routeBelongsTo);
                map.tiles.put(next.position.cpy(), next);
            }
            check(WildSpawnRules.habitat(water, map.tiles, "day").depth == 2, "Interior water depth");
            Tile shore = new Tile("sand1", new Vector2(48, 0), true, water.routeBelongsTo);
            WildSpawnRules.Habitat shoreHabitat = WildSpawnRules.habitat(shore, map.tiles, "day");
            check(shoreHabitat.wetness > 0 && shoreHabitat.wetness < 1 && !WildSpawnRules.allows("milotic", shoreHabitat), "Proximity cannot substitute actual water");
            Tile forest = tile("grass2", "forest1"), cemetery = tile("grass_graveyard1", "graveyard1");
            check(!WildSpawnRules.allows("gastly", WildSpawnRules.habitat(forest, map.tiles, "day")), "Daytime forest Gastly");
            check(WildSpawnRules.allows("gastly", WildSpawnRules.habitat(forest, map.tiles, "night")), "Night ghost absent");
            check(WildSpawnRules.allows("gastly", WildSpawnRules.habitat(cemetery, map.tiles, "day")), "Haunted habitat ignored");
            check(WildSpawnRules.choose(dry, map.tiles, "day", List.of("milotic"), new Random(1)).equals("trapinch"), "Invalid pool emptied biome");
            check(WildSpawnRules.choose(dry, map.tiles, "day", List.of(), new Random(1)) == null, "Scripted empty route populated");
            check(WildSpawnRules.choose(tile("lava1", "volcano1"), map.tiles, "day", List.of("numel"), new Random(1)) == null, "Wildlife on solid lava");
            Random first = new Random(87), second = new Random(87);
            int rare = 0;
            for (int i = 0; i < 20000; i++) {
                String a = WildSpawnRules.choose(water, map.tiles, "night", List.of("milotic", "magikarp"), first);
                String b = WildSpawnRules.choose(water, map.tiles, "night", List.of("milotic", "magikarp"), second);
                check(a.equals(b), "Fixed context/seed not deterministic");
                if (a.equals("milotic")) rare++;
            }
            check(rare > 700 && rare < 1200, "Rare weighting regressed: " + rare);
            System.out.println("BIOME rarity: " + rare + "/20000 Milotic choices");
        }
        private void actualEncounters() {
            map.rand = new Random(1) { @Override public int nextInt(int bound) { return 0; } };
            PlayerStanding standing = new PlayerStanding(this);
            Tile dry = tile("desert2", "desert1"); dry.isGrass = true; dry.items = new HashMap<>();
            map.tiles.clear(); map.tiles.put(dry.position.cpy(), dry); map.timeOfDay = "day";
            Route.allowedPokemon.put("desert1", new ArrayList<>(Arrays.asList("milotic", "numel")));
            Pokemon result = standing.checkWildEncounter(this, dry.position);
            check(result != null && (result.specie.name.equals("numel") || result.specie.name.equals("camerupt")), "Actual grass encounter ignored habitat");
            Route.allowedPokemon.put("desert1", new ArrayList<>(List.of("milotic")));
            result = standing.checkWildEncounter(this, dry.position);
            check(result != null && result.specie.name.equals("trapinch"), "Actual grass fallback failed");
            Tile forest = tile("grass2", "forest1"); forest.items = new HashMap<>();
            map.tiles.put(forest.position.cpy(), forest);
            Route.allowedPokemon.put("forest1", new ArrayList<>(List.of("gastly")));
            check(standing.checkWildEncounter(this, forest.position) == null, "Original daytime availability lost");
            map.timeOfDay = "night";
            result = standing.checkWildEncounter(this, forest.position);
            check(result != null && List.of("gastly", "haunter", "gengar").contains(result.specie.name), "Actual nighttime ghost missing");
            Route.allowedPokemon.remove("desert1"); Route.allowedPokemon.remove("forest1");
        }
        private void oasis() {
            Map<Vector2, Tile> tiles = new HashMap<>();
            Tile dry = tile("sand1", "oasis1"), pond = new Tile("water1", new Vector2(16, 0), true, dry.routeBelongsTo);
            tiles.put(dry.position.cpy(), dry); tiles.put(pond.position.cpy(), pond);
            Pokemon unique = new Pokemon("milotic", 44);
            unique.position = dry.position.cpy();
            Map<Vector2, Pokemon> encounters = new HashMap<>(); encounters.put(unique.position.cpy(), unique);
            Pokemon owned = new Pokemon("milotic", 18); player.pokemon.add(owned);
            WildSpawnRules.anchorOasisEncounter(tiles, encounters);
            check(encounters.size() == 1 && encounters.get(pond.position) == unique && unique.position.equals(pond.position), "Unique encounter replaced/lost");
            check(player.pokemon.contains(owned) && owned.level == 18, "Owned Pokemon touched");
            WildSpawnRules.anchorOasisEncounter(tiles, encounters);
            check(encounters.get(pond.position) == unique, "Oasis correction not idempotent");
        }
        private void waterMovement() {
            map.tiles.clear(); map.pokemon.clear(); actionStack.clear();
            Route oasis = new Route("oasis1", 30);
            Tile pond = new Tile("water1", new Vector2(16, 0), true, oasis);
            Tile sand = new Tile("sand1", new Vector2(32, 0), true, oasis);
            map.tiles.put(pond.position.cpy(), pond); map.tiles.put(sand.position.cpy(), sand);
            Pokemon wild = new Pokemon("milotic", 44);
            wild.position = pond.position.cpy(); wild.mapTiles = map.tiles; wild.inWater = true;
            wild.moveDirs.add("right"); wild.numMoves.add(1f);
            Action stopped = new WaitFrames(this, 1, null);
            Pokemon.FollowPath path = wild.new FollowPath(stopped); actionStack.add(path); path.step(this);
            check(wild.standingAction == stopped && wild.position.equals(pond.position), "Wild Milotic walked out of oasis");
            wild.previousOwner = player;
            check(!WildSpawnRules.mustStayInWater(wild), "Owned companion received wild habitat restriction");
            wild.moveDirs.add("right"); wild.numMoves.add(1f);
            path = wild.new FollowPath(stopped); actionStack.add(path); path.step(this);
            check(wild.standingAction instanceof Pokemon.Moving, "Owned Milotic lost original movement");
            actionStack.clear();
        }
    }
    private static Tile tile(String name, String route) { return new Tile(name, new Vector2(), true, new Route(route, 10)); }
    private static void check(boolean result, String message) { if (!result) throw new IllegalStateException(message); }
}
