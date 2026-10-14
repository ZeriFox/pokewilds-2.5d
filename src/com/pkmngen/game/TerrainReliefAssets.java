package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.HashMap;
import java.util.Map;

/** Dedicated geological materials. Regions are shared, read-only and owned here. */
public final class TerrainReliefAssets {
   private static TerrainReliefAssets shared;
   private final Map<String, Texture> textures = new HashMap<>();
   private final Map<String, TextureRegion> regions = new HashMap<>();
   private final java.util.Set<String> missing = new java.util.HashSet<>();

   private TerrainReliefAssets() {}
   public static TerrainReliefAssets get() {
      if (shared == null) shared = new TerrainReliefAssets();
      return shared;
   }
   public TextureRegion face(Tile tile, BiomeProfiles.Profile profile) {
      // Fluid banks retain their own wet material; this set describes dry relief.
      if (tile != null && (tile.isWater || tile.isLava)) return null;
      return region(earth(tile,profile) ? "earth-face.png" : "rock-face.png");
   }
   public TextureRegion stair(Tile tile, BiomeProfiles.Profile profile) {
      return region("stair-stone.png");
   }
   public TextureRegion earth() { return region("earth-face.png"); }
   public TextureRegion lava() { return required("lava-surface.png"); }
   public TextureRegion water() { return required("water-surface.png"); }
   private TextureRegion required(String name) {
      TextureRegion art=region(name);
      if(art==null)throw new IllegalStateException("Missing continuous fluid material: visual/terrain-relief/"+name);
      return art;
   }
   public static boolean earth(Tile tile,BiomeProfiles.Profile profile) {
      if (tile == null || profile == null) return false;
      return WorldReadability.smallJumpLedge(tile) || profile.tags().contains("woodland")
         || profile.id.equals("plains") || profile.id.equals("savanna") || profile.id.equals("wetland");
   }
   private TextureRegion region(String name) {
      if (regions.containsKey(name)) return regions.get(name);
      if (missing.contains(name)) return null;
      String path = "visual/terrain-relief/" + name;
      if (!Gdx.files.internal(path).exists()) { missing.add(name); return null; }
      Texture texture = new Texture(Gdx.files.internal(path));
      try {
         texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
         Texture.TextureWrap wrap = name.equals("stair-stone.png") ? Texture.TextureWrap.ClampToEdge : Texture.TextureWrap.Repeat;
         texture.setWrap(wrap, wrap);
         TextureRegion region = new TextureRegion(texture);
         textures.put(name, texture);
         regions.put(name, region);
         return region;
      } catch (RuntimeException error) { texture.dispose(); throw error; }
   }
   public static void disposeShared() {
      if (shared == null) return;
      for (Texture texture : shared.textures.values()) texture.dispose();
      shared.textures.clear(); shared.regions.clear(); shared.missing.clear();
      shared = null;
   }
}
