package com.pkmngen.game;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Affine2;
import com.pkmngen.game.util.ProxyBatch;

/** Discards only draw submissions while an original menu advances its state. */
final class ModernBatch extends ProxyBatch {
   boolean suppressed;
   @Override public void draw(Texture t,float x,float y) { if(!suppressed) super.draw(t,x,y); }
   @Override public void draw(Texture t,float x,float y,float w,float h) { if(!suppressed) super.draw(t,x,y,w,h); }
   @Override public void draw(Texture t,float[] v,int o,int n) { if(!suppressed) super.draw(t,v,o,n); }
   @Override public void draw(Texture t,float x,float y,int sx,int sy,int sw,int sh) { if(!suppressed) super.draw(t,x,y,sx,sy,sw,sh); }
   @Override public void draw(Texture t,float x,float y,float w,float h,int sx,int sy,int sw,int sh,boolean fx,boolean fy) { if(!suppressed) super.draw(t,x,y,w,h,sx,sy,sw,sh,fx,fy); }
   @Override public void draw(Texture t,float x,float y,float ox,float oy,float w,float h,float scx,float scy,float rot,int sx,int sy,int sw,int sh,boolean fx,boolean fy) { if(!suppressed) super.draw(t,x,y,ox,oy,w,h,scx,scy,rot,sx,sy,sw,sh,fx,fy); }
   @Override public void draw(Texture t,float x,float y,float w,float h,float u,float v,float u2,float v2) { if(!suppressed) super.draw(t,x,y,w,h,u,v,u2,v2); }
   @Override public void draw(TextureRegion t,float x,float y) { if(!suppressed) super.draw(t,x,y); }
   @Override public void draw(TextureRegion t,float x,float y,float w,float h) { if(!suppressed) super.draw(t,x,y,w,h); }
   @Override public void draw(TextureRegion t,float x,float y,float ox,float oy,float w,float h,float sx,float sy,float r) { if(!suppressed) super.draw(t,x,y,ox,oy,w,h,sx,sy,r); }
   @Override public void draw(TextureRegion t,float x,float y,float ox,float oy,float w,float h,float sx,float sy,float r,boolean cw) { if(!suppressed) super.draw(t,x,y,ox,oy,w,h,sx,sy,r,cw); }
   @Override public void draw(TextureRegion t,float w,float h,Affine2 a) { if(!suppressed) super.draw(t,w,h,a); }
}
