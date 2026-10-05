import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.pkmngen.game.PmdPokemonSprites;
import java.util.ArrayList;
import java.util.List;

/** Actual desktop GL test of every exact PMD catalog entry, timing and cache eviction. */
public final class PmdAssetSmokeTest extends ApplicationAdapter {
   private static final String[] DIRECTIONS = {"down", "down-right", "right", "up-right", "up", "up-left", "left", "down-left"};
   private final List<JsonValue> entries = new ArrayList<>();
   private SpriteBatch batch;
   private BitmapFont font;
   private PmdPokemonSprites sprites;
   private int index, portraits, animations, rows, timingChecks, missingPortraits, missingSprites, maxTextures;
   private long maxBytes;
   private boolean completed;

   public static void main(String[] args) {
      PmdAssetSmokeTest test = new PmdAssetSmokeTest();
      Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
      config.setWindowedMode(1024, 512);
      config.setInitialVisible(false);
      config.setTitle("PMDCollab GPU validation");
      config.setForegroundFPS(0);
      config.setIdleFPS(0);
      config.useVsync(false);
      config.disableAudio(true);
      new Lwjgl3Application(test, config);
      if (!test.completed) throw new IllegalStateException("PMD test did not complete");
      System.out.println("PMD PASS variants=" + test.entries.size() + " portraits=" + test.portraits
         + " animations=" + test.animations + " directionChecks=" + test.rows + " timingChecks=" + test.timingChecks
         + " missingPortraits=" + test.missingPortraits + " missingSprites=" + test.missingSprites
         + " maxTextures=" + test.maxTextures + " maxTextureBytes=" + test.maxBytes);
   }

   @Override public void create() {
      JsonValue values = new JsonReader().parse(Gdx.files.internal("visual/pmd/catalog.json")).get("species");
      for (JsonValue value = values.child; value != null; value = value.next) entries.add(value);
      check(entries.size() >= 1025, "Incomplete dex catalog");
      sprites = PmdPokemonSprites.get();
      check(sprites.isAvailable(), "PMD catalog did not load");
      batch = new SpriteBatch();
      font = new BitmapFont();
      System.out.println("GL " + Gdx.gl.glGetString(GL20.GL_VERSION));
   }

