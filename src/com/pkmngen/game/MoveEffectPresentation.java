package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.IntBuffer;

/** Johto-only raster presentation for the original move frames and metadata.
 * No battle rules, action scheduling, random numbers, or animation clocks live here.
 * PNG canvas dimensions cannot identify scope: even Tackle is authored at 160x144.
 */
public final class MoveEffectPresentation {
   public enum Scope { LOCAL, SCREEN }
   private final Scope scope;
   private final String animationName;
   private final ModernMoveEffects modern=new ModernMoveEffects();
   private final int duration;
   private int modernFrames;
   private FrameBuffer snapshot;
   private ShaderProgram colors;
   private int captureCount, rowFrames, screenFrames, localFrames, colorFrames, shrinkFrames;
   private final float[] orbitVertices=new float[20*20];
   private final com.badlogic.gdx.math.Vector3 projected=new com.badlogic.gdx.math.Vector3();

   public MoveEffectPresentation(String animationName) { this.animationName=animationName;scope=scopeFor(animationName);duration=ModernMoveEffects.family(animationName)>0?ModernMoveEffects.duration(animationName):1; }

   /** Explicit semantic classification, independent of transparent pixel bounds. */
   public static Scope scopeFor(String name) {
      String move=name.replace("_player_gsc", "").replace("_enemy_gsc", "").replace("_gsc", "");
      switch(move) {
         case "surf": case "whirlpool": case "rain_dance": case "sunny_day":
         case "sandstorm": case "hail": case "blizzard": case "mist": case "haze":
            return Scope.SCREEN;
         default: return Scope.LOCAL;
      }
   }

   private float width() { return Math.max(160f,144f*Gdx.graphics.getBackBufferWidth()/Math.max(1,Gdx.graphics.getBackBufferHeight())); }
   private float height() { return Math.max(144f,160f*Gdx.graphics.getBackBufferHeight()/Math.max(1,Gdx.graphics.getBackBufferWidth())); }
   private float left() { return (160-width())*.5f; }
   private float bottom() { return (144-height())*.5f; }

   /** Consume only raster metadata; the original action continues all other commands. */
   boolean drawMetadata(Game game,Battle.LoadAndPlayAnimation action,String properties) {
      boolean rows=properties.contains("row_copy:")||properties.contains("row_displace:")||properties.contains("row_split:");
      boolean recolor=has(properties,"inverse_colors")||has(properties,"darken_effect1")
         ||has(properties,"darken_effect2")||has(properties,"darken_effect3")
         ||has(properties,"lighten_effect1")||has(properties,"lighten_effect2");
      Pokemon visualTarget=properties.contains("displace_target:player")?game.player.currPokemon:
         properties.contains("displace_target:enemy")?game.battle.oppPokemon:action.target;
      if(!rows&&!recolor){if(properties.contains("screenshot:"))capture(game,visualTarget);return true;}
      // Legacy rows/inversions were authored for the flat monochrome field. On
      // the modern arena their visual meaning is an actor pulse/afterimage; the
      // environment is never copied, split, displaced or color-inverted.
      capture(game,visualTarget);
      if(rows){
         rowFrames++;
         if(properties.contains("row_displace:")){
            double speed=Double.parseDouble(value(properties,"row_displace:"));
            if(action.frameNum>=action.dispLastFrame+1.0/speed){action.dispLastFrame=action.frameNum;action.dispProgress++;}
         }
      }
      if(recolor)colorFrames++;
      SpriteBatch batch=game.uiBatch;ShaderProgram incoming=batch.getShader();Color color=new Color(batch.getColor());
      Clip clip=new Clip(batch,bottom(),height());
      try{
         batch.setColor(Color.WHITE);ensureShader();batch.setShader(colors);
         colors.setUniformf("u_tint",recolor?.65f:.25f);
         colors.setUniformf("u_alpha",recolor?.34f:.23f);
         float shake=rows&&!ModernUi.get(game).reducedMotion()?(float)Math.sin(action.frameNum*.55)*1.2f:0;
         Texture actor=snapshot.getColorBufferTexture();
         batch.draw(actor,left()+shake,bottom(),width(),height(),0,0,1,1);
      }finally{batch.flush();batch.setShader(incoming);batch.setColor(color);clip.close(batch);}
      return true;
   }

