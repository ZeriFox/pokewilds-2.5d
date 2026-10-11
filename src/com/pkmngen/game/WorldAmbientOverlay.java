package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/** Small world-space accents, never a filter over the UI or battle viewport.
 * Does not consume gameplay RNG, advance actions, load species or modify tiles.
 * The mesh owns its buffers; the white texture and camera remain borrowed.
 */
final class WorldAmbientOverlay {
   enum Effect { NONE, MIST, EMBER, FIREFLY, DUST, SNOW, GLINT }
   private final WorldMeshBatch mesh = new WorldMeshBatch();
   private final Vector2 lookup = new Vector2();
   private ShaderProgram shader;
   private Settings settings;
   private int particles, mistPatches;
   private float focusX, focusY;

   /** Source-bound effects: no embers on dry stone, or desert dust in a house. */
   static Effect effect(String biome, boolean night, boolean water, boolean lava, boolean solid) {
      if (biome.equals("interior") || biome.equals("cave")) return Effect.NONE;
      if (lava) return Effect.EMBER;
      if (water) return night ? Effect.NONE : Effect.GLINT;
      if (solid) return Effect.NONE;
      if (biome.equals("graveyard")) return Effect.MIST;
      if (biome.equals("snow")) return Effect.SNOW;
      if ((biome.equals("forest") || biome.equals("deep_forest") || biome.equals("wetland")) && night)
         return Effect.FIREFLY;
      if ((biome.equals("desert") || biome.equals("savanna")) && !night) return Effect.DUST;
      return Effect.NONE;
   }

   /** Coordinate hash rather than Game.rand/MathUtils.random. Negative cells work too. */
   static int seed(int x, int y) {
      int h = x * 0x1f123bb5 ^ y * 0x5f356495;
      h ^= h >>> 16; h *= 0x7feb352d; h ^= h >>> 15; h *= 0x846ca68b;
      return h ^ h >>> 16;
   }
   private static float unit(int bits) { return (bits & 0xffff) / 65535f; }
   private static float envelope(float phase) {
      float wave = MathUtils.sin(phase * MathUtils.PI);
      return wave * wave;
   }

