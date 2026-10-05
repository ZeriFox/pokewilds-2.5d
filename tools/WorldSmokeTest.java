package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Native integration check. Run only in an isolated build/world-smoke folder. */
public final class WorldSmokeTest {
    private static final boolean JOHTO = "johto".equalsIgnoreCase(System.getProperty("pokewilds.visual", "classic"));
    private static final Pattern STACK_TRACE = Pattern.compile(
        "(?m)^\\s*(?:[\\w$]+\\.)*[\\w$]*(?:Exception|Error)(?:[:\\s]|$)"
        + "|^\\s*at\\s+[\\w.$]+\\([^\\r\\n]*\\)|^\\s*Caused by:");

    public static void main(String[] args) throws Exception {
        PrintStream originalError = System.err;
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        System.setErr(new PrintStream(new OutputStream() {
            public synchronized void write(int value) {
                originalError.write(value);
                if (errorBytes.size() < 1024 * 1024) errorBytes.write(value);
            }
        }, true, "UTF-8"));
        AtomicReference<Throwable> backgroundFailure = new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            backgroundFailure.compareAndSet(null, failure);
            failure.printStackTrace(originalError);
            Gdx.app.exit();
        });
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(290000L); }
            catch (InterruptedException finished) { return; }
            originalError.println("WORLD FAIL: internal timeout (290s)");
            System.exit(124);
        }, "world-smoke-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        int exitCode = 1;
        try {
            Game.leakTracer = LeakTracer.NoOp.INSTANCE;
            SmokeGame game = new SmokeGame();
            Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
            configuration.setTitle("PokeWilds procedural world test");
            configuration.setWindowedMode(640, 576);
            configuration.setInitialVisible(false);
            configuration.setForegroundFPS(60);
            configuration.setIdleFPS(60);
            configuration.useVsync(false);
            new Lwjgl3Application(game, configuration);
            if (backgroundFailure.get() != null) throw new IllegalStateException("Worker failed", backgroundFailure.get());
            if (STACK_TRACE.matcher(errorBytes.toString("UTF-8")).find())
                throw new IllegalStateException("Exception written to stderr (including swallowed game exceptions)");
            if (!game.completed || !game.disposed) throw new IllegalStateException("Incomplete test lifecycle");
            System.out.println("WORLD PASS: original S generation, native rendering, one movement, save/load, clean close");
            exitCode = 0;
        } catch (Throwable failure) {
            originalError.println("WORLD FAIL: " + failure);
            failure.printStackTrace(originalError);
        } finally {
            watchdog.interrupt();
            System.setErr(originalError);
        }
        System.exit(exitCode);
    }

    private static final class SmokeGame extends Game {
        boolean ready, completed, disposed;
        int frames, phase, pressedKey = -1, phaseFrames;
        Vector2 movementStart, expectedPosition;
        int expectedTiles, expectedParty;
        String expectedSpecies, expectedName;
        Input nativeInput, virtualInput;
        long startedAt = System.nanoTime();
        long renderNanos, maxRenderNanos;
        long lastJohtoFrames;
        int fallbackFrames, fallbackReports;
        boolean johtoFrameActive;
        Player.Emote controlledEmote;
        Vector2 emotePosition;
        long emoteStartRenderedFrames;

        SmokeGame() { super(new String[0], 2); }

        @Override public void create() {
            super.create();
            nativeInput = Gdx.input;
            // Drive the real InputProcessor with one virtual key. All other
            // native input methods retain their regular implementation.
            virtualInput = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isKeyPressed") && (Integer) args[0] == pressedKey) return true;
                    try { return method.invoke(nativeInput, args); }
                    catch (InvocationTargetException error) { throw error.getCause(); }
                });
            actionStack.removeIf(action -> action instanceof DrawSetupMenu);
            server = new Server();
            Network.register(server); // Single-player path: no bind/listening socket.
            map = new PkmnMap("smoke-world");
            player.name = "Smoke";
            System.out.println("WORLD: generating original size S (30000), visual=" + System.getProperty("pokewilds.visual", "classic"));
            Thread generation = new Thread(() -> {
                GenIsland1 generator = new GenIsland1(this, new Vector2(), 30000);
                Gdx.app.postRunnable(() -> {
                    start();
                    generator.step(this);
                    int width = (int)(map.topRight.x - map.bottomLeft.x) / 8;
                    int height = (int)(map.topRight.y - map.bottomLeft.y) / 8;
                    map.minimap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
                    map.minimap.setColor(0, 0, 0, 1);
                    map.minimap.fill();
                    if (map.tiles.size() < 1000 || player.pokemon.isEmpty()) throw new IllegalStateException("Generation incomplete");
                    ready = true;
                    System.out.println("WORLD: generated " + map.tiles.size() + " tiles, party=" + player.pokemon.size()
                        + ", spawn=" + player.position + ", seconds=" + (System.nanoTime() - startedAt) / 1e9);
                });
            }, "world-smoke-generation");
            generation.setDaemon(true);
            generation.start();
        }

        @Override public void render() {
            // LWJGL resets Gdx.input to the current window input every frame.
            Gdx.input = virtualInput;
            long frameStarted = System.nanoTime();
            super.render();
            if (!ready) return;
            long frameNanos = System.nanoTime() - frameStarted;
            renderNanos += frameNanos;
            maxRenderNanos = Math.max(maxRenderNanos, frameNanos);
            frames++;
            phaseFrames++;
            if (JOHTO) {
                long currentFrames = johtoFrames();
                johtoFrameActive = currentFrames > lastJohtoFrames;
                lastJohtoFrames = currentFrames;
                if (!johtoFrameActive) {
                    fallbackFrames++;
                    if (fallbackReports < 3) {
                        logFallbackState();
                        fallbackReports++;
                    }
                }
            }
            if (phase == 0 && phaseFrames >= (JOHTO ? 90 : 30)) {
                if (JOHTO && (lastJohtoFrames < 90 || !johtoFrameActive)) {
                    if (phaseFrames >= 300) throw new IllegalStateException("Johto renderer could not complete 90 frames and capture an active frame within 300 frames");
                    return;
                }
                screenshot("world-generated.png");
                if (JOHTO) {
                    controlledEmote = player.new Emote("!", null);
                    emotePosition = player.position.cpy();
                    emoteStartRenderedFrames = lastJohtoFrames;
                    insertAction(controlledEmote);
                    phase = 4; phaseFrames = 0;
                } else {
                    beginMovement();
                    phase = 1; phaseFrames = 0;
                }
            } else if (phase == 4) {
                if (!johtoFrameActive || !player.position.equals(emotePosition))
                    throw new IllegalStateException("Player emote caused a renderer fallback or unexpected movement");
                if (phaseFrames == 20) {
                    if (!actionStack.contains(controlledEmote) || controlledEmote.timer != 20)
                        throw new IllegalStateException("Player emote lifecycle did not advance once per frame");
                    screenshot("world-emote.png");
                }
                if (!actionStack.contains(controlledEmote)) {
                    if (controlledEmote.timer != 61 || phaseFrames != 61 || lastJohtoFrames - emoteStartRenderedFrames != 61)
                        throw new IllegalStateException("Player emote lifecycle or Johto rendering was interrupted");
                    System.out.println("WORLD: player emote verified, original lifetime=61 frames, uninterrupted Johto=61 frames");
                    beginMovement();
                    phase = 1; phaseFrames = 0;
                } else if (phaseFrames > 70) throw new IllegalStateException("Player emote failed to finish");
            } else if (phase == 1) {
                if (!player.position.equals(movementStart)) pressedKey = -1;
                boolean moving = actionStack.stream().anyMatch(action -> action instanceof PlayerMoving);
                if (pressedKey == -1 && !moving && phaseFrames > 5) {
                    if (player.position.dst2(movementStart) != 256.0f) throw new IllegalStateException("Expected exactly one 16-unit movement: " + player.position);
                    System.out.println("WORLD: movement verified " + movementStart + " -> " + player.position);
                    phase = 2; phaseFrames = 0;
                } else if (phaseFrames > 120) throw new IllegalStateException("Player failed to move from spawn: canMove="
                    + playerCanMove + ", input=" + InputProcessor.acceptInput + ", key=" + pressedKey);
            } else if (phase == 2 && phaseFrames >= 15) {
                saveAndReload();
                phase = 3; phaseFrames = 0;
            } else if (phase == 3 && phaseFrames >= 45) {
                if (JOHTO && !johtoFrameActive) {
                    if (phaseFrames >= 300) throw new IllegalStateException("No active Johto frame after loading within 300 frames");
                    return;
                }
                screenshot("world-loaded.png");
                completed = true;
                Gdx.app.exit();
            }
        }

        private void beginMovement() {
            movementStart = player.position.cpy();
            int[][] directions = {{0, 16, InputProcessor.keyboardUp}, {16, 0, InputProcessor.keyboardRight},
                                  {0, -16, InputProcessor.keyboardDown}, {-16, 0, InputProcessor.keyboardLeft}};
            for (int[] direction : directions) {
                Vector2 next = movementStart.cpy().add(direction[0], direction[1]);
                Tile tile = map.tiles.get(next);
                if (tile != null && !tile.isSolid && !tile.isWater && !tile.isLedge && !tile.isGrass
                        && !map.pokemon.containsKey(next) && !tile.nameUpper.contains("door")) {
                    pressedKey = direction[2];
                    return;
                }
            }
            throw new IllegalStateException("No safe adjacent walking tile at generated spawn " + movementStart);
        }

        private void saveAndReload() {
            expectedTiles = map.overworldTiles.size();
            expectedPosition = player.position.cpy();
            expectedParty = player.pokemon.size();
            expectedSpecies = player.pokemon.get(0).specie.name;
            expectedName = player.name;
            // The original save routine backs up this folder before writing its
            // first save; give the backup writer a test marker to archive. It
            // rejects an empty directory. The game ignores this extra file.
            Gdx.files.local("smoke-world.sav").mkdirs();
            Gdx.files.local("smoke-world.sav/.smoke-harness").writeString("Isolated integration test save.\n", false);
            saveGame();
            if (!new File("smoke-world.sav/game.json.zip").isFile()
                    || !new File("smoke-world.sav/map0,0.json.zip").isFile()) throw new IllegalStateException("Save output missing");
            Pixmap oldMinimap = map.minimap;
            actionStack.clear();
            insertAction(new InputProcessor());
            map = new PkmnMap("smoke-world");
            player = new Player();
            start();
            map.loadFromFile(this);
            oldMinimap.dispose();
            if (map.overworldTiles.size() != expectedTiles || !player.position.equals(expectedPosition)
                    || player.pokemon.size() != expectedParty || !player.pokemon.get(0).specie.name.equals(expectedSpecies)
                    || !player.name.equals(expectedName)) throw new IllegalStateException("Save/load state mismatch");
            System.out.println("WORLD: reloaded tile count=" + expectedTiles + ", position=" + expectedPosition
                + ", party=" + expectedParty + ", first species=" + expectedSpecies + ", player=" + expectedName);
        }

        private void screenshot(String name) {
            verifyJohtoFrames();
            int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
            byte[] rgba = ScreenUtils.getFrameBufferPixels(0, 0, width, height, false);
            if (Gdx.gl.glGetError() != GL20.GL_NO_ERROR) throw new IllegalStateException("OpenGL error during framebuffer capture");
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Set<Integer> colors = new HashSet<>();
            for (int i = 0; i < rgba.length; i += 4) {
                int rgb = (rgba[i] & 255) << 16 | (rgba[i + 1] & 255) << 8 | (rgba[i + 2] & 255);
                colors.add(rgb);
                image.setRGB((i / 4) % width, height - 1 - (i / 4) / width, rgb);
            }
            if (colors.size() < 8) throw new IllegalStateException("World framebuffer appears blank: " + colors.size() + " colors");
            try {
                if (!ImageIO.write(image, "png", new File(name))) throw new IllegalStateException("PNG writer missing");
            } catch (java.io.IOException error) { throw new IllegalStateException(error); }
            System.out.println("WORLD: screenshot=" + name + ", " + width + "x" + height + ", colors=" + colors.size());
            System.out.println("WORLD: desktop FPS=" + Gdx.graphics.getFramesPerSecond()
                + ", mean game.render CPU ms=" + (renderNanos / 1e6 / frames)
                + ", max game.render CPU ms=" + maxRenderNanos / 1e6
                + ", frames=" + frames + ", classic fallback frames=" + fallbackFrames
                + " (60 FPS cap; save/load excluded; not a 3DS benchmark)");
        }

        private void verifyJohtoFrames() {
            if (!JOHTO) return;
            long renderedFrames = johtoFrames();
            if (renderedFrames < 90 || !johtoFrameActive)
                throw new IllegalStateException("Johto renderer did not complete 90 frames or current frame is a fallback: " + renderedFrames);
            System.out.println("WORLD: Johto renderer active, renderedFrames=" + renderedFrames);
        }

        private long johtoFrames() {
            try {
                // Reflection deliberately keeps the harness compilable against
                // the classic baseline JAR; Johto mode must never pass merely
                // because the game silently fell back to its classic renderer.
                Field field = Game.class.getDeclaredField("johtoRenderer");
                field.setAccessible(true);
                Object renderer = field.get(this);
                if (renderer == null) throw new IllegalStateException("Requested Johto renderer was not created");
                return ((Number)renderer.getClass().getMethod("getRenderedFrames").invoke(renderer)).longValue();
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot verify the requested Johto renderer", error);
            }
        }

        private void logFallbackState() {
            Map<String, Integer> actions = new TreeMap<>();
            for (Action action : actionStack) {
                if (action != null) actions.merge(action.getClass().getName(), 1, Integer::sum);
            }
            System.out.println("WORLD fallback diagnostic: frame=" + frames + ", rendered=" + lastJohtoFrames
                + ", day=" + map.timeOfDay + ", fadeDay=" + CycleDayNight.fadeToDay + ", fadeNight=" + CycleDayNight.fadeToNight
                + ", battle=" + player.dontDrawMapDuringBattle + ", planting=" + player.currPlanting
                + ", fieldMove=" + player.currFieldMove + ", actions=" + actions);
        }

        @Override public void dispose() {
            if (nativeInput != null) Gdx.input = nativeInput;
            super.dispose();
            disposed = true;
        }
    }
}