   /** Dig's two authored shrink stages retain the current PMD actor and foot anchor. */
   boolean drawShrink(Game game,String properties) {
      boolean back=properties.contains("player_shrink:");
      int progress=Integer.parseInt(value(properties,back?"player_shrink:":"enemy_shrink:"));
      Pokemon pokemon=back?game.player.currPokemon:game.battle.oppPokemon;
      Sprite source=back?pokemon.backSprite:pokemon.sprite;
      Sprite reduced=new Sprite(source);
      float factor=progress==2?3f/7f:5f/7f;
      reduced.setBounds(source.getX()+source.getWidth()*(1-factor)*.5f,source.getY(),source.getWidth()*factor,source.getHeight()*factor);
      Clip clip=new Clip(game.uiBatch,bottom(),height());
      try {
         game.johtoBattleRenderer.drawBackground(game);
         if(back&&game.battle.oppPokemon!=null&&!DrawBattle.hideOppPokemon)game.battle.oppPokemon.sprite.draw(game.uiBatch);
         if(!back&&game.player.currPokemon!=null&&!DrawBattle.hideOwnPokemon)game.player.currPokemon.backSprite.draw(game.uiBatch);
         if(!PmdBattleSprites.draw(game,pokemon,reduced,back,"Idle"))
            throw new IllegalStateException("Missing PMD shrink actor: "+pokemon.specie.name);
         shrinkFrames++;
      } finally {clip.close(game.uiBatch);}
      return true;
   }

   /** Draw actual normal-move Sprite, not the SpriteProxy-only compatibility hook. */
   void drawFrame(Game game,Sprite frame,String properties) {
      boolean modernMove=false;
      if(ModernMoveEffects.family(animationName)>0){
         Clip clip=new Clip(game.uiBatch,bottom(),height());
         try{modernMove=modern.draw(game,animationName,localFrames+screenFrames+1,duration);modernFrames++;}
         finally{clip.close(game.uiBatch);}
      }
      if(modernMove){if(scope==Scope.SCREEN)screenFrames++;else localFrames++;}
      else if(scope==Scope.SCREEN) {
         Clip clip=new Clip(game.uiBatch,bottom(),height());
         try {game.uiBatch.draw(frame.getTexture(),left(),bottom(),width(),height(),
            frame.getU(),frame.getV2(),frame.getU2(),frame.getV());screenFrames++;}
         finally {clip.close(game.uiBatch);}
      } else {
         // A continuous ruled mesh carries the authored particles between the
         // same actor anchors. Original PNGs, move clocks and source Sprite stay
         // read-only; the arena no longer jumps back to their old coordinates.
         float packed=frame.getColor().toFloatBits();int at=0;
         float x=frame.getX(),y=frame.getY(),w=frame.getWidth(),h=frame.getHeight();
         for(int column=0;column<20;column++){
            float f0=column/20f,f1=(column+1)/20f;
            for(int corner=0;corner<4;corner++){
               float px=x+w*(corner<2?f0:f1),py=(corner==1||corner==2)?y+h:y;
               game.johtoBattleRenderer.projectScenePoint(game,px,py,projected);
               orbitVertices[at++]=projected.x;orbitVertices[at++]=projected.y;
               orbitVertices[at++]=packed;
               float f=corner<2?f0:f1;
               orbitVertices[at++]=frame.getU()+(frame.getU2()-frame.getU())*f;
               orbitVertices[at++]=(corner==0||corner==3)?frame.getV2():frame.getV();
            }
         }
         Clip clip=new Clip(game.uiBatch,bottom(),height());
         try{game.uiBatch.draw(frame.getTexture(),orbitVertices,0,at);localFrames++;}
         finally{clip.close(game.uiBatch);}
      }
      // Health is drawn once more after composites, never sampled or distorted.
      DrawBattle draw=game.battle.drawAction;
      if(draw!=null) {
         if(draw.drawFriendlyHealthAction!=null&&!has(properties,"player_healthbar_gone"))
            game.johtoBattleRenderer.drawFriendlyHealth(game,draw.drawFriendlyHealthAction);
         if(draw.drawEnemyHealthAction!=null&&!has(properties,"enemy_healthbar_gone"))
            game.johtoBattleRenderer.drawEnemyHealth(game,draw.drawEnemyHealthAction);
      }
   }