   void prepare(Game game, WorldElevation elevation, float seconds, Texture white) {
      mesh.clear(); particles = mistPatches = 0;
      // The opt-out is also used by the historical pixel-parity test. It disables
      // only these new accents, not existing biome grading or gameplay effects.
      if ("off".equalsIgnoreCase(System.getProperty("pokewilds.ambientOverlays", ""))) {
         mesh.upload(); return;
      }
      if (settings == null) settings = Settings.load();
      if (!settings.enabled || settings.strength == 0 || game.map.tiles != game.map.overworldTiles) {
         mesh.upload(); return;
      }
      focusX = game.player.position.x + 8; focusY = game.player.position.y + 7;
      int cx = MathUtils.floor(game.cam.position.x / 32), cy = MathUtils.floor(game.cam.position.y / 32);
      float halfW = game.cam.viewportWidth * game.cam.zoom * .5f + 32;
      float halfH = game.cam.viewportHeight * game.cam.zoom * .5f + 32;
      int radius = Math.min(32, MathUtils.ceil(Math.max(halfW, halfH) / 32));
      boolean night = "night".equals(game.map.timeOfDay);
      // Closest cells first: a wide screen cannot spend the whole budget offscreen.
      for (int ring = 0; ring <= radius; ring++) {
         for (int dy = -ring; dy <= ring; dy++) for (int dx = -ring; dx <= ring; dx++) {
            if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) continue;
            float x = (cx + dx) * 32f + 16, y = (cy + dy) * 32f + 16;
            if (Math.abs(x-game.cam.position.x) > halfW || Math.abs(y-game.cam.position.y) > halfH) continue;
            Tile source = tile(game, x, y);
            if (source == null) continue;
            String biome = BiomeProfiles.visualForTile(source).id;
            Effect type = effect(biome, night, source.isWater, source.isLava, source.isSolid);
            if (type == Effect.NONE) continue;
            int h = seed(cx+dx, cy+dy);
            // Sparse deterministic eligibility. Density is bounded even at extreme zoom.
            if ((h & 3) != 0 || (type == Effect.MIST ? mistPatches >= settings.maxMist : particles >= settings.maxParticles)) continue;
            float phase = (Math.max(0, seconds) / (type == Effect.MIST ? 18f : 7f) + unit(h >>> 8)) % 1f;
            float pulse = envelope(phase);
            x += (unit(h)-.5f)*12 + MathUtils.sin(seconds*.23f + unit(h>>>8)*6.28f)*3;
            y += (unit(h>>>12)-.5f)*12;
            Tile at = tile(game,x,y);
            if (at == null || effect(BiomeProfiles.visualForTile(at).id, night, at.isWater, at.isLava, at.isSolid) != type) continue;
            if ((x-focusX)*(x-focusX)+(y-focusY)*(y-focusY) < 18*18) continue;
            float height = elevation.height(x,y);
            WorldMeshBatch.Buffer buffer;
            if (type == Effect.MIST) {
               // Only low, level, same-biome patches. Never stretch a rectangle
               // across a cliff, tomb, path opening or indoor boundary.
               float w = 24 + unit(h>>>4)*10, d = 8;
               if (!mistFootprint(game,elevation,x,y,w,d,height)) continue;
               buffer = mesh.buffer(white);
               float color = Color.toFloatBits(.70f,.78f,.72f,settings.strength*.16f*pulse);
               patch(buffer,x,y,height+.5f,w,d,color); mistPatches++;
               continue;
            }
            float size, opacity, lift, r, g, b;
            switch(type) {
               case EMBER: size=1.5f; opacity=.55f; lift=2+phase*11; r=1; g=.57f; b=.20f; break;
               case FIREFLY: size=1.7f; opacity=.55f; lift=5+unit(h>>>5)*8+MathUtils.sin(seconds*.6f+unit(h)*6.28f); r=.83f; g=.94f; b=.43f; break;
               case DUST: size=1.1f; opacity=.24f; lift=2+phase*3; r=.84f; g=.72f; b=.51f; break;
               case SNOW: size=1.3f; opacity=.38f; lift=2+(1-phase)*14; r=.84f; g=.92f; b=1; break;
               default: size=1.6f; opacity=.30f; lift=-BiomeProfiles.forTile(at).number("height","shoreDepth",2)+.16f; r=.68f; g=.90f; b=1; break;
            }
            buffer = mesh.buffer(white);
            float color = Color.toFloatBits(r,g,b,settings.strength*opacity*pulse);
            if (type == Effect.GLINT) patch(buffer,x,y,height+lift,size,size*.7f,color);
            else billboard(buffer,x,y,height+lift,size,color,type==Effect.EMBER||type==Effect.FIREFLY);
            particles++;
         }
         if (particles >= settings.maxParticles && mistPatches >= settings.maxMist) break;
      }
      mesh.upload();
   }
   private Tile tile(Game game,float x,float y) {
      return game.map.tiles.get(lookup.set(MathUtils.floor(x/16)*16,MathUtils.floor(y/16)*16));
   }
   private boolean mistFootprint(Game game,WorldElevation elevation,float x,float y,float w,float d,float height) {
      for (int iy=-1; iy<=1; iy++) for (int ix=-1; ix<=1; ix++) {
         float xx=x+ix*w*.5f, yy=y+iy*d*.5f;
         Tile t=tile(game,xx,yy);
         if(t==null || t.isSolid || t.isWater || t.isLava || !BiomeProfiles.visualForTile(t).id.equals("graveyard")
            || Math.abs(elevation.height(xx,yy)-height)>.25f) return false;
      }
      return true;
   }
   private static void billboard(WorldMeshBatch.Buffer b,float x,float y,float h,float size,float color,boolean emissive) {
      float rise=size*MathUtils.cosDeg(50), back=size*MathUtils.sinDeg(50);
      quad(b,x-size/2,h,-y,x+size/2,h,-y,x+size/2,h+rise,-y-back,x-size/2,h+rise,-y-back,color,emissive?1:0);
   }
   private static void patch(WorldMeshBatch.Buffer b,float x,float y,float h,float w,float d,float color) {
      quad(b,x-w/2,h,-y+d/2,x+w/2,h,-y+d/2,x+w/2,h,-y-d/2,x-w/2,h,-y-d/2,color,0);
   }
   private static void quad(WorldMeshBatch.Buffer b,float x0,float y0,float z0,float x1,float y1,float z1,
      float x2,float y2,float z2,float x3,float y3,float z3,float color,float emissive) {
      b.vertex(x0,y0,z0,color,0,1,emissive,0);b.vertex(x1,y1,z1,color,1,1,emissive,0);b.vertex(x2,y2,z2,color,1,0,emissive,0);
      b.vertex(x2,y2,z2,color,1,0,emissive,0);b.vertex(x3,y3,z3,color,0,0,emissive,0);b.vertex(x0,y0,z0,color,0,1,emissive,0);
   }

   /** Caller ends its world shader before this and restores it for water/UI. */
   void render(PerspectiveCamera camera,float r,float g,float b) {
      if(particles+mistPatches==0)return;
      if(shader==null) {
         shader=new ShaderProgram(
            "attribute vec3 a_position;attribute vec4 a_color;attribute vec2 a_texCoord0;attribute float a_style;"
            +"uniform mat4 u_projTrans;varying vec4 v_color;varying vec2 v_uv;varying vec2 v_world;varying float v_emission;"
            +"void main(){v_color=a_color;v_color.a*=255.0/254.0;v_uv=a_texCoord0;v_world=a_position.xz;v_emission=a_style;"
            +"gl_Position=u_projTrans*vec4(a_position,1.0);}",
            "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            +"varying vec4 v_color;varying vec2 v_uv;varying vec2 v_world;varying float v_emission;uniform vec3 u_light;uniform vec2 u_focus;"
            +"void main(){vec2 p=v_uv*2.0-1.0;float edge=max(0.0,1.0-dot(p,p));"
            +"float focus=smoothstep(12.0,28.0,distance(v_world,u_focus));"
            +"float alpha=v_color.a*edge*edge*focus;if(alpha<.002)discard;"
            +"gl_FragColor=vec4(v_color.rgb*mix(u_light,vec3(1.0),v_emission),alpha);}");
         if(!shader.isCompiled())throw new IllegalStateException("Ambient shader compilation failed: "+shader.getLog());
      }
      Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);Gdx.gl.glDepthMask(false);
      Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA);
      shader.begin();
      try {
         shader.setUniformMatrix("u_projTrans",camera.combined);
         // A faint accent can remain visible at night, but must not whiten it.
         shader.setUniformf("u_light",Math.max(.32f,r),Math.max(.32f,g),Math.max(.32f,b));
         shader.setUniformf("u_focus",focusX,-focusY);
         mesh.render(shader);
      } finally {shader.end();Gdx.gl.glDepthMask(true);Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);}
   }
   int particleCount(){return particles;}
   int mistCount(){return mistPatches;}
   void dispose(){mesh.dispose();if(shader!=null)shader.dispose();shader=null;settings=null;particles=mistPatches=0;}

   private static final class Settings {
      boolean enabled;float strength;int maxParticles,maxMist;
      static Settings load() {
         JsonValue data=new JsonReader().parse(Gdx.files.internal("visual/biomes/ambient-overlays.json"));
         if(data.getInt("version",0)!=1)throw new IllegalArgumentException("Unknown ambient overlay version");
         Settings s=new Settings();s.enabled=data.getBoolean("enabled",true);s.strength=data.getFloat("strength",.65f);
         s.maxParticles=data.getInt("maxParticles",24);s.maxMist=data.getInt("maxMistPatches",6);
         if(!Float.isFinite(s.strength)||s.strength<0||s.strength>1||s.maxParticles<0||s.maxParticles>48||s.maxMist<0||s.maxMist>8)
            throw new IllegalArgumentException("Ambient settings exceed readability budgets");
         return s;
      }
   }
}
