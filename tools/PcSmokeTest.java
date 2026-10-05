import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.ScreenUtils;
import com.pkmngen.game.Action;
import com.pkmngen.game.Game;
import com.pkmngen.leaks.LeakTracer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/**
 * Native desktop startup test. This needs a working desktop OpenGL driver, even
 * though its GLFW window stays invisible. It does not test world generation,
 * movement, battles or saving. Run from a disposable working directory: Game
 * may create settings.txt there. Keep the compiled test separate from the game.
 *
 * Exit codes: 0 = passed, 1 = failed, 124 = timed out.
 * Optional properties: -Dsmoke.frames=120 -Dsmoke.timeoutSeconds=45.
 */
public final class PcSmokeTest {
    private static final int FRAMES = Integer.getInteger("smoke.frames", 120);
    private static final int TIMEOUT_SECONDS = Integer.getInteger("smoke.timeoutSeconds", 45);
    private static final Pattern STACK_TRACE = Pattern.compile(
        "(?m)^\\s*(?:[\\w$]+\\.)*[\\w$]*(?:Exception|Error)(?:[:\\s]|$)"
        + "|^\\s*at\\s+[\\w.$]+\\([^\\r\\n]*\\)"
        + "|^\\s*Caused by:");

    private PcSmokeTest() { }

