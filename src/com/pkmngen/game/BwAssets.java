package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared landscape/animated presentation assets with optional custom sampling.
 * Tile state, collision and saves never change. Landscape credits: visual/landscape;
 * B/W avatars: visual/unova. Cached regions are read-only and owned here. */
public final class BwAssets {
   private static final String BASE_TEXTURE = "visual/stardew/world-atlas.png";
   private static final String BASE_ATLAS = "visual/stardew/world-atlas.json";
   private static final String TRAINER_TEXTURE = "visual/unova/world-atlas.png";
   private static final String TRAINER_ATLAS = "visual/unova/world-atlas.json";
   private static final String OVERRIDES = "visual/custom/world-overrides.json";
   private static BwAssets shared;
   private final Map<String, Texture> textures = new LinkedHashMap<>();
   private final Map<String, TextureRegion> regions = new HashMap<>();
   private final Map<String, TextureRegion[]> cells = new HashMap<>();
   private final Map<String, VisualSampling> sampling = new HashMap<>();
   private final Map<TextureRegion, SpriteLayout> layouts = new IdentityHashMap<>();
   private final Map<String, String[]> animations = new HashMap<>();
   private final Map<String, Float> speeds = new HashMap<>();
   private final Map<String, Float> anchorsX = new HashMap<>();
   private final Map<String, Float> anchorsY = new HashMap<>();
   private final TrainerCatalog trainers = new TrainerCatalog();
   private final MountedTrainerSprites mounted = new MountedTrainerSprites();
   private final Set<String> customRegions = new HashSet<>();
   private static final SpriteLayout TRAINER_LAYOUT = new SpriteLayout();
   private final Set<String> missing = new HashSet<>();

   private BwAssets() {
      try {
         loadAtlas(Gdx.files.internal(BASE_ATLAS), BASE_TEXTURE, true, false);
         // The previous world atlas contributes avatars only, never old scenery.
         loadAtlas(Gdx.files.internal(TRAINER_ATLAS), TRAINER_TEXTURE, true, true);
         FileHandle overrides = Gdx.files.internal(OVERRIDES);
         if (overrides.exists()) loadAtlas(overrides, BASE_TEXTURE, false, false);
      } catch (RuntimeException error) {
         disposeTextures();
         throw error;
      }
   }

   private void loadAtlas(FileHandle file, String defaultTexture, boolean builtin, boolean trainersOnly) {
      JsonValue root = new JsonReader().parse(file);
      if (!root.isObject()) throw new IllegalArgumentException(file + ": atlas must be an object.");
      int schema = integer(root,"schemaVersion",1);
      if(schema<1||schema>2)throw new IllegalArgumentException(file+": unsupported material schema "+schema);
      if(schema==2) {
         JsonValue contract=root.get("coordinateContract");
         String expected=builtin?"source-pixels":"normalized";
         if(contract==null||!expected.equals(contract.getString("anchorUnit","")))
            throw new IllegalArgumentException(file+": schema 2 anchorUnit must be "+expected);
      }
      JsonValue entries = root.get("regions");
      if (entries == null) entries = root;
      else defaultTexture = root.getString("texture", defaultTexture);
      if (!entries.isObject()) throw new IllegalArgumentException(file + ": regions must be an object.");
      for (JsonValue entry = entries.child; entry != null; entry = entry.next) {
         if (trainersOnly && !entry.name.startsWith("trainer_")) continue;
         try {
            loadRegion(entry, defaultTexture, builtin);
         } catch (RuntimeException error) {
            throw new IllegalArgumentException(file + ": invalid region '" + entry.name + "': " + error.getMessage(), error);
         }
      }
      JsonValue sequences = builtin && !trainersOnly ? root.get("animations") : null;
      if (sequences != null) for (JsonValue entry = sequences.child; entry != null; entry = entry.next) {
         String[] frames = entry.get("frames").asStringArray();
         float fps = number(entry, "fps", 0);
         if (frames.length == 0 || fps <= 0) throw new IllegalArgumentException(file + ": invalid animation '" + entry.name + "'.");
         for (String frame : frames) if (!regions.containsKey(frame))
            throw new IllegalArgumentException(file + ": missing animation frame '" + frame + "'.");
         animations.put(entry.name, frames);
         speeds.put(entry.name, fps);
      }
   }

