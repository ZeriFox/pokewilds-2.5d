package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.pkmngen.leaks.LeakTracer;
import java.util.Objects;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Catalog introspection through actual Tile and BwAssets classes in the built JAR. */
public final class AssetCoverageTest {
    private static Throwable failure;
    private static int tested, errors, missing, unresolved, sourceWarnings;
    public static void main(String[] args) {
        Game.leakTracer = LeakTracer.NoOp.INSTANCE;
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false); config.setWindowedMode(320, 288); config.disableAudio(true);
        new Lwjgl3Application(new Sample(), config);
        if (failure != null) { failure.printStackTrace(); System.exit(1); }
        System.out.println("ASSET COVERAGE AUDIT: " + tested + " actual Tile instances; " + errors
            + " construction errors; " + missing + " missing materials; " + unresolved + " unclassified visible/solid objects; "
            + sourceWarnings + " source warnings");
        System.exit(errors + missing + unresolved + sourceWarnings == 0 ? 0 : 2);
    }
    private static final class Sample extends Game {
        Sample() { super(new String[0], 2); }
        @Override public void create() {
            try {
                super.create(); actionStack.clear();
                JsonValue input = new JsonReader().parse(Gdx.files.local("catalog-input.json"));
                JsonValue rows = new JsonValue(JsonValue.ValueType.array);
                Game.rand.setSeed(712903L);
                BwAssets assets = BwAssets.get();
                for (JsonValue candidate = input.get("cases").child; candidate != null; candidate = candidate.next) {
                    JsonValue row = new JsonValue(JsonValue.ValueType.object);
                    String ground = candidate.getString("ground"), upper = candidate.getString("upper");
                    field(row, "inputGround", ground); field(row, "inputUpper", upper);
                    field(row, "evidence", candidate.getString("evidence"));
                    ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
                    PrintStream originalError = System.err;
                    try {
                        System.setErr(new PrintStream(diagnostics, true, StandardCharsets.UTF_8));
                        Tile tile = new Tile(ground, upper, new Vector2(), true);
                        tested++;
                        field(row, "ground", tile.name); field(row, "upper", tile.nameUpper);
                        field(row, "solid", tile.isSolid); field(row, "water", tile.isWater);
                        field(row, "lava", tile.isLava); field(row, "ledge", tile.isLedge);
                        field(row, "cuttable", tile.isCuttable); field(row, "smashable", tile.isSmashable);
                        field(row, "grass", tile.isGrass); field(row, "waterfall", tile.isWaterfall);
                        field(row, "biome", BiomeProfiles.visualForTile(tile).id);
                        String terrain = BwAssets.terrainName(tile), object = BwAssets.objectName(tile);
                        field(row, "terrain", terrain); field(row, "object", object);
                        String terrainAgain = BwAssets.terrainName(tile), objectAgain = BwAssets.objectName(tile);
                        if (!terrain.equals(terrainAgain) || !Objects.equals(object, objectAgain))
                            throw new IllegalStateException("Non-deterministic material identity");
                        TextureRegion surface = assets.terrain(tile), prop = assets.object(tile);
                        boolean absent = assets.named(terrain) == null || surface == null || (object != null && prop == null);
                        if (absent) { missing++; field(row, "status", "missing-material"); }
                        else if (object == null && tile.isSolid && !structuralOrScripted(tile)) {
                            unresolved++; field(row, "status", "unclassified-solid-object");
                        } else field(row, "status", object == null ? "surface-or-special" : "resolved");
                        if (prop != null) {
                            field(row, "cropWidth", prop.getRegionWidth()); field(row, "cropHeight", prop.getRegionHeight());
                            field(row, "worldWidthAt16Fallback", assets.worldWidth(prop, 16));
                            field(row, "worldHeightAt16Fallback", assets.worldHeight(prop, 16));
                            field(row, "anchorX", assets.layout(prop).anchorX(.5f));
                            field(row, "anchorY", assets.layout(prop).anchorY(0f));
                        }
                    } catch (Throwable error) {
                        errors++; field(row, "status", "construction-error"); field(row, "error", error.toString());
                        field(row, "errorLocation", error.getStackTrace().length == 0 ? "" : error.getStackTrace()[0].toString());
                    } finally {
                        System.setErr(originalError);
                        String warning = diagnostics.toString(StandardCharsets.UTF_8);
                        if (!warning.isEmpty()) {
                            originalError.print(warning); sourceWarnings++;
                            field(row, "sourceWarning", warning);
                        }
                    }
                    rows.addChild(row);
                }
                JsonValue report = new JsonValue(JsonValue.ValueType.object);
                field(report, "tested", tested); field(report, "constructionErrors", errors);
                field(report, "missingMaterials", missing); field(report, "unclassifiedSolidObjects", unresolved);
                field(report, "sourceWarnings", sourceWarnings);
                field(report, "limitation", "Exact Tile.init identities and evidenced constructor literals; dynamic name grammars remain listed separately. No rendering or pixel-correctness claim.");
                report.addChild("rows", rows);
                Gdx.files.local("asset-coverage-runtime.json").writeString(report.toJson(JsonWriter.OutputType.json) + "\n", false, "UTF-8");
            } catch (Throwable error) { failure = error; }
            finally { Gdx.app.exit(); }
        }
        @Override public void render() {}
    }
    private static boolean structuralOrScripted(Tile tile) {
        // Solid water is a navigable-surface rule; ledge geometry comes from the
        // elevation renderer. These are not props that need a billboard region.
        if (tile.isWater || tile.isLava || tile.isLedge || tile.name.startsWith("ledge")) return true;
        String name = (tile.name + " " + tile.nameUpper).toLowerCase(java.util.Locale.ROOT);
        return tile.name.equals("black1") || tile.name.equals("solid") || tile.nameUpper.equals("solid")
            || name.contains("_hidden") || name.contains("nosprite") || name.contains("revived");
    }
    private static void field(JsonValue row, String key, String value) { row.addChild(key, new JsonValue(value)); }
    private static void field(JsonValue row, String key, boolean value) { row.addChild(key, new JsonValue(value)); }
    private static void field(JsonValue row, String key, double value) { row.addChild(key, new JsonValue(value)); }
}
