package com.pkmngen.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/** Visible sparkle identity independent of shiny-sheet coverage; never alters palettes. */
public final class ShinyVisuals {
   private static final Color GOLD=new Color(1f,.80f,.24f,1f),WHITE=new Color(1f,.98f,.88f,1f);
   private ShinyVisuals(){}
   public static void drawBattle(Game game,Pokemon pokemon,float cx,float baseY,float w,float h,float seconds){
      if(game==null||game.modernUi==null||pokemon==null||!pokemon.isShiny||pokemon.isGhost||pokemon.isEgg)return;
      draw(game,cx-w*.5f,baseY,w,h,seconds);
   }
   static void draw(Game game,float x,float y,float w,float h,float seconds){
      ModernUi ui=game.modernUi;if(ui==null)return;
      boolean still=ui.reducedMotion();float clock=still?0:seconds;
      for(int i=0;i<4;i++) {
         float phase=(clock*.65f+i*.25f)%1f;
         float alpha=still?.85f:.30f+.7f*MathUtils.sin(phase*MathUtils.PI);
         float px=x+w*(.12f+.76f*(i%2))+(still?0:MathUtils.sin(clock*1.7f+i)*w*.04f);
         float py=y+h*(.2f+i*.19f);
         float radius=Math.max(1.1f,Math.min(2.2f,w*.06f))*(still?1:.6f+.4f*MathUtils.sin(phase*MathUtils.PI));
         GOLD.a=alpha;WHITE.a=alpha;
         ui.rect(game,px-radius,py-.35f,radius*2,.7f,GOLD);
         ui.rect(game,px-.35f,py-radius,.7f,radius*2,GOLD);
         ui.rect(game,px-.45f,py-.45f,.9f,.9f,WHITE);
      }
   }
}
