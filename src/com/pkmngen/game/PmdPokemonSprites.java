package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Read-only presentation of exact PMDCollab species/forms, with bounded lazy GPU caches. */
public final class PmdPokemonSprites implements Disposable {
   private static final String ROOT = "visual/pmd/";
   private static final int MAX_TEXTURES = 160;
   private static final long MAX_TEXTURE_BYTES = 64L * 1024L * 1024L;
   private static final int MAX_METADATA = 80;
   private static PmdPokemonSprites shared;
   private final Map<String, Entry> entries = new HashMap<>();
   private final LinkedHashMap<String, CachedTexture> textures = new LinkedHashMap<>(32, 0.75f, true);
   private final LinkedHashMap<String, JsonValue> metadata = new LinkedHashMap<>(32, 0.75f, true);
   private final Set<String> failed = new HashSet<>();
   private long cacheFrame = Long.MIN_VALUE;
   private long textureBytes;
   private long portraitFrames;
   private long animationFrames;
   private long fallbackFrames;
   private boolean available;

   /** Called on the LibGDX render thread, after Gdx.files and GL exist. */
   public static PmdPokemonSprites get() {
      if (shared == null) shared = new PmdPokemonSprites();
      return shared;
   }

   public static void disposeShared() {
      if (shared != null) {
         shared.dispose();
         shared = null;
      }
   }

   private PmdPokemonSprites() {
      FileHandle file = Gdx.files.internal(ROOT + "catalog.json");
      if (!file.exists()) return;
      try {
         JsonValue catalog = new JsonReader().parse(file).get("species");
         for (JsonValue value = catalog.child; value != null; value = value.next) {
            Entry entry = new Entry();
            entry.sourcePath = value.getString("sourcePath");
            entry.portrait = value.getString("portrait", null);
            entry.fallback = value.getString("animationFallback", null);
            JsonValue animations = value.get("animations");
            for (JsonValue anim = animations.child; anim != null; anim = anim.next) {
               entry.animations.put(anim.name.toLowerCase(Locale.ROOT), anim.name);
            }
            entries.put(value.name.toLowerCase(Locale.ROOT), entry);
         }
         available = true;
      } catch (RuntimeException ex) {
         Gdx.app.error("PmdSprites", "Invalid PMDCollab catalog; preserving original graphics.", ex);
         entries.clear();
      }
   }

   public boolean isAvailable() { return available; }
   public long getPortraitFrames() { return portraitFrames; }
   public long getAnimationFrames() { return animationFrames; }
   public long getFallbackFrames() { return fallbackFrames; }
   public int getLoadedTextureCount() { return textures.size(); }
   public long getLoadedTextureBytes() { return textureBytes; }
   public boolean hasPortrait(String species) {
      Entry entry = entry(species);
      return entry != null && entry.portrait != null;
   }
   public boolean hasAnimation(String species, String animation) {
      Entry entry = entry(species);
      return entry != null && animation != null && entry.animations.containsKey(animation.toLowerCase(Locale.ROOT));
   }

   /** Null is deliberate: an unavailable shiny/form must retain its original graphic. */
   public TextureRegion portrait(Pokemon pokemon) {
      return portrait(key(pokemon));
   }

   public TextureRegion portrait(String species) {
      Entry entry = entry(species);
      if (entry == null || entry.portrait == null) {
         fallbackFrames++;
         return null;
      }
      CachedTexture texture = texture(entry.portrait, null);
      if (texture == null) return null;
      portraitFrames++;
      return texture.portrait;
   }

   public Frame frame(Pokemon pokemon, String animation, float stateTime) {
      return frame(pokemon, pokemon == null ? "down" : pokemon.dirFacing, animation, stateTime);
   }

   public Frame frame(Pokemon pokemon, String direction, String animation, float stateTime) {
      return frame(key(pokemon), direction, animation, stateTime);
   }

   /** PMD cells are timed at 60 ticks/second; stateTime is elapsed seconds. */
   public Frame frame(String species, String direction, String animation, float stateTime) {
      Entry entry = entry(species);
      if (entry == null) {
         fallbackFrames++;
         return null;
      }
      String selected = entry.animations.get((animation == null ? "Idle" : animation).toLowerCase(Locale.ROOT));
      if (selected == null) selected = entry.fallback;
      if (selected == null) {
         fallbackFrames++;
         return null;
      }
      JsonValue animations = metadata(entry.sourcePath);
      if (animations == null) return null;
      JsonValue data = animations.get(selected);
      if (data == null) return null;
      CachedTexture texture = texture(data.getString("file"), data);
      if (texture == null || texture.frames == null) return null;
      int tick = Float.isNaN(stateTime) || Float.isInfinite(stateTime) ? 0
         : (int)(Math.max(0d, stateTime * 60d) % texture.totalTicks);
      int column = 0;
      while (column < texture.durations.length - 1 && tick >= texture.durations[column]) {
         tick -= texture.durations[column++];
      }
      int row = texture.rows == 1 ? 0 : directionIndex(direction);
      animationFrames++;
      return texture.frames[row * texture.durations.length + column];
   }

   /** Sheet rows are S, SE, E, NE, N, NW, W, SW (PMD export order). */
   public static int directionIndex(String direction) {
      if (direction == null) return 0;
      switch (direction.toLowerCase(Locale.ROOT).replace('_', '-')) {
         case "down-right": case "southeast": return 1;
         case "right": case "east": return 2;
         case "up-right": case "northeast": return 3;
         case "up": case "north": return 4;
         case "up-left": case "northwest": return 5;
         case "left": case "west": return 6;
         case "down-left": case "southwest": return 7;
         default: return 0;
      }
   }