   private void loadRegion(JsonValue entry, String defaultTexture, boolean builtin) {
      if (!entry.isObject()) throw new IllegalArgumentException("Region must be an object.");
      Texture texture = texture(entry.getString("texture", defaultTexture));
      int x = integer(entry, "x", 0), y = integer(entry, "y", 0);
      int width = integer(entry, "width", -1), height = integer(entry, "height", -1);
      if (x < 0 || y < 0 || width <= 0 || height <= 0
         || (long)x + width > texture.getWidth() || (long)y + height > texture.getHeight())
         throw new IllegalArgumentException("Crop must be positive and inside its texture.");
      int sampleWidth = integer(entry, "sampleWidth", Math.min(16, width));
      int sampleHeight = integer(entry, "sampleHeight", Math.min(16, height));
      int gridWidth = width, gridHeight = height;
      if (builtin && entry.get("sampleWidth") == null && entry.get("sampleHeight") == null) {
         // Preserve the exact old 16px cell mosaic for irregular built-in props.
         // named() still exposes their complete image. Custom regions below must
         // divide exactly: no custom source pixels are silently discarded.
         gridWidth = Math.max(1, width / 16) * sampleWidth;
         gridHeight = Math.max(1, height / 16) * sampleHeight;
      }
      VisualSampling grid = new VisualSampling(gridWidth, gridHeight, sampleWidth, sampleHeight);
      SpriteLayout layout = new SpriteLayout(entry, width, height, builtin);
      TextureRegion region = new TextureRegion(texture, x, y, width, height);
      TextureRegion[] parts = new TextureRegion[grid.count()];
      for (int i = 0; i < parts.length; i++)
         parts[i] = new TextureRegion(region, grid.sourceX(i), grid.sourceY(i), grid.sampleWidth, grid.sampleHeight);
      TextureRegion previous = regions.put(entry.name, region);
      if (previous != null) layouts.remove(previous);
      layouts.put(region, layout);
      sampling.put(entry.name, grid);
      cells.put(entry.name, parts);
      // Public legacy anchor access remains source pixels for existing consumers.
      anchorsX.put(entry.name, layout.anchorX(.5f) * width);
      anchorsY.put(entry.name, layout.anchorY(0f) * height);
      if (!builtin) {
         customRegions.add(entry.name);
         // Replacing an animation alias is an explicit static override; replacing
         // an individual frame instead preserves the original animation sequence.
         animations.remove(entry.name);
         speeds.remove(entry.name);
      }
   }

   private Texture texture(String path) {
      if (path == null || path.isEmpty()) throw new IllegalArgumentException("Missing texture path.");
      path = path.replace('\\', '/');
      if (path.startsWith("/") || path.contains(":") || path.equals("..") || path.startsWith("../")
         || path.contains("/../") || path.endsWith("/.."))
         throw new IllegalArgumentException("Texture paths must be relative internal asset paths.");
      Texture texture = textures.get(path);
      if (texture == null) {
         texture = new Texture(Gdx.files.internal(path));
         // Register ownership before setting GL state so constructor failures also clean up.
         textures.put(path, texture);
         texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
         texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
      }
      return texture;
   }

   private void disposeTextures() {
      mounted.dispose();
      trainers.dispose();
      for (Texture texture : textures.values()) texture.dispose();
      textures.clear();
      regions.clear();
      cells.clear();
      sampling.clear();
      layouts.clear();
      animations.clear();
      speeds.clear();
      anchorsX.clear();
      anchorsY.clear();
      missing.clear();
      customRegions.clear();
   }

   private static int integer(JsonValue object, String key, int fallback) {
      JsonValue value = object.get(key);
      if (value == null) return fallback;
      double number = value.isNumber() ? value.asDouble() : Double.NaN;
      if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
         throw new IllegalArgumentException(key + " must be an integer.");
      return (int)number;
   }

   private static float number(JsonValue object, String key, float fallback) {
      JsonValue value = object.get(key);
      if (value == null) return fallback;
      float result = value.isNumber() ? value.asFloat() : Float.NaN;
      if (!Float.isFinite(result)) throw new IllegalArgumentException(key + " must be finite.");
      return result;
   }