    public static void main(String[] args) throws Exception {
        if (FRAMES < 2 || TIMEOUT_SECONDS < 1) {
            throw new IllegalArgumentException("Require smoke.frames >= 2 and smoke.timeoutSeconds >= 1");
        }
        final PrintStream originalError = System.err;
        final CapturedError capturedError = new CapturedError(originalError);
        final AtomicReference<Throwable> backgroundFailure = new AtomicReference<Throwable>();
        System.setErr(new PrintStream(capturedError, true, "UTF-8"));
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            backgroundFailure.compareAndSet(null, error);
            originalError.println("SMOKE: uncaught exception in " + thread.getName());
            error.printStackTrace(originalError);
        });
        Thread timeout = new Thread(() -> {
            try {
                Thread.sleep(TIMEOUT_SECONDS * 1000L);
            } catch (InterruptedException done) {
                return;
            }
            originalError.println("SMOKE FAIL: timeout after " + TIMEOUT_SECONDS + " seconds");
            originalError.flush();
            System.exit(124);
        }, "pokewilds-smoke-timeout");
        timeout.setDaemon(true);
        timeout.start();

        int exitCode = 1;
        try {
            System.out.println("SMOKE: invisible native window; " + FRAMES + " menu frames; timeout "
                + TIMEOUT_SECONDS + " seconds");
            Game.leakTracer = LeakTracer.NoOp.INSTANCE;
            SmokeGame game = new SmokeGame();
            Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
            config.setTitle("PokeWilds PC smoke test");
            config.setWindowedMode(320, 288);
            config.setInitialVisible(false);
            config.setForegroundFPS(60);
            config.setIdleFPS(60);
            config.useVsync(false);
            // Leave audio and controllers enabled, like the normal desktop game.
            new Lwjgl3Application(game, config);

            if (backgroundFailure.get() != null) {
                throw new IllegalStateException("Background thread failed", backgroundFailure.get());
            }
            if (capturedError.truncated || STACK_TRACE.matcher(capturedError.text()).find()) {
                throw new IllegalStateException("Game/library wrote an exception stack trace to stderr");
            }
            if (!game.initialized || !game.menuSeen || game.frames < FRAMES || !game.disposed) {
                throw new IllegalStateException("Incomplete lifecycle: create=" + game.initialized
                    + ", menu=" + game.menuSeen + ", frames=" + game.frames + ", dispose=" + game.disposed);
            }
            System.out.println("SMOKE PASS: menu rendered " + game.frames
                + " frames; framebuffer CRC32=" + Long.toHexString(game.framebufferCrc)
                + "; lifecycle disposed cleanly");
            exitCode = 0;
        } catch (Throwable error) {
            originalError.println("SMOKE FAIL: " + error);
            error.printStackTrace(originalError);
        } finally {
            timeout.interrupt();
            System.setErr(originalError);
        }
        System.exit(exitCode);
    }

    private static final class SmokeGame extends Game {
        boolean initialized;
        boolean menuSeen;
        boolean disposed;
        int frames;
        long framebufferCrc;

        SmokeGame() {
            super(new String[0], 2);
        }

        @Override
        public void create() {
            super.create();
            initialized = true;
            verifyMenu();
        }

        @Override
        public void render() {
            super.render();
            verifyMenu();
            frames++;
            if (frames == FRAMES) {
                verifyFramebuffer();
                Gdx.app.exit();
            }
        }

        private void verifyMenu() {
            boolean present = false;
            for (Action action : actionStack) {
                if (action != null && action.getClass().getName().equals("com.pkmngen.game.DrawSetupMenu")) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                throw new IllegalStateException("Setup menu absent from game action stack");
            }
            menuSeen = true;
        }

        private void verifyFramebuffer() {
            int width = Gdx.graphics.getBackBufferWidth();
            int height = Gdx.graphics.getBackBufferHeight();
            byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, false);
            int glError = Gdx.gl.glGetError();
            if (glError != GL20.GL_NO_ERROR) {
                throw new IllegalStateException("OpenGL error after render/readback: " + glError);
            }
            Set<Integer> colors = new HashSet<Integer>();
            BufferedImage screenshot = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int i = 0; i + 3 < pixels.length; i += 4) {
                int rgb = ((pixels[i] & 255) << 16) | ((pixels[i + 1] & 255) << 8)
                    | (pixels[i + 2] & 255);
                colors.add(rgb);
                int pixelIndex = i / 4;
                // OpenGL readback starts at the bottom-left corner.
                screenshot.setRGB(pixelIndex % width, height - 1 - pixelIndex / width, rgb);
            }
            CRC32 crc = new CRC32();
            crc.update(pixels);
            framebufferCrc = crc.getValue();
            File screenshotFile = new File("smoke-menu.png").getAbsoluteFile();
            try {
                if (!ImageIO.write(screenshot, "png", screenshotFile)) {
                    throw new IOException("PNG writer unavailable");
                }
            } catch (IOException error) {
                throw new IllegalStateException("Could not save framebuffer screenshot", error);
            }
            System.out.println("SMOKE framebuffer: " + width + "x" + height + ", RGB colors="
                + colors.size() + ", CRC32=" + Long.toHexString(framebufferCrc)
                + ", screenshot=" + screenshotFile);
            // The original v0.8.11 menu, inspected under JDK 17, uses exactly
            // four RGB colors (baseline CRC32 1d17ad4d at 320x288).
            if (colors.size() < 4) {
                throw new IllegalStateException("Framebuffer appears blank/incomplete: " + colors.size() + " colors");
            }
        }

        @Override
        public void dispose() {
            super.dispose();
            disposed = true;
        }
    }

    /** Capture swallowed render exceptions while preserving the live error log. */
    private static final class CapturedError extends OutputStream {
        private static final int LIMIT = 1024 * 1024;
        private final PrintStream original;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        boolean truncated;

        CapturedError(PrintStream original) {
            this.original = original;
        }

        @Override
        public synchronized void write(int value) {
            original.write(value);
            if (buffer.size() < LIMIT) {
                buffer.write(value);
            } else {
                truncated = true;
            }
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            original.write(bytes, offset, length);
            int keep = Math.min(length, LIMIT - buffer.size());
            buffer.write(bytes, offset, keep);
            truncated |= keep < length;
        }

        @Override
        public void flush() throws IOException {
            original.flush();
        }

        synchronized String text() throws IOException {
            return buffer.toString("UTF-8");
        }
    }
}
