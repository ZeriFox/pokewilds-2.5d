package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.pkmngen.game.util.SpriteProxy;
import java.util.HashMap;
import java.util.Map;

/** PMD actor presentation; original actions still own transforms and battle timing. */
public final class PmdBattleSprites {
   private static float seconds;
   private static long actorDraws, portraitDraws;
   private static final Color saved = new Color();
   private static final Map<String, Float> scales = new HashMap<>();
   private static ShaderProgram flashShader;

   public static void beginFrame() { seconds += Math.min(.1f, Gdx.graphics.getDeltaTime()); }
   public static long getActorDraws() { return actorDraws; }
   public static long getPortraitDraws() { return portraitDraws; }

   /** Called only for SpriteProxy.draw, including the original intro and attack actions. */
   public static boolean drawKnown(SpriteProxy source, Batch batch) {
      Game game = Game.staticGame;
      if (game == null || game.modernUi == null || batch != game.uiBatch || game.battle == null) return false;
      if (batch instanceof ModernBatch && ((ModernBatch)batch).suppressed) return true;
      if (game.player != null && source == game.player.battleSprite) {
         TextureRegion trainer = BwAssets.get().trainer(game.player.character,"up",seconds,false);
         if (trainer == null) return false;
         Sprite draw = new Sprite(trainer);
         draw.setBounds(source.getX(), source.getY(), source.getWidth(), source.getHeight());
         draw.setColor(source.getColor()); draw.setOriginCenter(); draw.setScale(source.getScaleX(),source.getScaleY());
         draw.setRotation(source.getRotation()); draw.draw(game.uiBatch);
         return true;
      }
      Pokemon own = game.player == null ? null : game.player.currPokemon;
      Pokemon enemy = game.battle.oppPokemon;
      if (own != null && source == own.backSprite) return draw(game, own, source, true, animation(game, own));
      if (enemy != null && (source == enemy.sprite || enemy.introAnim != null && enemy.introAnim.contains(source)))
         return draw(game, enemy, source, false, animation(game, enemy));
      return false;
   }

   private static String animation(Game game, Pokemon pokemon) {
      if ("sleep".equals(pokemon.status)) return "Sleep";
      for (Action action : game.actionStack) {
         String name = action.getClass().getSimpleName();
         if (name.equals("DepleteFriendlyHealth") && pokemon == game.player.currPokemon
            || name.equals("DepleteEnemyHealth") && pokemon == game.battle.oppPokemon) return "Hurt";
         if (action instanceof Battle.LoadAndPlayAnimation) {
            Battle.LoadAndPlayAnimation effect = (Battle.LoadAndPlayAnimation)action;
            String move = effect.name.replace("_player_gsc", "").replace("_enemy_gsc", "").replace('_',' ');
            if (effect.target != pokemon && Pokemon.attacksImplemented.contains(move)) return "Attack";
         }
      }
      return "Idle";
   }

   public static boolean portrait(Game game, Pokemon pokemon, float x, float y, float w, float h) {
      if (pokemon == null || pokemon.isEgg || pokemon.isGhost) return false;
      TextureRegion region = PmdPokemonSprites.get().portrait(pokemon);
      if (region == null) return false;
      float scale = Math.min(w / region.getRegionWidth(), h / region.getRegionHeight());
      float width = region.getRegionWidth() * scale, height = region.getRegionHeight() * scale;
      saved.set(game.uiBatch.getColor()); game.uiBatch.setColor(Color.WHITE);
      game.uiBatch.draw(region, x + (w-width)*.5f, y + (h-height)*.5f, width, height);
      game.uiBatch.setColor(saved); portraitDraws++;
      return true;
   }

