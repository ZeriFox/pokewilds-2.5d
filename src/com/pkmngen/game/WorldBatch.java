package com.pkmngen.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Affine2;
import com.pkmngen.game.util.ProxyBatch;
import com.pkmngen.game.util.SmolSpriteProxy;
import com.pkmngen.game.util.SpriteProxy;

/** Keeps action updates running while the modern world exclusively owns pixels. */
public final class WorldBatch extends ProxyBatch {
   private final boolean modern;
   private boolean captureTarget;
   private long suppressedDraws, submittedDraws;
   private final Color targetTint = new Color(.35f, .95f, .85f, .65f);
   private final Color sample = new Color();
   public WorldBatch(boolean modern) { this.modern = modern; }
   public void beginWorldFrame() { captureTarget=false; targetTint.set(.35f,.95f,.85f,.65f); }
   public void captureTarget(boolean value) { captureTarget=value; }
   public Color getTargetTint() { return targetTint; }
   public long getSuppressedDraws() { return suppressedDraws; }
   public long getSubmittedDraws() { return submittedDraws; }
   private boolean submit() {
      if(modern) { suppressedDraws++; return false; }
      submittedDraws++; return true;
   }
   @Override public void draw(SpriteProxy s,float x,float y) { if(submit())super.draw(s,x,y); }
   @Override public void draw(SmolSpriteProxy s,float x,float y) { if(submit())super.draw(s,x,y); }
   @Override public void draw(Texture t,float x,float y) { if(submit())super.draw(t,x,y); }
   @Override public void draw(Texture t,float x,float y,float w,float h) { if(submit())super.draw(t,x,y,w,h); }
   @Override public void draw(Texture t,float[] v,int o,int n) {
      if(modern && captureTarget && n>=20) {
         Color.abgr8888ToColor(sample,com.badlogic.gdx.utils.NumberUtils.floatToIntColor(v[o+2]));
         // Preserve the original placement validator's red/green result without
         // drawing its old tile. A red upper layer wins over a neutral lower one.
         if(sample.r>sample.g*1.1f) targetTint.set(.96f,.33f,.30f,.70f);
         else if(targetTint.g>targetTint.r) targetTint.set(.32f,.95f,.72f,.65f);
      }
      if(submit())super.draw(t,v,o,n);
   }
   @Override public void draw(Texture t,float x,float y,int sx,int sy,int sw,int sh) { if(submit())super.draw(t,x,y,sx,sy,sw,sh); }
   @Override public void draw(Texture t,float x,float y,float w,float h,int sx,int sy,int sw,int sh,boolean fx,boolean fy) { if(submit())super.draw(t,x,y,w,h,sx,sy,sw,sh,fx,fy); }
   @Override public void draw(Texture t,float x,float y,float ox,float oy,float w,float h,float scx,float scy,float rot,int sx,int sy,int sw,int sh,boolean fx,boolean fy) { if(submit())super.draw(t,x,y,ox,oy,w,h,scx,scy,rot,sx,sy,sw,sh,fx,fy); }
   @Override public void draw(Texture t,float x,float y,float w,float h,float u,float v,float u2,float v2) { if(submit())super.draw(t,x,y,w,h,u,v,u2,v2); }
   @Override public void draw(TextureRegion t,float x,float y) { if(submit())super.draw(t,x,y); }
   @Override public void draw(TextureRegion t,float x,float y,float w,float h) { if(submit())super.draw(t,x,y,w,h); }
   @Override public void draw(TextureRegion t,float x,float y,float ox,float oy,float w,float h,float sx,float sy,float r) { if(submit())super.draw(t,x,y,ox,oy,w,h,sx,sy,r); }
   @Override public void draw(TextureRegion t,float x,float y,float ox,float oy,float w,float h,float sx,float sy,float r,boolean cw) { if(submit())super.draw(t,x,y,ox,oy,w,h,sx,sy,r,cw); }
   @Override public void draw(TextureRegion t,float w,float h,Affine2 a) { if(submit())super.draw(t,w,h,a); }
}
