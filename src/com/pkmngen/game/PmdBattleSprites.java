package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.pkmngen.game.util.SpriteProxy;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** PMD actor presentation; original actions still own transforms and battle timing. */
public final class PmdBattleSprites {
   private static float seconds;
   private static long actorDraws, portraitDraws;
   private static final Color saved = new Color();
   private static final Map<String, Float> scales = new HashMap<>();
   private static final Map<Texture, Map<String, TextureRegion>> trainerRegions = new IdentityHashMap<>();
   private static final Map<Pokemon, Boolean> diagnosedGhosts = new WeakHashMap<>();
   private static ShaderProgram flashShader;

   public static void beginFrame() { seconds += Math.min(.1f, Gdx.graphics.getDeltaTime()); }
   public static long getActorDraws() { return actorDraws; }
   public static long getPortraitDraws() { return portraitDraws; }

   /** Called only for SpriteProxy.draw, including the original intro and attack actions. */
   public static boolean drawKnown(SpriteProxy source, Batch batch) {
      Game game = Game.staticGame;
      if (game == null || game.modernUi == null || batch != game.uiBatch || game.battle == null) return false;
      if (batch instanceof ModernBatch && ((ModernBatch)batch).suppressed) return true;
      Pokemon own = game.player == null ? null : game.player.currPokemon;
      Pokemon enemy = game.battle.oppPokemon;
      boolean trainerActor=game.player != null && source == game.player.battleSprite;
      boolean friendlyActor=own != null && source == own.backSprite;
      boolean enemyActor=enemy != null && (source == enemy.sprite || enemy.introAnim != null && enemy.introAnim.contains(source));
      if(!trainerActor&&!friendlyActor&&!enemyActor)return false;
      boolean clipped=game.johtoBattleRenderer!=null&&game.johtoBattleRenderer.beginActorClip(batch);
      try {
      if (game.player != null && source == game.player.battleSprite) {
         if(source.getX()>=160||source.getX()+source.getWidth()<=0)return true;
         String direction=game.johtoBattleRenderer==null?"up":game.johtoBattleRenderer.actorFacing(game,true);
         TextureRegion trainer = BwAssets.get().trainer(game.player,direction,seconds,false);
         if (trainer == null) return false;
         trainer = trimTrainer(trainer);
         Sprite draw = new Sprite(trainer);
         float groundY=source.getY()-(game.battle.drawAction instanceof SpecialBattleMegaGengar.DrawBattle1?13:0);
         Vector3 anchor=game.johtoBattleRenderer==null?new Vector3(source.getX()+source.getWidth()*.5f,groundY,1):
            game.johtoBattleRenderer.projectActor(game,source.getX()+source.getWidth()*.5f,groundY,new Vector3());
         float scale=Math.min(source.getWidth()/trainer.getRegionWidth(),30f/trainer.getRegionHeight())*anchor.z;
         float width=trainer.getRegionWidth()*scale,height=trainer.getRegionHeight()*scale;
         draw.setBounds(anchor.x-width*.5f,anchor.y,width,height);
         draw.setColor(source.getColor());draw.setOrigin(width*.5f,0);draw.setScale(source.getScaleX(),source.getScaleY());
         draw.setRotation(source.getRotation());
         try(ActorDepth depth=new ActorDepth(game,anchor.y,false)){draw.draw(game.uiBatch);}
         return true;
      }
      if (own != null && source == own.backSprite) return draw(game, own, source, true, animation(game, own));
      if (enemy != null && (source == enemy.sprite || enemy.introAnim != null && enemy.introAnim.contains(source)))
         return draw(game, enemy, source, false, animation(game, enemy));
      return false;
      } finally {
         if(clipped)game.johtoBattleRenderer.endActorClip(batch);
      }
   }