   public static boolean draw(Game game, Pokemon pokemon, Sprite source, boolean back, String animation) {
      if (game.modernUi == null || pokemon == null || pokemon.isEgg || pokemon.isGhost || source == null) return false;
      // The arena slots are diagonal. North/south frames made both actors look
      // past each other. Use real NE/SW art for idle, attacks, hurt and sleep.
      String facing = VisualGeometry.battleFacing(back);
      PmdPokemonSprites.Frame frame = PmdPokemonSprites.get().frame(pokemon, facing, animation, seconds);
      if (frame == null) return false;
      float originalHeight = back ? 48f : pokemon.specie.sprite.getWidth();
      float widthRatio = source.getWidth() / (back ? 48f : pokemon.specie.sprite.getWidth());
      float fraction = Math.max(0, Math.min(1, source.getHeight() / Math.max(1, originalHeight * widthRatio)));
      if (fraction == 0) return true;
      String key = pokemon.specie.name + pokemon.isShiny + back;
      Float baseScale = scales.get(key);
      if (baseScale == null) {
         PmdPokemonSprites.Frame idle = PmdPokemonSprites.get().frame(pokemon, facing, "Idle", 0);
         if (idle == null) idle = frame;
         baseScale = Math.min(1.5f, Math.min(48f / Math.max(1,idle.width), 44f / Math.max(1,idle.height)));
         scales.put(key, baseScale);
      }
      float scale = baseScale * source.getWidth() / (back ? 48f : pokemon.specie.sprite.getWidth());
      TextureRegion visible = frame.region;
      if (fraction < 1f) {
         // The original faint action removes rows from the bottom of its image.
         // Keep the PMD pixels at their native aspect ratio while the remaining
         // upper part sinks to the ground; shrinking the full image squashes it.
         int rows = Math.max(1, Math.round(frame.height * fraction));
         visible = new TextureRegion(frame.region, 0, 0, frame.region.getRegionWidth(), rows);
      }
      Sprite draw = new Sprite(visible);
      float width = frame.width * scale, height = visible.getRegionHeight() * scale;
      draw.setBounds(source.getX() + source.getWidth()*.5f - frame.anchorX*scale,
         source.getY() - frame.anchorY*scale*fraction, width, height);
      // Preserve rotations belonging to move effects, not as a facing correction.
      draw.setOriginCenter(); draw.setRotation(source.getRotation());
      draw.setScale(source.getScaleX(), source.getScaleY());
      Color tint = new Color(source.getColor());
      if (SpriteProxy.darkenAllColors1 || SpriteProxy.darkenAllColors2 || SpriteProxy.darkenAllColors3
         || source instanceof SpriteProxy && (((SpriteProxy)source).darkenColors1 || ((SpriteProxy)source).darkenColors2 || ((SpriteProxy)source).darkenColors3))
         tint.mul(.65f,.65f,.75f,1);
      if (SpriteProxy.inverseColors || SpriteProxy.confuseRayColors1 || SpriteProxy.confuseRayColors2) tint.mul(.75f,.6f,1f,1f);
      boolean flash = SpriteProxy.lightenAllColors1 || SpriteProxy.lightenAllColors2
         || source instanceof SpriteProxy && (((SpriteProxy)source).lightenColors1 || ((SpriteProxy)source).lightenColors2);
      ShaderProgram previous = game.uiBatch.getShader();
      if (flash) {
         if (flashShader == null) {
            flashShader = new ShaderProgram(
               "attribute vec4 a_position; attribute vec4 a_color; attribute vec2 a_texCoord0; uniform mat4 u_projTrans; varying vec4 v_color; varying vec2 v_uv; void main(){v_color=a_color;v_color.a*=255.0/254.0;v_uv=a_texCoord0;gl_Position=u_projTrans*a_position;}",
               "#ifdef GL_ES\nprecision mediump float;\n#endif\nuniform sampler2D u_texture; varying vec4 v_color; varying vec2 v_uv; void main(){vec4 c=texture2D(u_texture,v_uv);c.rgb=mix(c.rgb,vec3(1.0),0.65);gl_FragColor=c*v_color;}");
            if (!flashShader.isCompiled()) throw new IllegalStateException(flashShader.getLog());
         }
         game.uiBatch.setShader(flashShader);
      }
      draw.setColor(tint); draw.draw(game.uiBatch);
      if (flash) game.uiBatch.setShader(previous);
      actorDraws++;
      return true;
   }

   public static boolean sendOut(Game game, float x, float y, int remaining) {
      if (game.modernUi == null || game.player.currPokemon == null) return false;
      Pokemon pokemon = game.player.currPokemon;
      if (PmdPokemonSprites.get().frame(pokemon,VisualGeometry.battleFacing(true),"Idle",seconds) == null) return false;
      // Keep the original ball/poof schedule, replacing only its sliced actor art.
      if (remaining <= 7) {
         Sprite proxy = new Sprite(pokemon.backSprite);
         float scale = Math.min(1f, (8-remaining)/4f);
         proxy.setBounds(x + 24*(1-scale), y, 48*scale, 48*scale);
         draw(game,pokemon,proxy,true,"Idle");
      }
      return true;
   }

   public static boolean event(Game game, String species, float x, float y, float width, float height) {
      if (game.modernUi == null) return false;
      PmdPokemonSprites.Frame frame = PmdPokemonSprites.get().frame(species,"down","Idle",seconds);
      if (frame == null) return false;
      PmdPokemonSprites.Frame idle = PmdPokemonSprites.get().frame(species,"down","Idle",0);
      if (idle == null) idle = frame;
      float scale = Math.min(width/Math.max(1,idle.width),height/Math.max(1,idle.height));
      game.uiBatch.draw(frame.region,x+width/2-frame.anchorX*scale,y-frame.anchorY*scale,frame.width*scale,frame.height*scale);
      actorDraws++; return true;
   }

   public static boolean event(Game game, Pokemon pokemon, String species, float x, float y, float width, float height) {
      return event(game, species + (pokemon.isShiny ? "#shiny" : ""), x,y,width,height);
   }

   public static void dispose() { if (flashShader != null) flashShader.dispose(); flashShader=null; scales.clear(); }
}