   /** Transparent target-actor buffer. No scenery/HUD pixels can enter row effects. */
   private void capture(Game game,Pokemon target) {
      SpriteBatch batch=game.uiBatch;batch.flush();
      GlState state=new GlState();
      Matrix4 projection=new Matrix4(batch.getProjectionMatrix()),transform=new Matrix4(batch.getTransformMatrix());
      ShaderProgram shader=batch.getShader();Color color=new Color(batch.getColor());
      try {
         int w=Gdx.graphics.getBackBufferWidth(),h=Gdx.graphics.getBackBufferHeight();
         if(snapshot==null||snapshot.getWidth()!=w||snapshot.getHeight()!=h) {
            if(snapshot!=null)snapshot.dispose();
            snapshot=new FrameBuffer(Pixmap.Format.RGBA8888,w,h,true);
            snapshot.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
         }
         snapshot.begin();Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
         batch.setProjectionMatrix(new Matrix4().setToOrtho2D(left(),bottom(),width(),height()));
         batch.setTransformMatrix(new Matrix4());batch.setShader(null);batch.setColor(Color.WHITE);
         Gdx.gl.glDepthMask(true);Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
         if(target!=null&&target==game.battle.oppPokemon&&DrawBattle.shouldDrawOppPokemon&&!DrawBattle.hideOppPokemon)
            game.battle.oppPokemon.sprite.draw(batch);
         else if(target!=null&&target==game.player.currPokemon&&DrawBattle.shouldDrawOwnPokemon&&!DrawBattle.hideOwnPokemon)
            game.player.currPokemon.backSprite.draw(batch);
         batch.flush();captureCount++;
      } finally {
         batch.flush();
         batch.setProjectionMatrix(projection);batch.setTransformMatrix(transform);batch.setShader(shader);batch.setColor(color);
         snapshot.end();state.restore();
      }
   }

   private static boolean has(String properties,String name) {return (" "+properties+" ").contains(" "+name+" ");}
   private static String value(String properties,String prefix) {return properties.split(prefix)[1].split(" ")[0];}
   private void ensureShader() {
      if(colors!=null)return;
      colors=new ShaderProgram("attribute vec4 a_position; attribute vec4 a_color; attribute vec2 a_texCoord0; uniform mat4 u_projTrans; varying vec4 v_color; varying vec2 v_uv; void main(){v_color=a_color;v_color.a*=255.0/254.0;v_uv=a_texCoord0;gl_Position=u_projTrans*a_position;}",
         "#ifdef GL_ES\nprecision mediump float;\n#endif\nvarying vec4 v_color; varying vec2 v_uv; uniform sampler2D u_texture; uniform float u_tint,u_alpha; void main(){vec4 c=texture2D(u_texture,v_uv)*v_color;c.rgb=mix(c.rgb,vec3(.76,.86,1.0),u_tint);c.a*=u_alpha;gl_FragColor=c;}");
      if(!colors.isCompiled())throw new IllegalStateException("Move effect color shader: "+colors.getLog());
   }

