package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.IntBuffer;

/** Read-only presentation of the original minimap action on the modern UI canvas.
 * The action owns input, teleport rules and its textures, including their disposal. */
final class ModernMapUi {
   static final ModernUi.Box BOX=new ModernUi.Box(6,20,148,97);
   private static final Color SEA=Color.valueOf("213d50"), PLAYER=Color.valueOf("ffe292"),
      CAMP=Color.valueOf("88d9eb"), WARP=Color.valueOf("c0a0f2");
   private DrawMiniMap active;
   private float baseX,baseY,scale,originX,originY;
   private final Vector2 point=new Vector2();

   void clear(){active=null;}
   private void layout(DrawMiniMap menu) {
      if(active!=menu){active=menu;baseX=menu.currX;baseY=menu.currY;}
      scale=Math.min((BOX.width-8)/menu.texture.getWidth(),(BOX.height-8)/menu.texture.getHeight());
      originX=BOX.x+(BOX.width-menu.texture.getWidth()*scale)*.5f+(menu.currX-baseX+menu.currOffset.x)*scale;
      originY=BOX.y+(BOX.height-menu.texture.getHeight()*scale)*.5f+(menu.currY-baseY+menu.currOffset.y)*scale;
   }
   Vector2 point(Game game,DrawMiniMap menu,float x,float y,Vector2 result) {
      layout(menu);return result.set(originX+x*scale,originY+y*scale);
   }
   void render(ModernUi ui,Game game,DrawMiniMap menu) {
      if(menu.firstStep||menu.texture==null||menu.texture.getTextureObjectHandle()==0)return;
      layout(menu);
      ui.fullScreen(game,"WORLD MAP","Procedural island");ui.panel(game,BOX);
      ui.rect(game,BOX.x+1,BOX.y+1,BOX.width-2,BOX.height-2,SEA);
      game.uiBatch.flush();
      boolean clipping=Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
      IntBuffer old=BufferUtils.newIntBuffer(4);Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX,old);
      ModernUi.Box view=ui.viewportBounds();
      float sx=Gdx.graphics.getBackBufferWidth()/view.width,sy=Gdx.graphics.getBackBufferHeight()/view.height;
      int left=(int)Math.ceil((BOX.x+1-view.x)*sx),bottom=(int)Math.ceil((BOX.y+1-view.y)*sy);
      int right=(int)Math.floor((BOX.x+BOX.width-1-view.x)*sx),top=(int)Math.floor((BOX.y+BOX.height-1-view.y)*sy);
      if(clipping){left=Math.max(left,old.get(0));bottom=Math.max(bottom,old.get(1));
         right=Math.min(right,old.get(0)+old.get(2));top=Math.min(top,old.get(1)+old.get(3));}
      float color=game.uiBatch.getPackedColor();
      try {
         Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);Gdx.gl.glScissor(left,bottom,Math.max(0,right-left),Math.max(0,top-bottom));
         // Borrow the live island overview. WorldBatch must keep suppressing the
         // legacy draw; otherwise the perspective world paints over it again.
         game.uiBatch.setColor(Color.WHITE);
         game.uiBatch.draw(menu.texture,originX,originY,menu.texture.getWidth()*scale,menu.texture.getHeight()*scale);
         for(Tile tile:DrawMiniMap.warpTiles.values()) {
            point(game,menu,(tile.position.x-game.map.bottomLeft.x)/8,(tile.position.y-game.map.bottomLeft.y)/8,point);
            marker(ui,game,point.x,point.y,WARP,1.1f);
         }
         point(game,menu,menu.spawnLoc.x,menu.spawnLoc.y,point);marker(ui,game,point.x,point.y,CAMP,1.5f);
         point(game,menu,menu.playerPos.x,menu.playerPos.y,point);marker(ui,game,point.x,point.y,PLAYER,1.8f);
         if(menu.nearestWarpTile!=null) {
            Tile tile=menu.nearestWarpTile;
            point(game,menu,(tile.position.x-game.map.bottomLeft.x)/8,(tile.position.y-game.map.bottomLeft.y)/8,point);
            cross(ui,game,point.x,point.y,WARP);
         }
      } finally {
         game.uiBatch.flush();game.uiBatch.setPackedColor(color);
         Gdx.gl.glScissor(old.get(0),old.get(1),old.get(2),old.get(3));
         if(!clipping)Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
      }
      ui.rect(game,9,15,2,2,PLAYER);ui.text(game,"You",13,17.4f,3.5f,ModernUi.INK);
      ui.rect(game,33,15,2,2,CAMP);ui.text(game,"Camp",37,17.4f,3.5f,ModernUi.INK);
      ui.rect(game,65,15,2,2,WARP);ui.text(game,"Teleport point",69,17.4f,3.5f,ModernUi.INK);
      ui.footer(game,DesktopControls.movement()+" Pan   "+DesktopControls.confirm()+" Select   "+DesktopControls.back()+" / Esc Back");
      ui.mark(menu);
   }
   private void marker(ModernUi ui,Game game,float x,float y,Color color,float radius) {
      ui.surface(game,x-radius-.5f,y-radius-.5f,2*radius+1,2*radius+1,.6f,ModernUi.INK);
      ui.surface(game,x-radius,y-radius,2*radius,2*radius,.4f,color);
   }
   private void cross(ModernUi ui,Game game,float x,float y,Color color) {
      ui.rect(game,x-4,y-.35f,2,.7f,color);ui.rect(game,x+2,y-.35f,2,.7f,color);
      ui.rect(game,x-.35f,y-4,.7f,2,color);ui.rect(game,x-.35f,y+2,.7f,2,color);
   }
}