   private static float optionalPositive(JsonValue object, String key) {
      float result = number(object, key, Float.NaN);
      if (!Float.isNaN(result) && result <= 0f) throw new IllegalArgumentException(key + " must be positive.");
      return result;
   }

   private static float optionalAnchor(JsonValue object, String key) {
      float result = number(object, key, Float.NaN);
      if (!Float.isNaN(result) && (result < 0f || result > 1f))
         throw new IllegalArgumentException(key + " must be in [0, 1].");
      return result;
   }

   /** Built-in landscape metadata measures anchors in source pixels. Custom
    * metadata is deliberately a separate format with normalized [0,1] anchors. */
   private static float pixelAnchor(JsonValue entry, String key, int size) {
      float pixels = number(entry, key, Float.NaN);
      if (Float.isNaN(pixels)) return Float.NaN;
      if (pixels < 0 || pixels > size) throw new IllegalArgumentException(key + " must lie inside its built-in region.");
      return pixels / size;
   }

   /** Visual sizes are world units, anchors are fractions measured from left/bottom.
    * Missing fields preserve each renderer's existing size and anchor. */
   public static final class SpriteLayout {
      private final float worldWidth, worldHeight, anchorX, anchorY;
      public final float offsetX, offsetY, elevation;
      public final boolean customized;

      private SpriteLayout() {
         worldWidth=worldHeight=anchorX=anchorY=Float.NaN;
         offsetX=offsetY=elevation=0f;customized=false;
      }

      private SpriteLayout(JsonValue entry, int width, int height, boolean builtin) {
         worldWidth = optionalPositive(entry, "worldWidth");
         worldHeight = optionalPositive(entry, "worldHeight");
         anchorX = builtin ? pixelAnchor(entry, "anchorX", width) : optionalAnchor(entry, "anchorX");
         anchorY = builtin ? pixelAnchor(entry, "anchorY", height) : optionalAnchor(entry, "anchorY");
         offsetX = number(entry, "offsetX", 0f);
         offsetY = number(entry, "offsetY", 0f);
         elevation = number(entry, "elevation", 0f);
         customized = !Float.isNaN(worldWidth) || !Float.isNaN(worldHeight)
            || !Float.isNaN(anchorX) || !Float.isNaN(anchorY)
            || offsetX != 0f || offsetY != 0f || elevation != 0f;
      }

      public float width(TextureRegion region, float fallback) {
         if (!Float.isNaN(worldWidth)) return worldWidth;
         return Float.isNaN(worldHeight) ? fallback : worldHeight * region.getRegionWidth() / region.getRegionHeight();
      }
      public float height(TextureRegion region, float fallback) {
         if (!Float.isNaN(worldHeight)) return worldHeight;
         return Float.isNaN(worldWidth) ? fallback : worldWidth * region.getRegionHeight() / region.getRegionWidth();
      }
      public float anchorX(float fallback) { return Float.isNaN(anchorX) ? fallback : anchorX; }
      public float anchorY(float fallback) { return Float.isNaN(anchorY) ? fallback : anchorY; }
   }