   /** The B/W avatar cell contains transparent padding; fit its complete visible body. */
   private static TextureRegion trimTrainer(TextureRegion source) {
      String key=source.getRegionX()+":"+source.getRegionY()+":"+source.getRegionWidth()+":"+source.getRegionHeight();
      Map<String,TextureRegion> byRegion=trainerRegions.get(source.getTexture());
      TextureRegion cached=byRegion==null?null:byRegion.get(key);
      if(cached!=null)return cached;
      TextureData data=source.getTexture().getTextureData();
      if(data.getType()!=TextureData.TextureDataType.Pixmap)return source;
      if(!data.isPrepared())data.prepare();
      Pixmap atlas=data.consumePixmap();
      // Composed/normalized trainers upload a 32x32 frame and release their
      // CPU Pixmap immediately. Preserve that complete frame; reading its
      // released native pixels is unsafe. File-backed custom art still trims.
      if(atlas==null||atlas.isDisposed())return source;
      int left=source.getRegionWidth(),top=source.getRegionHeight(),right=-1,bottom=-1;
      for(int y=0;y<source.getRegionHeight();y++)for(int x=0;x<source.getRegionWidth();x++){
         if((atlas.getPixel(source.getRegionX()+x,source.getRegionY()+y)&255)==0)continue;
         left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
      }
      if(data.disposePixmap())atlas.dispose();
      cached=right<left?source:new TextureRegion(source,left,top,right-left+1,bottom-top+1);
      if(byRegion==null){byRegion=new HashMap<>();trainerRegions.put(source.getTexture(),byRegion);}
      byRegion.put(key,cached);return cached;
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
         if (diagnosedGhosts.put(pokemon, Boolean.TRUE) == null)
            Gdx.app.log("GhostPresentation", "entity=Pokemon species=" + pokemon.specie.name
               + " isGhost=true requested=hidden-identity asset=lavender-spirit fallback=false"
               + " time=" + (game.map == null ? "none" : game.map.timeOfDay)
               + " shader=ui-default layer=battle-actor");
         // Concealed encounters share the explicit mystery sprite with the world;
         // the species frame is never requested before identification.
         float ratio=source.getWidth()/Math.max(1,pokemon.specie.sprite.getWidth());
         float cx=source.getX()+source.getWidth()*.5f,ground=source.getY();
         Vector3 anchor=game.johtoBattleRenderer==null?new Vector3(cx,ground,1):
            game.johtoBattleRenderer.projectActor(game,cx,ground,new Vector3());
         float ghostTime=ModernUi.get(game).reducedMotion()?0:seconds;
         TextureRegion spirit=GhostVisualAssets.get().frame(ghostTime);
         Sprite ghost=new Sprite(spirit);float scale=27*ratio*anchor.z/Math.max(spirit.getRegionWidth(),spirit.getRegionHeight());
         float width=spirit.getRegionWidth()*scale,height=spirit.getRegionHeight()*scale;
         ghost.setBounds(anchor.x-width*.5f,anchor.y,width,height);
         ghost.setColor(source.getColor());ghost.setOrigin(width*.5f,0);
         ghost.setScale(source.getScaleX(),source.getScaleY());ghost.setRotation(source.getRotation());
         try(ActorDepth depth=new ActorDepth(game,anchor.y,false)){ghost.draw(game.uiBatch);}
         return true;
      }
      // The arena slots are diagonal. North/south frames made both actors look
      // past each other. Use real NE/SW art for idle, attacks, hurt and sleep.
      String facing = game.johtoBattleRenderer==null?VisualGeometry.battleFacing(back):game.johtoBattleRenderer.actorFacing(game,back);
      PmdPokemonSprites.Frame frame = PmdPokemonSprites.get().frame(pokemon, facing, animation, seconds);
      if (frame == null) return drawFallback(game,pokemon,source,back);
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
         for(String orientation:new String[]{"up","up-right","right","down-right","down","down-left","left","up-left"})
         for (String name : new String[]{"Idle","Walk","Attack","Hurt","Sleep"}) {
            PmdPokemonSprites.AnimationBounds bounds=PmdPokemonSprites.get().bounds(pokemon,orientation,name);
            if (bounds == null) continue;
            minX=Math.min(minX,bounds.minX); minY=Math.min(minY,bounds.minY);
            maxX=Math.max(maxX,bounds.maxX); maxY=Math.max(maxY,bounds.maxY);
         }
         baseScale = Math.min(1.5f, Math.min(56f/Math.max(1,maxX-minX),46f/Math.max(1,maxY-minY)));
         // Leave room below the PMD ground anchor for feet/shadows without
         // intersecting the command panel, and above it for the entire pose.
         baseScale=Math.min(baseScale,Math.min(44f/Math.max(1,maxY),6f/Math.max(1,-minY)));
         baseScale=Math.min(baseScale,33f/Math.max(1,Math.max(-minX,maxX)));
         // Keep one size for every direction. Fit even the widest pose against
         // the complete orbit, rather than cropping large species at its edge.
         for(int step=0;step<=128;step++) {
            float yaw=step*MathUtils.PI2/128f;
            Vector3 limit=BattleScenery.project(back?40:124,back?48:88,yaw,new Vector3());
            float ratio=limit.z*(back?1f:.9f);
            baseScale=Math.min(baseScale,(limit.x-2)/(Math.max(1,-minX)*ratio));
            baseScale=Math.min(baseScale,(158-limit.x)/(Math.max(1,maxX)*ratio));
            baseScale=Math.min(baseScale,(121-limit.y)/(Math.max(1,maxY-minY)*ratio));
         }
         baseScale=Math.max(.05f,baseScale);
         scales.put(key, baseScale);
      }
      float scale = baseScale * source.getWidth() / (back ? 48f : pokemon.specie.sprite.getWidth()) * (back ? 1f : .90f);
      float baseGround=source.getY()-(back&&game.battle.drawAction instanceof SpecialBattleMegaGengar.DrawBattle1?14:0);
      Vector3 camera=game.johtoBattleRenderer==null?new Vector3(source.getX()+source.getWidth()*.5f,baseGround,1):
         game.johtoBattleRenderer.projectActor(game,source.getX()+source.getWidth()*.5f,baseGround,new Vector3());
      scale*=camera.z;
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
      float groundY=camera.y;
      PmdPokemonSprites.AnimationBounds contact=PmdPokemonSprites.get().bounds(pokemon,facing,"Idle");
      float contactLift=contact==null?0:-Math.min(0,contact.minY)*scale;
      if(fraction>=.99f&&source.getColor().a>0) {
         ModernUi ui=ModernUi.get(game);
         float radius=Math.min(back?13:10,Math.max(4,width*.32f));
         Color shade=new Color(.08f,.12f,.13f,.22f*source.getColor().a);
         try(ActorDepth depth=new ActorDepth(game,groundY+.8f,false)){
         for(int row=0;row<5;row++){
            float dy=(row-2)/2.5f,half=radius*(float)Math.sqrt(Math.max(0,1-dy*dy));
            ui.rect(game,camera.x-half,groundY-.7f+row*.4f,half*2,.4f,shade);
         }}
      }
      draw.setBounds(camera.x - frame.anchorX*scale,
         groundY + contactLift - frame.anchorY*scale*fraction, width, height);
      // Move effects retain their original rotation around the PMD ground anchor.
      draw.setOrigin(frame.anchorX*scale,frame.anchorY*scale*fraction); draw.setRotation(source.getRotation());
      draw.setScale(source.getScaleX(), source.getScaleY());
      Color tint = new Color(source.getColor());
      if (SpriteProxy.darkenAllColors1 || SpriteProxy.darkenAllColors2 || SpriteProxy.darkenAllColors3
         || source instanceof SpriteProxy && (((SpriteProxy)source).darkenColors1 || ((SpriteProxy)source).darkenColors2 || ((SpriteProxy)source).darkenColors3))
         tint.mul(.65f,.65f,.75f,1);
      if (SpriteProxy.inverseColors || SpriteProxy.confuseRayColors1 || SpriteProxy.confuseRayColors2) tint.mul(.75f,.6f,1f,1f);
      boolean flash = SpriteProxy.lightenAllColors1 || SpriteProxy.lightenAllColors2
         || source instanceof SpriteProxy && (((SpriteProxy)source).lightenColors1 || ((SpriteProxy)source).lightenColors2);
      draw.setColor(tint);
      try(ActorDepth depth=new ActorDepth(game,groundY,flash)){
         draw.draw(game.uiBatch);
         if(fraction>=.99f&&source.getColor().a>0)
            ShinyVisuals.drawBattle(game,pokemon,camera.x,groundY,width,height,seconds);
      }
      actorDraws++;
      return true;
   }

   /** Preserve the genuine legacy shiny art if PMD has no exact shiny sheet. */
   private static boolean drawFallback(Game game,Pokemon pokemon,Sprite source,boolean back){
      float baseGround=source.getY()-(back&&game.battle.drawAction instanceof SpecialBattleMegaGengar.DrawBattle1?14:0);
      Vector3 anchor=game.johtoBattleRenderer==null?new Vector3(source.getX()+source.getWidth()*.5f,baseGround,1):
         game.johtoBattleRenderer.projectActor(game,source.getX()+source.getWidth()*.5f,baseGround,new Vector3());
      float factor=.62f*anchor.z,width=source.getWidth()*factor,height=source.getHeight()*factor;
      Sprite fallback=new Sprite(source);fallback.setBounds(anchor.x-width*.5f,anchor.y,width,height);
      fallback.setOrigin(width*.5f,0);
      try(ActorDepth depth=new ActorDepth(game,anchor.y,false)){
         fallback.draw(game.uiBatch);
         float original=back?48:pokemon.specie.sprite.getWidth();
         if(source.getHeight()>=original*.99f&&source.getColor().a>0)
            ShinyVisuals.drawBattle(game,pokemon,anchor.x,anchor.y,width,height,seconds);
      }
      actorDraws++;return true;
   }

   /** Recognized actors already project themselves; plain poofs share the effect camera. */
   public static void drawSendOutFragment(Game game,Sprite fragment){
      if(fragment instanceof SpriteProxy){fragment.draw(game.uiBatch);return;}
      if(game.johtoBattleRenderer==null||!game.johtoBattleRenderer.drawSceneEffect(fragment,game.uiBatch))fragment.draw(game.uiBatch);
   }

   /** Pixel-art billboards sort by their ground depth, even across legacy GUI layers.
    * Transparent texels never occlude a nearer actor; all caller GL state is restored. */
   private static final class ActorDepth implements AutoCloseable {
      final Game game;final ShaderProgram previous;final boolean enabled,mask;final int function;
      ActorDepth(Game game,float groundY,boolean flash){
         this.game=game;game.uiBatch.flush();previous=game.uiBatch.getShader();
         enabled=Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
         java.nio.IntBuffer value=BufferUtils.newIntBuffer(1);Gdx.gl.glGetIntegerv(GL20.GL_DEPTH_WRITEMASK,value);mask=value.get(0)!=0;
         value.clear();Gdx.gl.glGetIntegerv(GL20.GL_DEPTH_FUNC,value);function=value.get(0);
         if(flashShader==null){
            flashShader=new ShaderProgram(
               "attribute vec4 a_position;attribute vec4 a_color;attribute vec2 a_texCoord0;uniform mat4 u_projTrans;uniform float u_depth;varying vec4 v_color;varying vec2 v_uv;void main(){v_color=a_color;v_color.a*=255.0/254.0;v_uv=a_texCoord0;gl_Position=u_projTrans*a_position;gl_Position.z=(u_depth*2.0-1.0)*gl_Position.w;}",
               "#ifdef GL_ES\nprecision mediump float;\n#endif\nuniform sampler2D u_texture;uniform float u_flash;varying vec4 v_color;varying vec2 v_uv;void main(){vec4 c=texture2D(u_texture,v_uv)*v_color;if(c.a<0.01)discard;c.rgb=mix(c.rgb,vec3(1.0),u_flash);gl_FragColor=c;}");
            if(!flashShader.isCompiled())throw new IllegalStateException(flashShader.getLog());
         }
         Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);Gdx.gl.glDepthMask(true);
         game.uiBatch.setShader(flashShader);flashShader.setUniformf("u_depth",MathUtils.clamp(.15f+groundY/250f,.02f,.98f));
         flashShader.setUniformf("u_flash",flash?.65f:0);
      }
      @Override public void close(){game.uiBatch.flush();game.uiBatch.setShader(previous);Gdx.gl.glDepthMask(mask);Gdx.gl.glDepthFunc(function);if(!enabled)Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);}
   }

   public static boolean sendOut(Game game, float x, float y, int remaining) {
      if (game.modernUi == null || game.player.currPokemon == null) return false;
      Pokemon pokemon = game.player.currPokemon;
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

   public static void dispose() { if (flashShader != null) flashShader.dispose(); flashShader=null; scales.clear(); trainerRegions.clear(); diagnosedGhosts.clear(); }
}
