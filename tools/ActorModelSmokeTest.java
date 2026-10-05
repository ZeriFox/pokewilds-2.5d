package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Deterministic visual/integration fixture, distinct from procedural-world testing. */
public final class ActorModelSmokeTest {
    private static final String[] POSES = {"down", "right", "up", "left"};
    private static final String[] ROUTE = {"right", "up", "left", "down"};
    private static final Pattern STACK_TRACE = Pattern.compile(
        "(?m)^\\s*(?:[\\w$]+\\.)*[\\w$]*(?:Exception|Error)(?:[:\\s]|$)"
        + "|^\\s*at\\s+[\\w.$]+\\([^\\r\\n]*\\)|^\\s*Caused by:");

    public static void main(String[] args) throws Exception {
        PrintStream originalError = System.err;
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        System.setErr(new PrintStream(new OutputStream() {
            public synchronized void write(int value) {
                originalError.write(value);
                if (errors.size() < 1024 * 1024) errors.write(value);
            }
        }, true, "UTF-8"));
        AtomicReference<Throwable> backgroundFailure = new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            backgroundFailure.compareAndSet(null, failure);
            failure.printStackTrace(originalError);
            if (Gdx.app != null) Gdx.app.exit();
        });
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(110000L); }
            catch (InterruptedException finished) { return; }
            originalError.println("ACTOR FAIL: timeout after 110s");
            System.exit(124);
        }, "actor-model-smoke-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        int exit = 1;
        try {
            Game.leakTracer = LeakTracer.NoOp.INSTANCE;
            ModelGame game = new ModelGame();
            Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
            config.setTitle("PokeWilds actor 3D model test");
            config.setWindowedMode(640, 576);
            config.setInitialVisible(false);
            config.setForegroundFPS(60);
            config.setIdleFPS(60);
            config.useVsync(false);
            new Lwjgl3Application(game, config);
            if (backgroundFailure.get() != null) throw new IllegalStateException("Background failure", backgroundFailure.get());
            if (STACK_TRACE.matcher(errors.toString("UTF-8")).find()) throw new IllegalStateException("Exception written to stderr");
            if (!game.complete || !game.disposed) throw new IllegalStateException("Incomplete test lifecycle");
            System.out.println("ACTOR PASS: native perspective models, four idle and walking directions, original follower movement, save/load");
            exit = 0;
        } catch (Throwable failure) {
            originalError.println("ACTOR FAIL: " + failure);
            failure.printStackTrace(originalError);
        } finally {
            watchdog.interrupt();
            System.setErr(originalError);
        }
        System.exit(exit);
    }

    private static final class ModelGame extends Game {
        Pokemon machop;
        Input nativeInput, fixtureInput;
        boolean complete, disposed, walkingScreenshot;
        int frames, phase, phaseFrames, pose, route, step, pressedKey = -1;
        long previousWorldFrames, previousPlayerFrames, previousPokemonFrames, animationBeforeWalk;
        long renderNanos, renderMaxNanos;
        Vector2 movementStart, routeOrigin, savedPosition;
        String savedDirection;
        int savedTiles, savedLevel, savedHp;
        Map<String, Integer> savedItems;

        ModelGame() { super(new String[0], 4); }

        @Override public void create() {
            super.create();
            nativeInput = Gdx.input;
            fixtureInput = (Input)Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                (proxy, method, args) -> {
                    // Keep this isolated fixture independent of keyboard activity.
                    if (method.getName().equals("isKeyPressed")) return (Integer)args[0] == pressedKey;
                    if (method.getName().equals("isKeyJustPressed") || method.getName().equals("isTouched")) return false;
                    try { return method.invoke(nativeInput, args); }
                    catch (InvocationTargetException error) { throw error.getCause(); }
                });
            actionStack.removeIf(action -> action instanceof DrawSetupMenu);
            server = new Server();
            Network.register(server);
            map = new PkmnMap("actor-fixture");
            for (int y = -320; y <= 320; y += 16) {
                for (int x = -320; x <= 320; x += 16) {
                    Vector2 position = new Vector2(x, y);
                    String ground = Math.abs(y) <= 32 ? "sand1" : "green1";
                    map.tiles.put(position, new Tile(ground, position.cpy(), true));
                }
            }
            // Fixed scenery provides scale and visible depth without obstructing
            // the original movement actions used by the route below.
            putTile("green1", "tree1", -64, 80);
            putTile("green1", "tree1", 64, 80);
            putTile("green1", "rock1", 80, -48);
            map.bottomLeft = new Vector2(-320, -320);
            map.topRight = new Vector2(320, 320);
            map.minimap = new Pixmap(80, 80, Pixmap.Format.RGBA8888);
            map.minimap.setColor(0, 0, 0, 1);
            map.minimap.fill();
            player.position.set(-32, 0);
            player.spawnLoc.set(player.position);
            player.name = "ModelTest";
            cam.position.set(player.position.x + 16, player.position.y, 0);
            start();
            machop = player.pokemon.get(0);
            if (!"machop".equals(machop.specie.name)) throw new IllegalStateException("Starter is not Machop");
            player.hmPokemon = machop;
            insertAction(machop.new Follow(player));
            System.out.println("ACTOR: deterministic 41x41 fixture, 1681 tiles; actual starter Machop; no user save files");
        }

        private void putTile(String lower, String upper, int x, int y) {
            Vector2 position = new Vector2(x, y);
            map.tiles.put(position, new Tile(lower, upper, position.cpy(), true));
        }

        @Override public void render() {
            Gdx.input = fixtureInput;
            // Pose selection controls only the disposable fixture; production
            // actors and all animation/movement code remain unchanged.
            if (phase == 1) {
                player.position.set(-32, 0);
                machop.position.set(32, 0);
                player.dirFacing = POSES[pose];
                machop.dirFacing = POSES[pose];
                cam.position.set(0, 8, 0);
            }
            long started = System.nanoTime();
            super.render();
            long elapsed = System.nanoTime() - started;
            renderNanos += elapsed;
            renderMaxNanos = Math.max(renderMaxNanos, elapsed);
            frames++;
            phaseFrames++;
            long worldFrames = counter("getRenderedFrames");
            long playerFrames = counter("getPlayerModelFrames");
            long pokemonFrames = counter("getPokemonModelFrames");
            if (frames > 5 && (worldFrames <= previousWorldFrames || playerFrames <= previousPlayerFrames
                    || pokemonFrames <= previousPokemonFrames)) {
                throw new IllegalStateException("Expected both models on an active Johto frame: world=" + worldFrames
                    + ", player=" + playerFrames + ", Pokemon=" + pokemonFrames);
            }
            previousWorldFrames = worldFrames;
            previousPlayerFrames = playerFrames;
            previousPokemonFrames = pokemonFrames;
            if (phase == 0 && phaseFrames >= 90) {
                requireMachopModel();
                phase = 1; phaseFrames = 0;
            } else if (phase == 1 && phaseFrames >= 24) {
                requireMachopModel();
                screenshot("actor-idle-" + POSES[pose] + ".png");
                System.out.println("ACTOR: idle direction " + POSES[pose] + " verified; player=" + player.position + ", Machop=" + machop.position);
                pose++;
                phaseFrames = 0;
                if (pose == POSES.length) {
                    player.dirFacing = "right";
                    machop.dirFacing = "right";
                    machop.position.set(player.position.cpy().add(-16, 0));
                    routeOrigin = player.position.cpy();
                    animationBeforeWalk = counter("getAnimatedModelFrames");
                    beginStep();
                    phase = 2;
                }
            } else if (phase == 2) {
                float distance2 = player.position.dst2(movementStart);
                if (distance2 > 0) pressedKey = -1;
                if (step == 1 && !walkingScreenshot && distance2 >= 16 && distance2 < 256) {
                    if (!player.dirFacing.equals(ROUTE[route]) || !machop.dirFacing.equals(ROUTE[route]))
                        throw new IllegalStateException("Actor directions do not match current walking route");
                    screenshot("actor-walk-" + ROUTE[route] + ".png");
                    walkingScreenshot = true;
                }
                boolean moving = actionStack.stream().anyMatch(action -> action instanceof PlayerMoving || action instanceof Pokemon.Moving);
                if (pressedKey == -1 && !moving && phaseFrames > 5) {
                    if (distance2 != 256) throw new IllegalStateException("Original player movement must travel one 16-unit tile");
                    if (machop.position.dst2(player.position) > 1024) throw new IllegalStateException("Follower drifted more than two tiles");
                    System.out.println("ACTOR: original movement " + ROUTE[route] + ", step=" + (step + 1)
                        + ", player=" + player.position + ", follower=" + machop.position);
                    if (++step == 2) {
                        if (!walkingScreenshot) throw new IllegalStateException("Walking screenshot missing for " + ROUTE[route]);
                        step = 0;
                        route++;
                    }
                    if (route == ROUTE.length) {
                        if (!player.position.equals(routeOrigin)) throw new IllegalStateException("Four-direction route failed to return to origin");
                        if (counter("getAnimatedModelFrames") <= animationBeforeWalk) throw new IllegalStateException("No animated 3D models during movement");
                        saveAndReload();
                        phase = 3; phaseFrames = 0;
                    } else {
                        beginStep();
                    }
                } else if (phaseFrames > 150) throw new IllegalStateException("Original player/follower movement timed out");
            } else if (phase == 3 && phaseFrames >= 60) {
                verifySavedState();
                requireMachopModel();
                screenshot("actor-loaded.png");
                System.out.println("ACTOR: counters player=" + playerFrames + ", Pokemon=" + pokemonFrames
                    + ", animated=" + counter("getAnimatedModelFrames") + ", species=" + counter("getLoadedSpeciesCount")
                    + ", native FPS=" + Gdx.graphics.getFramesPerSecond() + ", CPU mean ms=" + renderNanos / 1e6 / frames
                    + ", CPU max ms=" + renderMaxNanos / 1e6);
                complete = true;
                Gdx.app.exit();
            }
        }

        private void beginStep() {
            movementStart = player.position.cpy();
            switch (ROUTE[route]) {
                case "up": pressedKey = InputProcessor.keyboardUp; break;
                case "down": pressedKey = InputProcessor.keyboardDown; break;
                case "left": pressedKey = InputProcessor.keyboardLeft; break;
                default: pressedKey = InputProcessor.keyboardRight;
            }
            phaseFrames = 0;
            if (step == 0) walkingScreenshot = false;
        }

        private void saveAndReload() {
            savedTiles = map.tiles.size();
            savedPosition = player.position.cpy();
            savedDirection = player.dirFacing;
            savedLevel = machop.level;
            savedHp = machop.currentStats.get("hp");
            savedItems = new HashMap<>(player.getItemsDict());
            Gdx.files.local("actor-fixture.sav").mkdirs();
            Gdx.files.local("actor-fixture.sav/.smoke-harness").writeString("Isolated actor model fixture.\n", false);
            saveGame();
            if (!new File("actor-fixture.sav/game.json.zip").isFile()) throw new IllegalStateException("Save file missing");
            Pixmap oldMinimap = map.minimap;
            actionStack.clear();
            insertAction(new InputProcessor());
            map = new PkmnMap("actor-fixture");
            player = new Player();
            start();
            map.loadFromFile(this);
            oldMinimap.dispose();
            machop = player.pokemon.get(0);
            verifySavedState();
            if (player.hmPokemon != machop) throw new IllegalStateException("Starter follower not restored by original loader");
            System.out.println("ACTOR: original save/load preserved tiles=" + savedTiles + ", position=" + savedPosition
                + ", direction=" + savedDirection + ", starter=" + machop.specie.name + ", level=" + savedLevel + ", HP=" + savedHp + ", inventory=" + savedItems);
        }

        private void verifySavedState() {
            if (map.tiles.size() != savedTiles || !player.position.equals(savedPosition) || !player.dirFacing.equals(savedDirection)
                    || !"ModelTest".equals(player.name) || player.pokemon.size() != 1 || !"machop".equals(machop.specie.name)
                    || machop.level != savedLevel || machop.currentStats.get("hp") != savedHp
                    || !player.getItemsDict().equals(savedItems)) throw new IllegalStateException("State changed across original save/load");
        }

        private Object renderer() {
            try {
                Field field = Game.class.getDeclaredField("johtoRenderer");
                field.setAccessible(true);
                Object renderer = field.get(this);
                if (renderer == null) throw new IllegalStateException("Johto renderer missing");
                return renderer;
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }

        private long counter(String name) {
            try {
                Object renderer = renderer();
                return ((Number)renderer.getClass().getMethod(name).invoke(renderer)).longValue();
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Missing model counter " + name, error); }
        }

        private void requireMachopModel() {
            try {
                Object renderer = renderer();
                if (!(Boolean)renderer.getClass().getMethod("hasLoadedSpecies", String.class).invoke(renderer, "machop")
                        || counter("getLoadedSpeciesCount") < 1 || counter("getPlayerModelFrames") < 60 || counter("getPokemonModelFrames") < 60)
                    throw new IllegalStateException("Machop 3D model or native player model is missing");
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot verify loaded Machop model", error); }
        }

        private void screenshot(String name) {
            int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
            byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, false);
            if (Gdx.gl.glGetError() != GL20.GL_NO_ERROR) throw new IllegalStateException("OpenGL error capturing " + name);
            if (name.startsWith("actor-idle-")) verifyVisibleMachop(pixels, width, height);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Set<Integer> colors = new HashSet<>();
            for (int i = 0; i < pixels.length; i += 4) {
                int rgb = (pixels[i] & 255) << 16 | (pixels[i + 1] & 255) << 8 | pixels[i + 2] & 255;
                colors.add(rgb);
                image.setRGB(i / 4 % width, height - 1 - i / 4 / width, rgb);
            }
            if (colors.size() < 16) throw new IllegalStateException("Blank/incomplete model framebuffer");
            try {
                if (!ImageIO.write(image, "png", new File(name))) throw new IllegalStateException("PNG writer missing");
            } catch (java.io.IOException error) { throw new IllegalStateException(error); }
            System.out.println("ACTOR: native framebuffer " + name + ", colors=" + colors.size() + ", frame=" + frames);
        }

        private void verifyVisibleMachop(byte[] actual, int width, int height) {
            // A submitted model can still be invisible (bad skinning bounds).
            // Compare against the same read-only presentation with only this
            // fixture's Machop hidden. Ignore the ground/shadow band entirely.
            Object renderer = renderer();
            Sprite original = machop.currOwSprite;
            try {
                Field cameraField = renderer.getClass().getDeclaredField("camera");
                cameraField.setAccessible(true);
                PerspectiveCamera camera = (PerspectiveCamera)cameraField.get(renderer);
                Vector3 foot = camera.project(new Vector3(machop.position.x + 8, .15f, -machop.position.y - 7));
                machop.currOwSprite = null;
                if (!(Boolean)renderer.getClass().getMethod("render", Game.class).invoke(renderer, this))
                    throw new IllegalStateException("Cannot produce model-free visibility reference");
                byte[] reference = ScreenUtils.getFrameBufferPixels(0, 0, width, height, false);
                int changed = 0;
                int x0 = Math.max(0, (int)foot.x - 65), x1 = Math.min(width, (int)foot.x + 65);
                int y0 = Math.max(0, (int)foot.y + 18), y1 = Math.min(height, (int)foot.y + 120);
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    int i = (y * width + x) * 4;
                    if (actual[i] != reference[i] || actual[i + 1] != reference[i + 1] || actual[i + 2] != reference[i + 2]) changed++;
                }
                if (changed < 30) throw new IllegalStateException("Machop model invisible above its ground shadow: " + changed + " changed pixels");
                System.out.println("ACTOR: real Machop geometry verified above shadow, changed pixels=" + changed);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot verify model pixels", error); }
            finally {
                machop.currOwSprite = original;
                try { renderer.getClass().getMethod("render", Game.class).invoke(renderer, this); }
                catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot restore model presentation", error); }
            }
        }

        @Override public void dispose() {
            if (nativeInput != null) Gdx.input = nativeInput;
            super.dispose();
            disposed = true;
        }
    }
}