   /** Metadata can affect the arena but never the command/text area below y=48. */
   private static final class Clip {
      final boolean enabled;final int[] box;
      Clip(SpriteBatch batch,float bottom,float height) {this(batch,bottom,height,false);}
      Clip(SpriteBatch batch,float bottom,float height,boolean local) {
         batch.flush();enabled=Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);box=ints(GL20.GL_SCISSOR_BOX,4);
         int w=Gdx.graphics.getBackBufferWidth(),h=Gdx.graphics.getBackBufferHeight();
         int y=Math.max(0,(int)Math.ceil((48-bottom)/height*h));
         int x=local?(int)Math.ceil(w*.5f-80*h/height):0;
         int right=local?(int)Math.floor(w*.5f+80*h/height):w,top=h;
         if(enabled){x=Math.max(x,box[0]);y=Math.max(y,box[1]);right=Math.min(right,box[0]+box[2]);top=Math.min(top,box[1]+box[3]);}
         Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);Gdx.gl.glScissor(x,y,Math.max(0,right-x),Math.max(0,top-y));
      }
      void close(SpriteBatch batch) {batch.flush();Gdx.gl.glScissor(box[0],box[1],box[2],box[3]);if(!enabled)Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);}
   }

   private static int[] ints(int name,int count) {
      IntBuffer values=BufferUtils.newIntBuffer(count);Gdx.gl.glGetIntegerv(name,values);
      int[] result=new int[count];for(int i=0;i<count;i++)result[i]=values.get(i);return result;
   }
   private static final class GlState {
      final int framebuffer=ints(GL20.GL_FRAMEBUFFER_BINDING,1)[0];
      final java.nio.FloatBuffer clear=BufferUtils.newFloatBuffer(4);
      final int[] viewport=ints(GL20.GL_VIEWPORT,4),scissor=ints(GL20.GL_SCISSOR_BOX,4);
      final boolean depth=Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST),clip=Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST),blend=Gdx.gl.glIsEnabled(GL20.GL_BLEND);
      final boolean depthMask=ints(GL20.GL_DEPTH_WRITEMASK,1)[0]!=0;
      final int sourceRgb=ints(GL20.GL_BLEND_SRC_RGB,1)[0],destinationRgb=ints(GL20.GL_BLEND_DST_RGB,1)[0];
      final int sourceAlpha=ints(GL20.GL_BLEND_SRC_ALPHA,1)[0],destinationAlpha=ints(GL20.GL_BLEND_DST_ALPHA,1)[0];
      final int active=ints(GL20.GL_ACTIVE_TEXTURE,1)[0],binding=ints(GL20.GL_TEXTURE_BINDING_2D,1)[0];
      final int texture0;
      GlState(){Gdx.gl.glGetFloatv(GL20.GL_COLOR_CLEAR_VALUE,clear);Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);texture0=ints(GL20.GL_TEXTURE_BINDING_2D,1)[0];Gdx.gl.glActiveTexture(active);}
      void restore() {
         Gdx.gl.glClearColor(clear.get(0),clear.get(1),clear.get(2),clear.get(3));
         Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,framebuffer);
         Gdx.gl.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
         Gdx.gl.glScissor(scissor[0],scissor[1],scissor[2],scissor[3]);
         enable(GL20.GL_DEPTH_TEST,depth);enable(GL20.GL_SCISSOR_TEST,clip);enable(GL20.GL_BLEND,blend);
         Gdx.gl.glDepthMask(depthMask);Gdx.gl.glBlendFuncSeparate(sourceRgb,destinationRgb,sourceAlpha,destinationAlpha);
         Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D,texture0);
         Gdx.gl.glActiveTexture(active);Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D,binding);
      }
      private void enable(int value,boolean enabled){if(enabled)Gdx.gl.glEnable(value);else Gdx.gl.glDisable(value);}
   }

   public Scope getScope(){return scope;}
   public int getCaptureCount(){return captureCount;}
   public int getSnapshotWidth(){return snapshot==null?0:snapshot.getWidth();}
   public int getSnapshotHeight(){return snapshot==null?0:snapshot.getHeight();}
   public int getRowFrames(){return rowFrames;}
   public int getScreenFrames(){return screenFrames;}
   public int getLocalFrames(){return localFrames;}
   public int getColorFrames(){return colorFrames;}
   public int getShrinkFrames(){return shrinkFrames;}
   public int getModernFrames(){return modernFrames;}
   public void dispose(){
      // LibGDX ShaderProgram.dispose unbinds every current program, including a
      // caller's SpriteBatch shader. Completion may happen inside that batch.
      int caller=ints(GL20.GL_CURRENT_PROGRAM,1)[0];
      if(colors!=null&&caller==colors.getHandle()||caller==modern.shaderHandle())caller=0;
      if(snapshot!=null){snapshot.dispose();snapshot=null;}
      if(colors!=null){colors.dispose();colors=null;}
      modern.dispose();Gdx.gl.glUseProgram(caller);
   }
}
