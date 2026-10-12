package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Concealed spirit artwork only: never inspects or reveals the underlying species. */
final class GhostVisualAssets {
   private static GhostVisualAssets shared;
   private final Texture texture;
   private final TextureRegion[] frames=new TextureRegion[4];
   private GhostVisualAssets() {
      texture=new Texture(Gdx.files.internal("visual/ghost/lavender-spirit.png"));
      texture.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
      int width=texture.getWidth()/frames.length;
      for(int i=0;i<frames.length;i++)frames[i]=new TextureRegion(texture,i*width,0,width,texture.getHeight());
   }
   static GhostVisualAssets get(){if(shared==null)shared=new GhostVisualAssets();return shared;}
   TextureRegion frame(float seconds){return frames[frameIndex(seconds)];}
   static int frameIndex(float seconds){return !Float.isFinite(seconds)||seconds<=0?0:(int)((seconds%1.2f)/.3f)%4;}
   static void disposeShared(){if(shared!=null){shared.texture.dispose();shared=null;}}
}
