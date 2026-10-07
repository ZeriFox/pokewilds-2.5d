package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
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
   private static final Map<String, TextureRegion> trainerRegions = new HashMap<>();
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
         trainer = trimTrainer(trainer);
         Sprite draw = new Sprite(trainer);
         float scale = Math.min(source.getWidth()/trainer.getRegionWidth(), source.getHeight()/trainer.getRegionHeight());
         float width=trainer.getRegionWidth()*scale, height=trainer.getRegionHeight()*scale;
         float groundY=source.getY()-(game.battle.drawAction instanceof SpecialBattleMegaGengar.DrawBattle1?13:0);
         draw.setBounds(source.getX()+(source.getWidth()-width)*.5f, groundY, width,height);
         draw.setColor(source.getColor()); draw.setOrigin(width*.5f,0); draw.setScale(source.getScaleX(),source.getScaleY());
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

   /** The B/W avatar cell contains transparent padding; fit its complete visible body. */
   private static TextureRegion trimTrainer(TextureRegion source) {
      String key=source.getRegionX()+":"+source.getRegionY();
      TextureRegion cached=trainerRegions.get(key);
      if(cached!=null)return cached;
      Pixmap atlas=new Pixmap(Gdx.files.internal("visual/unova/world-atlas.png"));
      int left=source.getRegionWidth(),top=source.getRegionHeight(),right=-1,bottom=-1;
      for(int y=0;y<source.getRegionHeight();y++)for(int x=0;x<source.getRegionWidth();x++){
         if((atlas.getPixel(source.getRegionX()+x,source.getRegionY()+y)&255)==0)continue;
         left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
      }
      atlas.dispose();
      cached=right<left?source:new TextureRegion(source,left,top,right-left+1,bottom-top+1);
      trainerRegions.put(key,cached);return cached;
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
      if (game.modernUi == null || pokemon == null || pokemon.isEgg || source == null) return false;
      if (pokemon.isGhost) {
         // The night encounter intentionally hides its species until Silph Scope.
         // Present it as the same luminous wisp used in the modern world, never
         // as a missing-art fallback or as an early reveal of the real Pokemon.
         ModernUi ui=ModernUi.get(game);float ratio=source.getWidth()/Math.max(1,pokemon.specie.sprite.getWidth());
         float cx=source.getX()+source.getWidth()*.5f,cy=source.getY()+17*ratio;
         for(int layer=0;layer<4;layer++) {
            float radius=(17-layer*2.5f)*ratio;
            Color color=new Color(.64f+layer*.07f,.71f+layer*.06f,.94f,source.getColor().a*(.15f+layer*.05f));
            for(float y=-radius;y<=radius;y+=Math.max(.5f,ratio)) {
               float half=(float)Math.sqrt(Math.max(0,radius*radius-y*y));
               ui.rect(game,cx-half,cy+y,half*2,Math.max(.5f,ratio),color);
            }
         }
         return true;
      }
      PmdPokemonSprites.Frame frame = PmdPokemonSprites.get().frame(pokemon, back ? "up" : "down", animation, seconds);
      if (frame == null) return false;
      float originalHeight = back ? 48f : pokemon.specie.sprite.getWidth();
      float widthRatio = source.getWidth() / (back ? 48f : pokemon.specie.sprite.getWidth());
      float fraction = Math.max(0, Math.min(1, source.getHeight() / Math.max(1, originalHeight * widthRatio)));
      if (fraction == 0) return true;
      String key = pokemon.specie.name + pokemon.isShiny + back;
      Float baseScale = scales.get(key);
      if (baseScale == null) {
         // Fit the complete animation envelope once; individual trimmed frames
         // may be larger or offset by jumps, tails and attack movement.
         float minX=0,minY=0,maxX=0,maxY=0;
         for (String name : new String[]{"Idle","Walk","Attack","Hurt","Sleep"}) {
            PmdPokemonSprites.AnimationBounds bounds=PmdPokemonSprites.get().bounds(pokemon,back?"up":"down",name);
            if (bounds == null) continue;
            minX=Math.min(minX,bounds.minX); minY=Math.min(minY,bounds.minY);
            maxX=Math.max(maxX,bounds.maxX); maxY=Math.max(maxY,bounds.maxY);
         }
         baseScale = Math.min(1.5f, Math.min(56f/Math.max(1,maxX-minX),46f/Math.max(1,maxY-minY)));
         // Leave room below the PMD ground anchor for feet/shadows without
         // intersecting the command panel, and above it for the entire pose.
         baseScale=Math.min(baseScale,Math.min(44f/Math.max(1,maxY),6f/Math.max(1,-minY)));
         baseScale=Math.min(baseScale,33f/Math.max(1,Math.max(-minX,maxX)));
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
      float groundY=source.getY()-(back&&game.battle.drawAction instanceof SpecialBattleMegaGengar.DrawBattle1?14:0);
      draw.setBounds(source.getX() + source.getWidth()*.5f - frame.anchorX*scale,
         groundY - frame.anchorY*scale*fraction, width, height);
      draw.setOrigin(frame.anchorX*scale,frame.anchorY*scale*fraction); draw.setRotation(source.getRotation());
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
      if (PmdPokemonSprites.get().frame(pokemon,"up","Idle",seconds) == null) return false;
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
      PmdPokemonSprites.AnimationBounds bounds=PmdPokemonSprites.get().bounds(species,"down","Idle");
      if (bounds == null) return false;
      float scale=Math.min(width/Math.max(1,bounds.width),height/Math.max(1,bounds.height));
      float originX=x+(width-bounds.width*scale)*.5f-bounds.minX*scale;
      float originY=y+(height-bounds.height*scale)*.5f-bounds.minY*scale;
      game.uiBatch.draw(frame.region,originX-frame.anchorX*scale,originY-frame.anchorY*scale,frame.width*scale,frame.height*scale);
      actorDraws++; return true;
   }

   public static boolean event(Game game, Pokemon pokemon, String species, float x, float y, float width, float height) {
      return event(game, species + (pokemon.isShiny ? "#shiny" : ""), x,y,width,height);
   }

   public static void dispose() { if (flashShader != null) flashShader.dispose(); flashShader=null; scales.clear(); trainerRegions.clear(); }
}