   @Override public void render() {
      ScreenUtils.clear(0.09f, 0.15f, 0.2f, 1f);
      sprites.beginFrame();
      if (index < entries.size()) {
         JsonValue entry = entries.get(index++);
         TextureRegion portrait = sprites.portrait(entry.name);
         check((portrait != null) == !entry.get("portrait").isNull(), "Portrait coverage mismatch: " + entry.name);
         batch.begin();
         if (portrait != null) {
            portraits++;
            batch.draw(portrait, 12, 420, 80, 80);
         } else missingPortraits++;
         int animationRow = 0;
         JsonValue expectedAnimations = entry.get("animations");
         if (expectedAnimations.size == 0) {
            missingSprites++;
            check(sprites.frame(entry.name, "down", "Idle", 0f) == null, "Missing variant substituted: " + entry.name);
         } else {
            JsonValue metadata = new JsonReader().parse(Gdx.files.internal("visual/pmd/sprite/" + entry.getString("sourcePath") + "/metadata.json"));
            for (JsonValue animation = expectedAnimations.child; animation != null; animation = animation.next) {
               JsonValue data = metadata.get(animation.name);
               int columns = data.get("durations").size;
               int expectedRows = data.getInt("rows");
               for (int direction = 0; direction < DIRECTIONS.length; direction++) {
                  PmdPokemonSprites.Frame frame = sprites.frame(entry.name, DIRECTIONS[direction], animation.name, 0f);
                  check(frame != null, "Missing frame " + entry.name + " " + animation.name);
                  float[] cell = data.get("frames").get((expectedRows == 1 ? 0 : direction) * columns).asFloatArray();
                  check(frame.region.getRegionX() == (int)cell[0] && frame.region.getRegionY() == (int)cell[1], "Direction row mismatch " + entry.name);
                  check(frame.anchorX == cell[4] && frame.anchorY == cell[5], "Ground anchor mismatch " + entry.name);
                  batch.draw(frame.region, 130 + direction * 106 - frame.anchorX, 65 + animationRow * 82 - frame.anchorY, frame.width, frame.height);
                  rows++;
               }
               if (columns > 1) {
                  int firstDuration = data.get("durations").getInt(0);
                  PmdPokemonSprites.Frame first = sprites.frame(entry.name, "down", animation.name, 0f);
                  PmdPokemonSprites.Frame second = sprites.frame(entry.name, "down", animation.name, (firstDuration + .1f) / 60f);
                  float[] cell = data.get("frames").get(1).asFloatArray();
                  check(second != first && second.region.getRegionX() == (int)cell[0] && second.region.getRegionY() == (int)cell[1], "Animation timing mismatch " + entry.name);
                  timingChecks++;
               }
               animations++;
               animationRow++;
            }
         }
         batch.end();
         check(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "OpenGL error at " + entry.name);
         maxTextures = Math.max(maxTextures, sprites.getLoadedTextureCount());
         maxBytes = Math.max(maxBytes, sprites.getLoadedTextureBytes());
         check(sprites.getLoadedTextureCount() <= 160, "Texture count not bounded");
         check(sprites.getLoadedTextureBytes() <= 64L * 1024L * 1024L, "Texture bytes not bounded");
         if (index % 250 == 0) System.out.println("PMD variants checked " + index + "/" + entries.size());
         return;
      }
      if (index == entries.size()) { capture("pmd-sample-idle.png", 0f); index++; return; }
      if (index == entries.size() + 1) { capture("pmd-sample-walk.png", .2f); index++; return; }
      check(sprites.frame("gdarmanitanzen", "down", "Walk", 0f) == null, "Missing exact form incorrectly replaced by base");
      check(sprites.frame("not-a-pokemon", "down", "Walk", 0f) == null, "Missing species not null");
      check(sprites.portrait("pikachu#shiny") != null, "Known shiny portrait missing");
      completed = true;
      Gdx.app.exit();
   }

   private void capture(String output, float time) {
      String[] samples = {"machop", "pikachu", "charizard", "gyarados", "araichu", "unown_qmark", "sprigatito", "quaxly", "fuecoco", "ceruledge", "ogerpon", "pikachu#shiny"};
      batch.begin();
      font.setColor(Color.WHITE);
      for (int n = 0; n < samples.length; n++) {
         float x = 24 + (n % 6) * 169;
         float y = 380 - (n / 6) * 240;
         TextureRegion portrait = sprites.portrait(samples[n]);
         if (portrait != null) batch.draw(portrait, x, y + 18, 80, 80);
         PmdPokemonSprites.Frame frame = sprites.frame(samples[n], "down", "Walk", time);
         if (frame != null) batch.draw(frame.region, x + 55 - frame.anchorX * 2, y - 58 - frame.anchorY * 2, frame.width * 2, frame.height * 2);
         font.draw(batch, samples[n], x, y - 90);
      }
      batch.end();
      Pixmap pixels = ScreenUtils.getFrameBufferPixmap(0, 0, 1024, 512);
      PixmapIO.PNG writer = new PixmapIO.PNG();
      writer.setFlipY(true);
      try { writer.write(Gdx.files.local(output), pixels); }
      catch (java.io.IOException ex) { throw new RuntimeException(ex); }
      finally { writer.dispose(); pixels.dispose(); }
   }

   private static void check(boolean test, String message) {
      if (!test) throw new IllegalStateException(message);
   }

   @Override public void dispose() {
      batch.dispose(); font.dispose(); PmdPokemonSprites.disposeShared();
   }
}
