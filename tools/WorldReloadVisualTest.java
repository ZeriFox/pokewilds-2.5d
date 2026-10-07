package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;

/** Read-only captures of the saved volcanic-mansion/tidal-coast regression map. */
public final class WorldReloadVisualTest {
    private static Throwable failure;
    private static boolean completed;
    public static void main(String[] args) {
        Game.leakTracer = LeakTracer.NoOp.INSTANCE;
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false); config.setWindowedMode(640, 576); config.setForegroundFPS(60);
        new Lwjgl3Application(new Inspect(), config);
        if (failure != null) { failure.printStackTrace(); System.exit(1); }
        if (!completed) throw new IllegalStateException("Incomplete saved scene test");
        System.out.println("RELOAD VISUAL PASS: same saved island, contextual mansion path, visible tidal bed, continuous modern frames");
        System.exit(0);
    }
    private static final class Inspect extends Game {
        int frames; long previous;
        Inspect() { super(new String[0], 2); }
        @Override public void create() {
            try {
                super.create(); server = new Server(); Network.register(server); actionStack.clear();
                map = new PkmnMap("smoke-world"); start(); map.loadFromFile(this);
                player.position.set(player.spawnLoc); cam.position.set(player.position.x + 16, player.position.y, 0);
                player.acceptInput = false;
                actionStack.removeIf(action -> action instanceof CycleDayNight || action instanceof DrawSetupMenu);
                int paths = 0, tidal = 0;
                for (Tile tile : map.tiles.values()) {
                    if (tile.name.equals("path1") && Math.abs(tile.position.x + 48) <= 112 && Math.abs(tile.position.y + 160) <= 144) {
                        if (!BiomeProfiles.visualForTile(tile).id.equals("volcano") || !BwAssets.terrainName(tile).equals("volcanic_cracked"))
                            throw new IllegalStateException("Volcanic mansion approach remains snow: " + tile.position + "/" + BwAssets.terrainName(tile));
                        if (!tile.routeBelongsTo.name.equals("snow1")) throw new IllegalStateException("Saved encounter route changed");
                        paths++;
                    }
                    if (tile.isTidal && !tile.isWater && tile.name.startsWith("sand")) {
                        String material = BwAssets.terrainName(tile);
                        if (material.startsWith("water")) throw new IllegalStateException("Walkable tidal flat lost its visible bed");
                        tidal++;
                    }
                }
                if (paths < 1 || tidal < 1) throw new IllegalStateException("Wrong regression save: paths=" + paths + ", tidal=" + tidal);
                System.out.println("RELOAD materials: volcanic approach cells=" + paths + ", visible tidal beds=" + tidal);
            } catch (Throwable error) { failure = error; Gdx.app.exit(); }
        }
        @Override public void render() {
            try {
                if (failure != null) return;
                super.render(); frames++;
                Field field = Game.class.getDeclaredField("johtoRenderer"); field.setAccessible(true);
                long current = ((JohtoRenderer)field.get(this)).getRenderedFrames();
                if (current <= previous) throw new IllegalStateException("Modern renderer interrupted at " + frames);
                previous = current;
                if (frames == 45) {
                    screenshot("world-generated.png");
                    Vector2 target = new Vector2(-48, -160); Tile tile = map.tiles.get(target);
                    if (tile == null || !tile.name.startsWith("volcano")) throw new IllegalStateException("Missing saved caldera regression location");
                    player.position.set(target); cam.position.set(target.x + 8, target.y + 8, 0); map.refreshCache = true;
                } else if (frames == 75) {
                    screenshot("world-caldera.png"); completed = true; Gdx.app.exit();
                }
            } catch (Throwable error) { failure = error; Gdx.app.exit(); }
        }
        private void screenshot(String name) throws Exception {
            byte[] bytes = ScreenUtils.getFrameBufferPixels(0, 0, 640, 576, false);
            if (Gdx.gl.glGetError() != GL20.GL_NO_ERROR) throw new IllegalStateException("OpenGL error");
            BufferedImage image = new BufferedImage(640, 576, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 576; y++) for (int x = 0; x < 640; x++) {
                int p = (y * 640 + x) * 4;
                image.setRGB(x, 575 - y, (bytes[p] & 255) << 16 | (bytes[p + 1] & 255) << 8 | (bytes[p + 2] & 255));
            }
            if (!ImageIO.write(image, "png", new File(name))) throw new IllegalStateException("PNG writer absent");
            System.out.println("RELOAD screenshot=" + name + ", modern frames=" + previous);
        }
    }
}