   private static String key(Pokemon pokemon) {
      if (pokemon == null || pokemon.specie == null || pokemon.isEgg || pokemon.isGhost) return null;
      return pokemon.specie.name.toLowerCase(Locale.ROOT) + (pokemon.isShiny ? "#shiny" : "");
   }

   private Entry entry(String key) {
      if (key == null || !available) return null;
      String normalized = key.toLowerCase(Locale.ROOT);
      Entry result = entries.get(normalized);
      if (result == null) {
         // Punctuation differs between the old game and National Dex slugs.
         // Only normalize punctuation; never strip a regional/form identifier.
         String[] parts = normalized.split("#", 2);
         String basic = parts[0].replaceAll("[^a-z0-9]", "");
         if (basic.equals("nidoranf")) basic = "nidoran_f";
         else if (basic.equals("nidoranm")) basic = "nidoran_m";
         else if (basic.equals("farfetchd")) basic = "farfetch_d";
         else if (basic.equals("hooh")) basic = "ho_oh";
         result = entries.get(basic + (parts.length == 2 ? "#" + parts[1] : ""));
      }
      return result;
   }

   private JsonValue metadata(String sourcePath) {
      JsonValue cached = metadata.get(sourcePath);
      if (cached != null) return cached;
      String path = "sprite/" + sourcePath + "/metadata.json";
      if (failed.contains(path)) return null;
      try {
         cached = new JsonReader().parse(Gdx.files.internal(ROOT + path));
         metadata.put(sourcePath, cached);
         while (metadata.size() > MAX_METADATA) metadata.remove(metadata.keySet().iterator().next());
         return cached;
      } catch (RuntimeException ex) {
         failed.add(path);
         Gdx.app.error("PmdSprites", "Unavailable animation metadata: " + path, ex);
         return null;
      }
   }

   /** Eviction only removes textures from an earlier frame, never queued draw regions. */
   public void beginFrame() {
      long frame = Gdx.graphics.getFrameId();
      if (cacheFrame == frame) return;
      cacheFrame = frame;
      trim();
   }

   private void trim() {
      Iterator<Map.Entry<String, CachedTexture>> iterator = textures.entrySet().iterator();
      while ((textures.size() > MAX_TEXTURES || textureBytes > MAX_TEXTURE_BYTES) && iterator.hasNext()) {
         CachedTexture value = iterator.next().getValue();
         if (value.lastFrame == cacheFrame) continue;
         textureBytes -= value.bytes;
         value.texture.dispose();
         iterator.remove();
      }
   }

   private CachedTexture texture(String path, JsonValue animation) {
      beginFrame();
      CachedTexture cached = textures.get(path);
      if (cached != null) {
         cached.lastFrame = cacheFrame;
         return cached;
      }
      if (failed.contains(path)) return null;
      Texture texture = null;
      try {
         texture = new Texture(Gdx.files.internal(ROOT + path));
         texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
         cached = new CachedTexture();
         cached.texture = texture;
         cached.lastFrame = cacheFrame;
         cached.bytes = texture.getWidth() * (long)texture.getHeight() * 4L;
         if (animation == null) cached.portrait = new TextureRegion(texture);
         else {
            cached.rows = animation.getInt("rows");
            cached.durations = animation.get("durations").asIntArray();
            for (int duration : cached.durations) cached.totalTicks += duration;
            JsonValue frames = animation.get("frames");
            cached.frames = new Frame[frames.size];
            String sourceAnimation = animation.getString("sourceAnimation");
            int index = 0;
            for (JsonValue values = frames.child; values != null; values = values.next) {
               float[] cells = values.asFloatArray();
               TextureRegion region = new TextureRegion(texture, (int)cells[0], (int)cells[1], (int)cells[2], (int)cells[3]);
               cached.frames[index++] = new Frame(region, cells[4], cells[5], sourceAnimation);
            }
         }
         textures.put(path, cached);
         textureBytes += cached.bytes;
         // Trimming here is safe because every region obtained this frame was pinned.
         trim();
         return cached;
      } catch (RuntimeException ex) {
         if (texture != null) texture.dispose();
         failed.add(path);
         Gdx.app.error("PmdSprites", "Unavailable sprite sheet: " + path, ex);
         return null;
      }
   }

   @Override public void dispose() {
      for (CachedTexture texture : textures.values()) texture.texture.dispose();
      textures.clear();
      metadata.clear();
      failed.clear();
      textureBytes = 0L;
   }

   private static final class Entry {
      String sourcePath;
      String portrait;
      String fallback;
      final Map<String, String> animations = new HashMap<>();
   }

   private static final class CachedTexture {
      Texture texture;
      TextureRegion portrait;
      Frame[] frames;
      int[] durations;
      int rows;
      int totalTicks;
      long bytes;
      long lastFrame;
   }

   /**
    * Region clips transparent padding only. width/height are its exact pixel size.
    * anchorX/anchorY locate the original ground origin relative to its bottom-left.
    * Render at (groundX-anchorX*scale, groundY-anchorY*scale), without re-centering
    * each frame; this preserves jumps and attack movement present in the source.
    */
   public static final class Frame {
      public final TextureRegion region;
      public final float width;
      public final float height;
      public final float anchorX;
      public final float anchorY;
      public final String sourceAnimation;
      Frame(TextureRegion region, float anchorX, float anchorY, String sourceAnimation) {
         this.region = region;
         this.width = region.getRegionWidth();
         this.height = region.getRegionHeight();
         this.anchorX = anchorX;
         this.anchorY = anchorY;
         this.sourceAnimation = sourceAnimation;
      }
   }
}
