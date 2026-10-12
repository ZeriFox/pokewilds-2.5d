package com.pkmngen.game;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.Locale;

/** Continuous illustrated floor with a shared CPU/GPU camera projection.
 * A bounded 2.5D orbit avoids exposing absent backs of the painted scenery. */
final class BattleScenery {
   private ShaderProgram shader;
   static int kind(String biome) {
      String n=biome==null?"":biome.toLowerCase(Locale.ROOT);
      if(n.contains("volcan"))return 7;
      if(n.contains("cave")||n.contains("dungeon"))return 6;
      if(n.contains("graveyard"))return 8;
      if(n.contains("snow")||n.contains("tundra"))return 4;
      if(n.contains("desert"))return 5;if(n.contains("beach"))return 1;
      if(n.contains("ocean")||n.equals("sea")||n.equals("sea1")||n.equals("water")||n.equals("water1"))return 2;
      return 3;
   }
   static String asset(String biome) {
      return switch(kind(biome)){case 1,2->"beach";case 4->"snow";case 5->"desert";
         case 6->"cave";case 7->"volcano";case 8->"graveyard";default->"forest";};
   }
   static float orbit(float time){return -.18f*(1-MathUtils.cos(time*.16f))+.20f*MathUtils.sin(time*.16f);}
   /** z returns apparent size. The source gameplay Sprite is never changed. */
   static Vector3 project(float x,float y,float yaw,Vector3 out) {
      if(yaw==0)return out.set(x,y,1);
      float z=(y-70f)/(.55f-(y-70f)/260f),depth=1+z/260f;
      float wx=(x-80f)*depth,c=MathUtils.cos(yaw),s=MathUtils.sin(yaw);
      float rx=c*wx-s*z,rz=s*wx+c*z,scale=1/(1+rz/260f);
      return out.set(80+rx*scale,70+.55f*rz*scale,scale*depth);
   }
   static String facing(boolean friendly,float yaw) {
      float angle=45f+yaw*MathUtils.radiansToDegrees+(friendly?0:180);
      String[] dirs={"right","up-right","up","up-left","left","down-left","down","down-right"};
      return dirs[Math.floorMod(Math.round(angle/45f),8)];
   }
   void draw(SpriteBatch batch,Texture arena,String biome,float time,float x,float y,float w,float h){
      draw(batch,arena,biome,time,orbit(time),x,y,w,h);
   }
   void draw(SpriteBatch batch,Texture arena,String biome,float time,float yaw,float x,float y,float w,float h) {
      if(shader==null){
         shader=new ShaderProgram(VERTEX,FRAGMENT);
         if(!shader.isCompiled()){String log=shader.getLog();shader.dispose();shader=null;throw new IllegalStateException("Battle scenery shader: "+log);}
      }
      ShaderProgram previous=batch.getShader();
      try{
         batch.setShader(shader);shader.setUniformf("u_time",time);shader.setUniformf("u_yaw",yaw);
         shader.setUniformf("u_kind",kind(biome));shader.setUniformf("u_canvas",x,y,w,h);
         shader.setUniformf("u_size",arena.getWidth(),arena.getHeight());batch.draw(arena,x,y,w,h);
      }finally{batch.setShader(previous);}
   }
   void dispose(){if(shader!=null){shader.dispose();shader=null;}}
   private static final String VERTEX="""
      attribute vec4 a_position;attribute vec4 a_color;attribute vec2 a_texCoord0;
      uniform mat4 u_projTrans;varying vec4 v_color;varying vec2 v_texCoords;
      void main(){v_color=a_color;v_color.a*=255.0/254.0;v_texCoords=a_texCoord0;gl_Position=u_projTrans*a_position;}
      """;
   private static final String FRAGMENT="""
      #ifdef GL_ES
      precision mediump float;
      #endif
      varying vec4 v_color;varying vec2 v_texCoords;
      uniform sampler2D u_texture;uniform vec4 u_canvas;uniform vec2 u_size;
      uniform float u_time,u_kind,u_yaw;
      float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
      float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
      float clearFloor(vec2 p){
         float halfWidth=mix(.13,.30,smoothstep(.29,.50,p.y));
         return smoothstep(.29,.38,p.y)*(1.0-smoothstep(halfWidth-.035,halfWidth,abs(p.x-.5)))
            *(1.0-smoothstep(.86,.96,p.y));
      }
      vec2 inverseFloor(vec2 p){
         // Portrait canvases extend above the floor horizon; only the lower
         // part is projected. Keep the unused upper coordinates finite too.
         p.y=min(p.y,145.0);
         float z=(p.y-70.0)/(.55-(p.y-70.0)/260.0),depth=1.0+z/260.0;
         float wx=(p.x-80.0)*depth,c=cos(u_yaw),s=sin(u_yaw);
         float rx=c*wx+s*z,rz=-s*wx+c*z;
         return vec2(80.0+rx/(1.0+rz/260.0),70.0+.55*rz/(1.0+rz/260.0));
      }
      void main(){
         vec2 uv=v_texCoords;
         vec2 point=vec2(u_canvas.x+uv.x*u_canvas.z,u_canvas.y+(1.0-uv.y)*u_canvas.w);
         vec2 floorPoint=inverseFloor(point);
         vec2 floorUv=vec2((floorPoint.x-u_canvas.x)/u_canvas.z,1.0-(floorPoint.y-u_canvas.y)/u_canvas.w);
         float aspect=u_canvas.z/u_canvas.w,artAspect=u_size.x/u_size.y;
         vec2 cover=vec2(min(1.0,aspect/artAspect),min(1.0,artAspect/aspect));
         vec2 artworkUv=(uv-.5)*cover*.91+.5;
         float edge=smoothstep(.63,.94,abs(artworkUv.x-.5)*2.0);
         // The perimeter contains upright trunks and rocks. Treat it as a
         // separate depth layer rather than rotating those pixels as a floor.
         float floorMask=clearFloor(artworkUv);
         // Separate depths for distant vista, middle trunks and near framing foliage.
         vec2 vista=uv;vista.x+=sin(u_yaw)*(.018+edge*.072);vista.y+=sin(u_yaw)*sin(u_yaw)*.016;
         vec2 sampleUv=(vista-.5)*cover*.91+.5;
         floorUv=(floorUv-.5)*cover*.91+.5;
         // A rotated ray must not pull a perimeter trunk into the clearing.
         // The unprojected perimeter remains an upright parallax layer.
         floorMask*=clearFloor(floorUv);
         vec4 base=texture2D(u_texture,clamp(sampleUv,.5/u_size,1.0-.5/u_size));
         float vegetation=smoothstep(.035,.15,base.g-base.r)*smoothstep(.025,.12,base.g-base.b);
         if(u_kind>2.5&&u_kind<4.5){
            sampleUv.x+=sin(u_time*1.3+sampleUv.y*31.0+sampleUv.x*13.0)*.0018*vegetation*(.25+edge);
            base=texture2D(u_texture,clamp(sampleUv,.5/u_size,1.0-.5/u_size));
         }
         base=mix(base,texture2D(u_texture,clamp(floorUv,.5/u_size,1.0-.5/u_size)),floorMask);
         vec3 color=base.rgb;
         float clouds=noise(sampleUv*vec2(5.0,7.0)+vec2(-u_time*.023,u_time*.004));
         color*=.93+.10*smoothstep(.18,.8,clouds);
         if(u_kind<2.5){
            float water=smoothstep(.02,.2,color.b-color.r)*smoothstep(.01,.12,color.g-color.r);
            float wave=sin(sampleUv.y*220.0-u_time*1.8+sin(sampleUv.x*19.0+u_time*.3));
            float cross=sin(sampleUv.x*53.0+sampleUv.y*90.0+u_time*.7);
            color+=vec3(.04,.065,.085)*wave*water;
            color=mix(color,vec3(.85,.97,.97),water*pow(max(0.0,wave*.7+cross*.3),10.0)*.25);
         }else if(u_kind>6.5&&u_kind<7.5){
            float lava=smoothstep(.12,.5,color.r-color.b)*smoothstep(.20,.75,color.r);
            color+=vec3(.15,.07,.01)*lava*(.5+.5*sin(u_time*1.5+sampleUv.x*25.0));
         }else if(u_kind>7.5||u_kind>5.5&&u_kind<6.5){
            float mist=noise(sampleUv*vec2(8.0,6.0)+vec2(-u_time*.032,u_time*.01));
            color=mix(color,vec3(.53,.65,.72),smoothstep(.45,.9,mist)*.10);
         }
         float shaft=pow(max(0.0,sin(sampleUv.x*14.0+sampleUv.y*5.0+u_time*.15)),8.0);
         if(u_kind>2.5&&u_kind<4.5)color+=vec3(.06,.065,.025)*shaft*(1.0-edge);
         gl_FragColor=vec4(clamp(color,0.0,1.0),1.0)*v_color;
      }
      """;
}
