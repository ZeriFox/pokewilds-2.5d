package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** A short authored opening. No map, save, encounter RNG or action timing is touched. */
final class StartupCinematic {
   static final float DURATION=7.2f;
   private static Texture coast,forest;
   private static final Color NIGHT=Color.valueOf("101e2c");
   static boolean active(DesktopMenuController c){
      return c.screen==DesktopMenuController.Screen.TITLE&&c.preferences.openingScene
         &&!c.preferences.reducedMotion&&c.screenAge<DURATION;
   }
   static boolean render(ModernUi ui,Game game,DesktopMenuController c) {
      if(!active(c))return false;
      ensure();float time=Math.max(0,c.screenAge);
      ModernUi.Box view=ui.viewportBounds();float saved=game.uiBatch.getPackedColor();
      try {
         ui.fill(game,NIGHT);
         // The opening crosses the shore, then enters a clearing with the selected trainer.
         float blend=smooth((time-2.3f)/.8f),arrive=smooth((time-3f)/2f);
         backdrop(game,coast,view,1.08f+.025f*time,-.028f+.009f*time,1);
         if(blend>0) {
            backdrop(game,forest,view,1.12f-.04f*arrive,.024f*(1-arrive),blend);
            float actorAlpha=blend*smooth((time-3.1f)/.5f);
            float x=54+25*arrive,y=27+5*arrive;
            shadow(ui,game,x+10,y+2,18,actorAlpha);
            TextureRegion trainer=BwAssets.get().trainer(game.player,arrive<.995f?"right":"up",time,arrive<.995f);
            region(game,trainer,x,y,24,31,actorAlpha);
            PmdPokemonSprites.Frame partner=PmdPokemonSprites.get().frame("pikachu",arrive<.995f?"right":"up",arrive<.995f?"Walk":"Idle",time);
            if(partner!=null){shadow(ui,game,x-6,y+1,11,actorAlpha);region(game,partner.region,x-14,y,16,19,actorAlpha);}
            // A close branch moves faster than the background to show parallax depth.
            TextureRegion tree=BwAssets.get().named("tree");
            region(game,tree,view.x-26-9*arrive,view.y+2,58,98,blend*.9f);
            region(game,tree,view.x+view.width-34+5*arrive,view.y-5,61,101,blend*.85f);
         }
         float title=smooth((time-5.15f)/.6f);
         if(title>0){
            ui.rect(game,view.x,87,view.width,43,new Color(NIGHT.r,NIGHT.g,NIGHT.b,.34f*title));
            ui.fitText(game,"POKéWILDS",21,119,18,118,new Color(1,1,1,title));
            ui.fitText(game,"A NEW HORIZON",45,97,5.8f,83,new Color(ModernUi.GOLD.r,ModernUi.GOLD.g,ModernUi.GOLD.b,title));
         }
         float fade=Math.max(1-smooth(time/.55f),smooth((time-6.75f)/.45f));
         if(fade>0)ui.fill(game,new Color(NIGHT.r,NIGHT.g,NIGHT.b,fade));
         float bar=Math.max(4,view.height*.045f);
         ui.rect(game,view.x,view.y,view.width,bar,NIGHT);
         ui.rect(game,view.x,view.top()-bar,view.width,bar,NIGHT);
         ui.fitText(game,"Enter / "+DesktopControls.confirm()+"  Skip intro",82,view.y+bar-1,4.2f,72,ModernUi.PAPER);
      } finally {game.uiBatch.setPackedColor(saved);}
      return true;
   }
   private static void ensure(){
      if(coast==null){coast=load("beach");forest=load("forest");}
   }
   static void menuBackdrop(ModernUi ui,Game game,DesktopMenuController c){
      ensure();ModernUi.Box view=ui.viewportBounds();float saved=game.uiBatch.getPackedColor();
      float time=c.preferences.reducedMotion?0:c.clock;
      try {
         backdrop(game,forest,view,1.08f,.006f*(float)Math.sin(time*.16f),1);
         ui.fill(game,new Color(NIGHT.r,NIGHT.g,NIGHT.b,.48f));
         // Soft upper shade holds readable title text over the detailed foliage.
         for(int i=0;i<16;i++){
            float y=76+(view.top()-76)*i/16f;
            ui.rect(game,view.x,y,view.width,(view.top()-76)/16f,new Color(.025f,.06f,.10f,.24f*i/15f));
         }
         TextureRegion trainer=BwAssets.get().trainer(game.player,"right",time*.35f,!c.preferences.reducedMotion);
         region(game,trainer,view.x+view.width*.18f,view.y+12,24,31,.85f);
      }finally{game.uiBatch.setPackedColor(saved);}
   }
   private static Texture load(String id){
      Texture texture=new Texture(Gdx.files.internal("visual/battle-scenes/"+id+".png"));
      texture.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);return texture;
   }
   private static void backdrop(Game game,Texture texture,ModernUi.Box view,float zoom,float pan,float alpha){
      float scale=Math.max(view.width/texture.getWidth(),view.height/texture.getHeight())*zoom;
      float w=texture.getWidth()*scale,h=texture.getHeight()*scale;
      game.uiBatch.setColor(1,1,1,alpha);
      game.uiBatch.draw(texture,view.x+(view.width-w)*.5f+pan*view.width,view.y+(view.height-h)*.5f,w,h);
      game.uiBatch.setColor(Color.WHITE);
   }
   private static void region(Game game,TextureRegion image,float x,float y,float width,float height,float alpha){
      if(image==null)return;
      float scale=Math.min(width/image.getRegionWidth(),height/image.getRegionHeight());
      float w=image.getRegionWidth()*scale,h=image.getRegionHeight()*scale;
      game.uiBatch.setColor(1,1,1,alpha);game.uiBatch.draw(image,x+(width-w)*.5f,y,w,h);game.uiBatch.setColor(Color.WHITE);
   }
   private static void shadow(ModernUi ui,Game game,float x,float y,float width,float alpha){
      ui.surface(game,x-width*.5f,y,width,2.4f,1.1f,new Color(.05f,.12f,.1f,.22f*alpha));
   }
   private static float smooth(float value){float p=Math.max(0,Math.min(1,value));return p*p*(3-2*p);}
   static void disposeShared(){if(coast!=null){coast.dispose();coast=null;}if(forest!=null){forest.dispose();forest=null;}}
}
