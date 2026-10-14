package com.pkmngen.game;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Vector3;
import java.util.WeakHashMap;

/** New ball artwork on the original throw/wobble clocks and capture rules. */
final class CaptureBallVisual {
   private static final WeakHashMap<Game,String> selected=new WeakHashMap<>();
   static void select(Game game,String name){selected.put(game,name==null||name.isEmpty()?"poke ball":name);}
   static String selected(Game game){return selected.getOrDefault(game,"poke ball");}
   static void draw(Game game,Sprite sprite){
      if(sprite instanceof BallSprite){sprite.draw(game.uiBatch);return;}
      if(game.johtoBattleRenderer==null||!game.johtoBattleRenderer.drawSceneEffect(sprite,game.uiBatch))sprite.draw(game.uiBatch);
   }
   static Sprite create(Game game,Texture original,int x,int y,int w,int h){
      if(game.modernUi==null)return new Sprite(original,x,y,w,h);
      return new BallSprite(game,original,x,y,w,h,selected(game));
   }
   private static final class BallSprite extends Sprite {
      final Game game;final int pose;final String ball;
      final Vector3 projected=new Vector3();
      BallSprite(Game game,Texture original,int x,int y,int w,int h,String ball){super(original,x,y,w,h);this.game=game;this.pose=x/12;this.ball=ball;}
      @Override public void draw(Batch batch){
         if(batch instanceof ModernBatch&&((ModernBatch)batch).suppressed)return;
         Sprite art=new Sprite(UserSpriteAssets.get().ball(ball,0));
         float cx=getX()+getWidth()*.5f,cy=getY()+getHeight()*.5f;
         if(game.johtoBattleRenderer!=null)game.johtoBattleRenderer.projectScenePoint(game,cx,cy,projected);else projected.set(cx,cy,1);
         float width=getWidth()*.68f*projected.z,height=getHeight()*.68f*projected.z;
         art.setBounds(projected.x-width*.5f,projected.y-height*.5f,width,height);
         art.setOriginCenter();art.setScale(getScaleX(),getScaleY());art.setRotation(getRotation()+(pose==1?-22:pose==2?22:0));
         art.setColor(getColor());art.draw(batch);
      }
   }
}
