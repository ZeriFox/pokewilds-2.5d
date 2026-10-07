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
   private FrameBuffer snapshot;
   private ShaderProgram colors;
   private boolean captured;
   private int captureCount, rowFrames, screenFrames, localFrames, colorFrames, shrinkFrames;

   public MoveEffectPresentation(String animationName) { scope=scopeFor(animationName); }

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
      if(properties.contains("screenshot:")) capture(game);
      if(!rows&&!recolor)return true;
      if(!captured || snapshot.getWidth()!=Gdx.graphics.getBackBufferWidth()
         ||snapshot.getHeight()!=Gdx.graphics.getBackBufferHeight() || !rows)capture(game);
      SpriteBatch batch=game.uiBatch;
      ShaderProgram oldShader=batch.getShader();Color oldColor=new Color(batch.getColor());
      boolean untargetedDistortion=properties.contains("row_displace:")&&!properties.contains("displace_target:");
      boolean fullField=scope==Scope.SCREEN||recolor||untargetedDistortion;
      Clip clip=new Clip(batch,bottom(),height(),!fullField);
      try {
         batch.setColor(Color.WHITE);
         if(recolor) {
            ensureShader();batch.setShader(colors);
            colors.setUniformf("u_inverse",has(properties,"inverse_colors")?1f:0f);
            colors.setUniformf("u_dark",has(properties,"darken_effect3")?1f:has(properties,"darken_effect2")?.66f:has(properties,"darken_effect1")?.33f:0f);
            colors.setUniformf("u_light",has(properties,"lighten_effect2")?.66f:has(properties,"lighten_effect1")?.33f:0f);
            colorFrames++;
         }
         // Local rows replace only their authored target bands; repainting the whole
         // snapshot would unnecessarily freeze/resample the untouched modern field.
         if(fullField)strip(batch,left(),bottom(),width(),height(),left(),bottom(),width(),height());
         if(rows) {
            rowFrames++;
            boolean ignorePlayer=action.name.contains("player")&&scope!=Scope.SCREEN;
            boolean ignoreEnemy=action.name.contains("enemy")&&scope!=Scope.SCREEN;
            for(String token:properties.split("\\s+"))if(token.startsWith("row_copy:")) {
               String[] pair=token.substring(9).split(",");
               int targetY=144-Integer.parseInt(pair[0]),sourceY=144-Integer.parseInt(pair[1]);
               float x0=ignorePlayer&&targetY<112?86:scope==Scope.SCREEN?left():0;
               float x1=ignoreEnemy&&targetY>=88?96:scope==Scope.SCREEN?left()+width():160;
               strip(batch,x0,targetY,x1-x0,1,x0,sourceY,x1-x0,1);
            }
            if(properties.contains("row_displace:")) {
               double speed=Double.parseDouble(value(properties,"row_displace:"));
               // Same progress accumulator and threshold as the original action.
               if(action.frameNum>=action.dispLastFrame+1.0/speed) {
                  action.dispLastFrame=action.frameNum;action.dispProgress++;
               }
               ignorePlayer=properties.contains("displace_target:enemy");
               ignoreEnemy=properties.contains("displace_target:player");
               for(int y=48;y<Math.ceil(bottom()+height());y++)
                  if((!ignorePlayer||y>=88)&&(!ignoreEnemy||y<92)) {
                     int shift=(int)(5.0*Math.sin((Math.PI/6)*(y+action.dispProgress)));
                     displacedRow(batch,y,shift,ignorePlayer,ignoreEnemy);
                  }
            }
            if(properties.contains("row_split:")) {
               int progress=Integer.parseInt(value(properties,"row_split:"));
               ignorePlayer=action.name.contains("enemy");ignoreEnemy=action.name.contains("player");
               for(int y=48;y<Math.ceil(bottom()+height());y++)
                  if((!ignorePlayer||y>=88)&&(!ignoreEnemy||y<92))
                     displacedRow(batch,y,progress*(y%2==0?-1:1),ignorePlayer,ignoreEnemy);
            }
         }
      } finally {
         batch.flush();batch.setShader(oldShader);batch.setColor(oldColor);clip.close(batch);
      }
      return true;
   }

   private void displacedRow(SpriteBatch batch,int y,int shift,boolean ignorePlayer,boolean ignoreEnemy) {
      float x0=ignorePlayer?86:ignoreEnemy?0:left(),x1=ignoreEnemy?96:ignorePlayer?160:left()+width();
      // Keep the source band, shift its copied pixels, and clip only at viewport/UI.
      strip(batch,x0+shift,y,x1-x0,1,x0,y,x1-x0,1);
   }

   private void strip(SpriteBatch batch,float x,float y,float w,float h,float sx,float sy,float sw,float sh) {
      Texture texture=snapshot.getColorBufferTexture();
      float u=(sx-left())/width(),v=(sy-bottom())/height();
      // This explicit-UV overload takes the lower edge first; FBO v=0 is bottom.
      batch.draw(texture,x,y,w,h,u,v,u+sw/width(),v+sh/height());
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
      if(scope==Scope.SCREEN) {
         Clip clip=new Clip(game.uiBatch,bottom(),height());
         try {game.uiBatch.draw(frame.getTexture(),left(),bottom(),width(),height(),
            frame.getU(),frame.getV2(),frame.getU2(),frame.getV());screenFrames++;}
         finally {clip.close(game.uiBatch);}
      } else {frame.draw(game.uiBatch);localFrames++;}
      // Health is drawn once more after composites, never sampled or distorted.
      DrawBattle draw=game.battle.drawAction;
      if(draw!=null) {
         if(draw.drawFriendlyHealthAction!=null&&!has(properties,"player_healthbar_gone"))
            game.johtoBattleRenderer.drawFriendlyHealth(game,draw.drawFriendlyHealthAction);
         if(draw.drawEnemyHealthAction!=null&&!has(properties,"enemy_healthbar_gone"))
            game.johtoBattleRenderer.drawEnemyHealth(game,draw.drawEnemyHealthAction);
      }
   }

   /** Repaint only arena and actors to a viewport-sized target: no HUD, no action.step(). */
   private void capture(Game game) {
      SpriteBatch batch=game.uiBatch;batch.flush();
      GlState state=new GlState();
      Matrix4 projection=new Matrix4(batch.getProjectionMatrix()),transform=new Matrix4(batch.getTransformMatrix());
      ShaderProgram shader=batch.getShader();Color color=new Color(batch.getColor());
      try {
         int w=Gdx.graphics.getBackBufferWidth(),h=Gdx.graphics.getBackBufferHeight();
         if(snapshot==null||snapshot.getWidth()!=w||snapshot.getHeight()!=h) {
            if(snapshot!=null)snapshot.dispose();
            snapshot=new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false);
            snapshot.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
         }
         snapshot.begin();Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
         batch.setProjectionMatrix(new Matrix4().setToOrtho2D(left(),bottom(),width(),height()));
         batch.setTransformMatrix(new Matrix4());batch.setShader(null);batch.setColor(Color.WHITE);
         game.johtoBattleRenderer.drawBackground(game);
         if(game.battle.oppPokemon!=null&&DrawBattle.shouldDrawOppPokemon&&!DrawBattle.hideOppPokemon)
            game.battle.oppPokemon.sprite.draw(batch);
         if(game.player.currPokemon!=null&&DrawBattle.shouldDrawOwnPokemon&&!DrawBattle.hideOwnPokemon)
            game.player.currPokemon.backSprite.draw(batch);
         batch.flush();captured=true;captureCount++;
      } finally {
         batch.flush();
         batch.setProjectionMatrix(projection);batch.setTransformMatrix(transform);batch.setShader(shader);batch.setColor(color);
         state.restore();
      }
   }

   private static boolean has(String properties,String name) {return (" "+properties+" ").contains(" "+name+" ");}
   private static String value(String properties,String prefix) {return properties.split(prefix)[1].split(" ")[0];}
   private void ensureShader() {
      if(colors!=null)return;
      colors=new ShaderProgram("attribute vec4 a_position; attribute vec4 a_color; attribute vec2 a_texCoord0; uniform mat4 u_projTrans; varying vec4 v_color; varying vec2 v_uv; void main(){v_color=a_color;v_color.a*=255.0/254.0;v_uv=a_texCoord0;gl_Position=u_projTrans*a_position;}",
         "#ifdef GL_ES\nprecision mediump float;\n#endif\nvarying vec4 v_color; varying vec2 v_uv; uniform sampler2D u_texture; uniform float u_inverse,u_dark,u_light; void main(){vec4 c=texture2D(u_texture,v_uv)*v_color;c.rgb=mix(c.rgb,vec3(1.0)-c.rgb,u_inverse);c.rgb=mix(c.rgb,vec3(0.0),u_dark);c.rgb=mix(c.rgb,vec3(1.0),u_light);gl_FragColor=c;}");
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
      final int[] viewport=ints(GL20.GL_VIEWPORT,4),scissor=ints(GL20.GL_SCISSOR_BOX,4);
      final boolean depth=Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST),clip=Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST),blend=Gdx.gl.glIsEnabled(GL20.GL_BLEND);
      final boolean depthMask=ints(GL20.GL_DEPTH_WRITEMASK,1)[0]!=0;
      final int sourceRgb=ints(GL20.GL_BLEND_SRC_RGB,1)[0],destinationRgb=ints(GL20.GL_BLEND_DST_RGB,1)[0];
      final int sourceAlpha=ints(GL20.GL_BLEND_SRC_ALPHA,1)[0],destinationAlpha=ints(GL20.GL_BLEND_DST_ALPHA,1)[0];
      final int active=ints(GL20.GL_ACTIVE_TEXTURE,1)[0],binding=ints(GL20.GL_TEXTURE_BINDING_2D,1)[0];
      final int texture0;
      GlState(){Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);texture0=ints(GL20.GL_TEXTURE_BINDING_2D,1)[0];Gdx.gl.glActiveTexture(active);}
      void restore() {
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
   public void dispose(){if(snapshot!=null){snapshot.dispose();snapshot=null;}if(colors!=null){colors.dispose();colors=null;}captured=false;}
}
