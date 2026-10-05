import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.ScreenUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import net.mgsx.gltf.scene3d.utils.MaterialConverter;

/** Load and GPU-render every exact asset with ActorModelRenderer's shader setup. */
public final class ModelCatalogSmokeTest extends ApplicationAdapter {
    private static final Set<String> CAPTURE = new HashSet<>(Arrays.asList("machop", "pikachu", "charizard", "gyarados", "pidgey", "chimecho"));
    private static final Pattern TRACE = Pattern.compile("(?m)^\\s*(?:[\\w$]+\\.)*[\\w$]*(?:Exception|Error)(?:[:\\s]|$)|^\\s*at\\s+[\\w.$]+\\(");
    private static final int BONES = 128;
    private final File[] files;
    private final List<String> failures = new ArrayList<>();
    private final List<String> noIdle = new ArrayList<>(), noWalk = new ArrayList<>(), noAnimation = new ArrayList<>();
    private final Environment environment = new Environment();
    private PerspectiveCamera camera;
    private ModelBatch batch;
    private PrintWriter report;
    private int index, passed, samples, maxBones, primitiveParts;
    private boolean complete, disposed;

    private ModelCatalogSmokeTest(File directory, int expectedCount) {
        files = directory.listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".glb"));
        if (files == null || files.length != expectedCount || expectedCount < 1)
            throw new IllegalArgumentException("Expected " + expectedCount + " manifest GLB models in " + directory);
        Arrays.sort(files, (left, right) -> left.getName().compareTo(right.getName()));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Pass the model directory and verified manifest count");
        PrintStream originalError = System.err;
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        System.setErr(new PrintStream(new OutputStream() {
            public synchronized void write(int value) {
                originalError.write(value);
                if (errors.size() < 1024 * 1024) errors.write(value);
            }
        }, true, "UTF-8"));
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(280000); }
            catch (InterruptedException finished) { return; }
            originalError.println("CATALOG FAIL: internal timeout 280s");
            System.exit(124);
        }, "catalog-smoke-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        int exit = 1;
        try {
            ModelCatalogSmokeTest test = new ModelCatalogSmokeTest(new File(args[0]), Integer.parseInt(args[1]));
            Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
            config.setWindowedMode(512, 512);
            config.setInitialVisible(false);
            config.setTitle("PokeWilds model GPU catalog test");
            config.setForegroundFPS(0);
            config.setIdleFPS(0);
            config.useVsync(false);
            config.disableAudio(true);
            new Lwjgl3Application(test, config);
            if (!test.complete || !test.disposed || test.passed != test.files.length || !test.failures.isEmpty())
                throw new IllegalStateException("Catalog incomplete or failed models: " + test.failures);
            if (TRACE.matcher(errors.toString("UTF-8")).find()) throw new IllegalStateException("An exception was written to stderr");
            System.out.println("CATALOG PASS: " + test.passed + "/" + test.files.length + " exact GLBs; GPU samples=" + test.samples
                + "; primitive parts=" + test.primitiveParts + "; max bones=" + test.maxBones);
            System.out.println("CATALOG clip report: no idle=" + test.noIdle + "; no walk=" + test.noWalk + "; no animation=" + test.noAnimation);
            exit = 0;
        } catch (Throwable error) {
            originalError.println("CATALOG FAIL: " + error);
            error.printStackTrace(originalError);
        } finally {
            watchdog.interrupt();
            System.setErr(originalError);
        }
        System.exit(exit);
    }

    @Override public void create() {
        camera = new PerspectiveCamera(35, 512, 512);
        DefaultShader.Config config = new DefaultShader.Config();
        config.numBones = BONES;
        config.numDirectionalLights = 1;
        config.numPointLights = 0;
        config.numSpotLights = 0;
        batch = new ModelBatch(new DefaultShaderProvider(config));
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, .68f, .68f, .68f, 1));
        environment.add(new DirectionalLight().set(.62f, .60f, .55f, -.45f, -.85f, -.35f));
        camera.position.set(0, 30, 56);
        camera.lookAt(0, 7, 0);
        camera.near = .1f;
        camera.far = 200;
        camera.update();
        try {
            report = new PrintWriter(Files.newBufferedWriter(Path.of("catalog-results.tsv"), StandardCharsets.UTF_8));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        report.println("species\tstatus\tbones\tprimitive_parts\tanimations\tidle_clip\twalk_clip\tidle_pixels\twalk_pixels\tms\terror");
        System.out.println("CATALOG: GPU=" + Gdx.gl.glGetString(GL20.GL_RENDERER) + "; shader=DefaultShader; bones=128; material converter enabled");
    }

    @Override public void render() {
        if (index == files.length) {
            report.flush();
            complete = true;
            Gdx.app.exit();
            return;
        }
        File file = files[index++];
        String name = file.getName().replaceFirst("\\.glb$", "");
        SceneAsset asset = null;
        long started = System.nanoTime();
        try {
            asset = new GLBLoader().load(new FileHandle(file));
            if (asset.scene == null || asset.maxBones > BONES) throw new IllegalStateException("No default scene or unsupported bone count: " + asset.maxBones);
            // Match production's in-memory unique node names before ModelInstance
            // copies animations by node ID. GLTF names need not be unique.
            Class.forName("com.pkmngen.game.ActorModelRenderer")
                .getMethod("prepareSceneAsset", SceneAsset.class).invoke(null, asset);
            Scene scene = new Scene(asset.scene);
            MaterialConverter.makeCompatible(scene);
            String idle = animation(scene, "idle", "standing", "wait");
            String walk = animation(scene, "walk", "run", "move");
            if (idle != null) {
                scene.animationController.setAnimation(idle, -1);
                scene.animationController.update(0);
            }
            scene.modelInstance.calculateTransforms();
            // Use the production CPU-skinning bounds helper, so GLB root scale
            // and bone transforms match the actual DefaultShader vertices.
            BoundingBox bounds = (BoundingBox)Class.forName("com.pkmngen.game.ActorModelRenderer")
                .getMethod("calculateVisualBounds", ModelInstance.class).invoke(null, scene.modelInstance);
            if (!Float.isFinite(bounds.getHeight()) || bounds.getHeight() < .0001f) throw new IllegalStateException("Invalid geometry bounds");
            float scale = Math.min(18f / bounds.getHeight(), Math.min(25f / Math.max(.001f, bounds.getWidth()), 30f / Math.max(.001f, bounds.getDepth())));
            scene.modelInstance.transform.idt().scale(scale, scale, scale)
                .translate(-bounds.getCenterX(), -bounds.min.y, -bounds.getCenterZ());
            int parts = 0;
            for (Node node : scene.modelInstance.nodes) parts += parts(node);
            if (parts == 0) throw new IllegalStateException("No enabled primitive geometry");
            primitiveParts += parts;
            maxBones = Math.max(maxBones, asset.maxBones);
            if (idle == null) noIdle.add(name);
            if (walk == null) noWalk.add(name);
            if (scene.modelInstance.animations.size == 0) noAnimation.add(name);
            // If semantic clip names are absent, sample a real available clip and
            // report that fact. Missing clips alone do not mean broken geometry.
            String idleSample = idle;
            if (idleSample == null && scene.modelInstance.animations.size > 0) idleSample = scene.modelInstance.animations.get(0).id;
            String walkSample = walk;
            if (walkSample == null && scene.modelInstance.animations.size > 0)
                walkSample = scene.modelInstance.animations.get(Math.min(1, scene.modelInstance.animations.size - 1)).id;
            int idlePixels = sample(asset, scene, scale, name, "idle", idleSample, .37f);
            int walkPixels = sample(asset, scene, scale, name, "walk", walkSample, .71f);
            passed++;
            double ms = (System.nanoTime() - started) / 1e6;
            report.printf(Locale.ROOT, "%s\tPASS\t%d\t%d\t%d\t%s\t%s\t%d\t%d\t%.2f\t%n",
                name, asset.maxBones, parts, scene.modelInstance.animations.size, printable(idle), printable(walk), idlePixels, walkPixels, ms);
            System.out.println("CATALOG MODEL PASS " + name + " bones=" + asset.maxBones + ", parts=" + parts
                + ", clips=" + scene.modelInstance.animations.size + ", idle=" + printable(idle) + ", walk=" + printable(walk)
                + ", pixels=" + idlePixels + "/" + walkPixels);
        } catch (Throwable error) {
            failures.add(name + ": " + error);
            report.println(name + "\tFAIL\t\t\t\t\t\t\t\t\t" + printable(error.toString()));
            System.err.println("CATALOG MODEL FAIL " + name);
            error.printStackTrace(System.err);
            // Drain GL flags to keep the next asset's diagnosis independent.
            for (int i = 0; i < 16 && Gdx.gl.glGetError() != GL20.GL_NO_ERROR; i++) { }
        } finally {
            if (asset != null) asset.dispose();
            report.flush();
        }
    }

    private int sample(SceneAsset asset, Scene scene, float scale, String species, String pose, String clip, float time) throws Exception {
        // Match the runtime's fixed per-clip origin. This preserves actual pose
        // movement/bobbing at the sample time and never changes the camera.
        Vector3 anchor = (Vector3)Class.forName("com.pkmngen.game.ActorModelRenderer")
            .getMethod("calculateClipAnchor", SceneAsset.class, String.class).invoke(null, asset, clip);
        scene.modelInstance.transform.idt().scale(scale, scale, scale).translate(-anchor.x, -anchor.y, -anchor.z);
        if (clip != null) {
            scene.animationController.setAnimation(clip, -1);
            scene.animationController.update(time);
        }
        Gdx.gl.glViewport(0, 0, 512, 512);
        Gdx.gl.glClearColor(.07f, .05f, .09f, 1);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        batch.begin(camera);
        batch.render(scene.modelInstance, environment);
        batch.end();
        byte[] rgba = ScreenUtils.getFrameBufferPixels(0, 0, 512, 512, false);
        int gl = Gdx.gl.glGetError();
        if (gl != GL20.GL_NO_ERROR) throw new IllegalStateException("OpenGL error=" + gl + " pose=" + pose);
        int background = (rgba[0] & 255) << 16 | (rgba[1] & 255) << 8 | rgba[2] & 255;
        int foreground = 0;
        BufferedImage image = CAPTURE.contains(species) ? new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB) : null;
        for (int i = 0; i < rgba.length; i += 4) {
            int rgb = (rgba[i] & 255) << 16 | (rgba[i + 1] & 255) << 8 | rgba[i + 2] & 255;
            if (rgb != background) foreground++;
            if (image != null) image.setRGB(i / 4 % 512, 511 - i / 4 / 512, rgb);
        }
        if (foreground < 100) {
            BoundingBox bounds = (BoundingBox)Class.forName("com.pkmngen.game.ActorModelRenderer")
                .getMethod("calculateVisualBounds", ModelInstance.class).invoke(null, scene.modelInstance);
            throw new IllegalStateException("Geometry not visible in GPU framebuffer: " + foreground
                + " foreground pixels; pose=" + pose + "; clip=" + clip + "; time=" + time + "; bounds=" + bounds);
        }
        if (image != null && !ImageIO.write(image, "png", new File(species + "-" + pose + ".png"))) throw new IllegalStateException("PNG writer missing");
        samples++;
        return foreground;
    }

    private static String animation(Scene scene, String... terms) {
        for (String term : terms) for (Animation animation : scene.modelInstance.animations)
            if (animation.id.toLowerCase(Locale.ROOT).contains(term)) return animation.id;
        return null;
    }

    private static int parts(Node node) {
        int parts = 0;
        for (NodePart part : node.parts) if (part.enabled && part.meshPart != null && part.meshPart.size > 0) parts++;
        for (Node child : node.getChildren()) parts += parts(child);
        return parts;
    }

    private static String printable(String value) {
        return value == null ? "<none>" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    @Override public void dispose() {
        if (batch != null) batch.dispose();
        if (report != null) report.close();
        disposed = true;
    }
}
