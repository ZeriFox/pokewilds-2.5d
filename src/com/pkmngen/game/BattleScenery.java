package com.pkmngen.game;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.Locale;

/** Continuous illustrated floor with a shared CPU/GPU camera projection.
 * A continuous cylindrical panorama surrounds a projected floor through 360 degrees. */
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
   static final float ORBIT_PERIOD=48f;
   static float orbit(float time){return time*MathUtils.PI2/ORBIT_PERIOD;}
   /** Legacy coordinates are read-only inputs; z is the billboard perspective scale. */
   static Vector3 project(float x,float y,float yaw,Vector3 out) {
      float legacyZ=(y-70f)/(.55f-(y-70f)/260f),legacyDepth=1+legacyZ/260f;
      float wx=(x-80f)*legacyDepth*.82f,wz=legacyZ*.82f;
      float c=MathUtils.cos(yaw),s=MathUtils.sin(yaw);
      float rx=c*wx-s*wz,rz=s*wx+c*wz,scale=1/(1+rz/300f);
      return out.set(80+rx*scale,74+.32f*rz*scale,scale);
   }
   /** Particles remain vertical billboards above the same diagonal actor baseline. */
   static Vector3 projectEffect(float x,float y,float yaw,Vector3 out) {
      float t=MathUtils.clamp((x-40)/84,0,1),ground=48+40*t;
      project(x,ground,yaw,out);
      out.y+=(y-ground)*out.z*.68f;
      return out;
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
         batch.draw(arena,x,y,w,h);
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
      uniform sampler2D u_texture;uniform vec4 u_canvas;
      uniform float u_time,u_kind,u_yaw;
      float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
      float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
      // Fixed illustrations live on a cylinder: yaw changes azimuth only.
      // A narrow color crossfade closes the texture seam without reflecting or
      // stretching scenery. There is no time-dependent UV displacement.
      vec4 panoramaSample(float azimuth,float v){
         // Three upright illustrated sectors avoid stretching one flat image
         // around an entire circumference; every sector remains rigid.
         float u=fract(.5+azimuth*.477464829);
         vec4 sample=texture2D(u_texture,vec2(.015+.97*u,v));
         vec4 seam=.5*(texture2D(u_texture,vec2(.015,v))+texture2D(u_texture,vec2(.985,v)));
         float edge=smoothstep(0.0,.055,min(u,1.0-u));
         return mix(seam,sample,edge);
      }
      vec4 floorSample(vec2 world){
         vec2 f=fract(world/160.0+.5);
         // Fixed clearing patch, wrapped with a small material crossfade.
         vec2 uv=vec2(.35+.30*f.x,.52+.27*f.y);
         vec4 sample=texture2D(u_texture,uv);
         vec4 acrossX=.5*(texture2D(u_texture,vec2(.35,uv.y))+texture2D(u_texture,vec2(.65,uv.y)));
         sample=mix(acrossX,sample,smoothstep(0.0,.09,min(f.x,1.0-f.x)));
         vec4 acrossY=.5*(texture2D(u_texture,vec2(uv.x,.52))+texture2D(u_texture,vec2(uv.x,.79)));
         return mix(acrossY,sample,smoothstep(0.0,.09,min(f.y,1.0-f.y)));
      }
      vec2 floorWorld(vec2 p){
         float dy=min(p.y,155.0)-74.0;
         float rz=dy/(.32-dy/300.0),rx=(p.x-80.0)*(1.0+rz/300.0);
         float c=cos(u_yaw),s=sin(u_yaw);
         return vec2(c*rx+s*rz,-s*rx+c*rz);
      }
      void main(){
         vec2 uv=v_texCoords;
         vec2 point=vec2(u_canvas.x+uv.x*u_canvas.z,u_canvas.y+(1.0-uv.y)*u_canvas.w);
         vec2 world=floorWorld(point);
         float azimuth=atan((point.x-80.0)/145.0)+u_yaw;
         float panoramaV=clamp((150.0-point.y)/112.0,0.0,.62);
         vec4 panorama=panoramaSample(azimuth,panoramaV);
         vec4 ground=floorSample(world);
         float horizon=smoothstep(76.0,94.0,point.y);
         vec4 base=mix(ground,panorama,horizon);
         // Lighting and weather change color, never the camera/illustration UVs.
         vec2 sampleUv=mix(world/160.0,vec2(sin(azimuth),cos(azimuth))*2.0+panoramaV,horizon);
         float edge=smoothstep(.65,1.0,abs(uv.x-.5)*2.0);
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