   public static BwAssets get() { if (shared == null) shared = new BwAssets(); return shared; }
   public static void disposeShared() { if (shared != null) { shared.disposeTextures(); shared = null; } }
   public TextureRegion named(String name) { return regions.get(name); }
   public SpriteLayout layout(TextureRegion region) {
      SpriteLayout result=layouts.get(region);
      return result==null&&trainers.owns(region)?TRAINER_LAYOUT:result;
   }
   public float worldWidth(TextureRegion region, float fallback) {
      SpriteLayout layout = layouts.get(region);
      return layout == null ? fallback : layout.width(region, fallback);
   }
   public float worldHeight(TextureRegion region, float fallback) {
      SpriteLayout layout = layouts.get(region);
      return layout == null ? fallback : layout.height(region, fallback);
   }
   /** Missing props stay invisible. Keep enough context to diagnose a real asset gap. */
   public void reportMissing(String key, Tile tile, String timeOfDay) {
      String biome = BiomeProfiles.visualForTile(tile).id;
      String ground = tile == null ? "<none>" : String.valueOf(tile.name);
      String object = tile == null ? "<none>" : String.valueOf(tile.nameUpper);
      String message = "asset=" + key + " biome=" + biome + " tile=" + ground
         + " object=" + object + " time=" + timeOfDay;
      if (missing.size() < 256 && missing.add(message)) Gdx.app.error("ModernAssets", message);
   }
   public float anchorX(String name) { return anchorsX.getOrDefault(name, 0f); }
   public float anchorY(String name) { return anchorsY.getOrDefault(name, 0f); }
   /** Frame selection is pure; negative/invalid clocks cannot escape the frame array. */
   public TextureRegion namedAnimated(String name, float seconds) { return named(frameName(name, seconds)); }
   private String frameName(String name, float seconds) {
      String[] frames = animations.get(name);
      if (frames == null) return name;
      float time = Float.isFinite(seconds) ? seconds : 0f;
      return frames[Math.floorMod((int)Math.floor(time * speeds.get(name)), frames.length)];
   }
   /** Source-pixel samples occupy the same 16-unit world cells at any resolution. */
   public TextureRegion cell(String name, float x, float y) {
      VisualSampling grid = sampling.get(name);
      return grid == null ? null : cells.get(name)[grid.index(x, y)];
   }
   public TextureRegion cell(String name, float x, float y, float seconds) {
      return cell(frameName(name, seconds), x, y);
   }
   public TextureRegion trainer(Player player, float seconds, boolean moving) {
      return trainer(player, player==null?"down":player.dirFacing,seconds,moving);
   }
   public TextureRegion trainer(Player player,String direction,float seconds,boolean moving) {
      return trainerFrame(player==null?"gold":player.character,direction,seconds,moving,
         running(player,moving),player==null?null:player.color,CharacterAppearance.player(player));
   }
   /** Dedicated mounted body, resolved from the idle identity and its current wardrobe. */
   TextureRegion mountedTrainer(Player player,String move,float seconds) {
      String id=lower(player==null?"gold":player.character),direction=player==null?"down":player.dirFacing;
      String dir="up".equals(direction)||"left".equals(direction)||"right".equals(direction)?direction:"down";
      String pose=MountedTrainerSprites.pose(move);
      TextureRegion override=trainerOverride(id,pose,dir,1);
      if(override==null)override=trainerOverride(femaleTrainer(id)?"female":"male",pose,dir,1);
      if(override!=null)return override;
      TextureRegion idle=trainer(player,dir,seconds,false);
      return mounted.frame(idle,move,dir,this::trainerPixels);
   }
   private Pixmap trainerPixels(TextureRegion region) {
      Pixmap copy=trainers.copyPixels(region);if(copy!=null)return copy;
      for(Map.Entry<String,Texture> entry:textures.entrySet())if(entry.getValue()==region.getTexture()) {
         Pixmap source=new Pixmap(Gdx.files.internal(entry.getKey()));
         try{return MountedTrainerSprites.copyRegion(source,region);}finally{source.dispose();}
      }
      throw new IllegalArgumentException("Unowned trainer texture cannot be posed");
   }
   public TextureRegion classicTrainer(String character,String direction,float seconds,boolean moving,com.badlogic.gdx.graphics.Color color) { return trainers.frame(character,direction,seconds,moving,color); }
   public TextureRegion trainer(String character, String direction, float seconds, boolean moving) {
      return trainerFrame(character,direction,seconds,moving,false,null);
   }
   public TextureRegion detailedTrainer(String character, String direction, float seconds, boolean moving) {
      return trainerFrame(character,direction,seconds,moving,false,null);
   }
   /** Menu previews share exactly the world appearance, including installed mods. */
   TextureRegion trainerPreview(String character,String direction,float seconds,boolean moving,com.badlogic.gdx.graphics.Color color) {
      return trainerFrame(character,direction,seconds,moving,false,color);
   }
   TextureRegion trainerPreview(String character,String direction,float seconds,boolean moving,com.badlogic.gdx.graphics.Color color,CharacterAppearance appearance) {
      return trainerFrame(character,direction,seconds,moving,false,color,appearance);
   }
   private static boolean running(Player player,boolean moving) {
      Game game=Game.staticGame;
      if(!moving||player==null||game==null||player.isSleeping||player.isSitting
         ||player.isFishing||player.currFieldMove!=null&&!player.currFieldMove.isEmpty())return false;
      // The action owns motion; held buttons and stale currState are not a run state.
      for(Action action:game.actionStack)
         if(action instanceof PlayerRunning&&((PlayerRunning)action).player==player)return true;
      return false;
   }
   private TextureRegion trainerFrame(String character,String direction,float seconds,boolean moving,
         boolean running,com.badlogic.gdx.graphics.Color color) {
      return trainerFrame(character,direction,seconds,moving,running,color,null);
   }
   private TextureRegion trainerFrame(String character,String direction,float seconds,boolean moving,
         boolean running,com.badlogic.gdx.graphics.Color color,CharacterAppearance appearance) {
      String name=character==null?"gold":character;
      String id=lower(name);
      boolean female = femaleTrainer(id);
      String dir = "up".equals(direction) || "left".equals(direction) || "right".equals(direction) ? direction : "down";
      int frame=TrainerCatalog.animationFrame(seconds,moving,running);
      String sex=female?"female":"male",pose=running?"run":"walk";
      TextureRegion override=trainerOverride(id,pose,dir,frame);
      if(override==null&&running)override=trainerOverride(id,"walk",dir,frame);
      // Existing generic custom atlas keys retain their original idle=1 contract.
      if(override==null)override=trainerOverride(sex,pose,dir,frame);
      if(override==null&&running)override=trainerOverride(sex,"walk",dir,frame);
      if(override!=null)return override;
      if(TrainerCatalog.hasMod(name)) {
         TextureRegion mod=trainers.frame(name,dir,seconds,moving,running,color);
         if(mod!=null)return mod;
      }
      TextureRegion customized=trainers.appearanceFrame(id,dir,seconds,moving,running,appearance,color);
      if(customized!=null)return customized;
      TextureRegion modern=trainers.modernFrame(id,dir,seconds,moving,running,color);
      if(modern!=null)return modern;
      // Original B/W sheets place idle first: left step, idle, right step, idle.
      int originalFrame=frame==0?1:frame==1?0:2;
      return named("trainer_"+sex+"_"+pose+"_"+dir+"_"+originalFrame);
   }
   private TextureRegion trainerOverride(String id,String pose,String direction,int frame) {
      String key="trainer_"+id+"_"+pose+"_"+direction+"_"+frame;
      return customRegions.contains(key)?named(key):null;
   }
   private static boolean femaleTrainer(String id) {
      return Arrays.asList("kris","lyra","leaf","may","dawn","hilda","rosa","serena","selene","gloria","elaine","summer","mint").contains(id);
   }
   boolean hasTrainerOverride(String character) {
      String id=lower(character==null?"gold":character),sex=femaleTrainer(id)?"female":"male";
      for(String key:customRegions)if(key.startsWith("trainer_"+id+"_")||key.startsWith("trainer_"+sex+"_"))return true;
      return false;
   }
   public TextureRegion terrain(Tile tile) { return terrain(tile, 0f); }
   /** Total terrain mapping: even unknown biome tiles use an explicit modern material. */
   public TextureRegion terrain(Tile tile, float seconds) {
      String name = terrainName(tile);
      TextureRegion result = cell(name, tile == null || tile.position == null ? 0 : tile.position.x,
         tile == null || tile.position == null ? 0 : tile.position.y, seconds);
      return result != null ? result : named("grass_light");
   }
   public static String terrainName(Tile tile) { return WorldMaterials.terrainName(tile); }
   public TextureRegion object(Tile tile) { return object(tile, 0f); }
   public TextureRegion object(Tile tile, float seconds) {
      String key = objectName(tile);
      return key == null ? null : namedAnimated(key, seconds);
   }
   /** Visual identity only. Callers still use the original flags for geometry/collision.
    * Null means a pure surface or an intentionally invisible script marker. */
   public static String objectName(Tile tile) { return WorldMaterials.objectName(tile); }
   private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
}
