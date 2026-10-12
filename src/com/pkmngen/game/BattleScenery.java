package com.pkmngen.game;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import java.util.Locale;

/** A presentation-only panorama pass. Actors, plinths and the UI retain their original anchors. */
final class BattleScenery {
   private ShaderProgram shader;

   static int kind(String biome) {
      String name=biome==null?"":biome.toLowerCase(Locale.ROOT);
      if(name.contains("volcan"))return 7;
      if(name.contains("cave")||name.contains("dungeon"))return 6;
      if(name.contains("graveyard"))return 8;
      if(name.contains("snow")||name.contains("tundra"))return 4;
      if(name.contains("desert"))return 5;
      if(name.contains("beach"))return 1;
      if(name.contains("ocean")||name.equals("sea")||name.equals("sea1")||name.equals("water")||name.equals("water1"))return 2;
      return 3;
   }

   void draw(SpriteBatch batch,Texture arena,String biome,float time,float x,float y,float width,float height) {
      if(shader==null) {
         shader=new ShaderProgram(VERTEX,FRAGMENT);
         if(!shader.isCompiled()) {
            String log=shader.getLog();shader.dispose();shader=null;
            throw new IllegalStateException("Battle scenery shader: "+log);
         }
      }
      ShaderProgram previous=batch.getShader();
      try {
         batch.setShader(shader);
         shader.setUniformf("u_time",time);
         shader.setUniformf("u_size",arena.getWidth(),arena.getHeight());
         shader.setUniformf("u_kind",kind(biome));
         batch.draw(arena,x,y,width,height);
      } finally {
         // setShader flushes the scenery with its own uniforms before restoring the caller.
         batch.setShader(previous);
      }
   }

   void dispose() {
      if(shader!=null){shader.dispose();shader=null;}
   }

   private static final String VERTEX="""
      attribute vec4 a_position;
      attribute vec4 a_color;
      attribute vec2 a_texCoord0;
      uniform mat4 u_projTrans;
      varying vec4 v_color;
      varying vec2 v_texCoords;
      void main(){v_color=a_color;v_color.a*=255.0/254.0;v_texCoords=a_texCoord0;gl_Position=u_projTrans*a_position;}
      """;

   private static final String FRAGMENT="""
      #ifdef GL_ES
      precision mediump float;
      #endif
      varying vec4 v_color;
      varying vec2 v_texCoords;
      uniform sampler2D u_texture;
      uniform vec2 u_size;
      uniform float u_time;
      uniform float u_kind;
      float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
      float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
      float clouds(vec2 p){return .67*noise(p)+.33*noise(p*2.13+7.2);}
      float stage(vec2 p){
         float enemy=length((p-vec2(248.0,118.0))/vec2(66.0,20.0));
         float friendly=length((p-vec2(77.5,200.0))/vec2(91.0,25.0));
         return smoothstep(1.0,1.16,enemy)*smoothstep(1.0,1.16,friendly)*(1.0-smoothstep(160.0,177.0,p.y));
      }
      void main(){
         vec2 p=v_texCoords*u_size-(u_size-vec2(320.0,288.0))*.5;
         vec4 original=texture2D(u_texture,v_texCoords);
         float movable=stage(p);
         float far=1.0-smoothstep(52.0,103.0,p.y);
         // A slow elliptical camera path affects the distant panorama only.
         vec2 drift=vec2(sin(u_time*.16)*4.2,sin(u_time*.11)*1.25)*far;
         drift.x+=(p.x-160.0)*sin(u_time*.13)*.006*far;
         if(u_kind>2.5&&u_kind<4.5) {
            float foliage=smoothstep(.0,.11,original.g-original.r)*smoothstep(.02,.12,original.g-original.b);
            drift.x+=sin(u_time*1.05+p.x*.026)*1.15*far*foliage;
         }
         vec2 uv=clamp(v_texCoords+drift/u_size,.5/u_size,1.0-.5/u_size);
         vec3 color=texture2D(u_texture,uv).rgb;
         float n=clouds(vec2(p.x*.011-u_time*.023,p.y*.009+u_time*.004));
         if(u_kind<5.5) {
            // Broad cloud shadows and warm moving light, confined to the scenery.
            color*=.90+.14*smoothstep(.22,.79,n);
            float sky=(1.0-smoothstep(30.0,79.0,p.y));
            float veil=smoothstep(.46,.76,clouds(vec2(p.x*.017-u_time*.025,p.y*.024)))*sky;
            color=mix(color,vec3(.89,.95,.92),veil*.19);
         }
         if(u_kind<2.5) {
            float shore=143.0+7.0*sin(p.x/43.0);
            float ocean=smoothstep(75.0,79.0,p.y)*(1.0-smoothstep(shore-1.0,shore+1.0,p.y));
            float depth=clamp((p.y-76.0)/68.0,0.0,1.0);
            float wave=sin(p.y*.31-u_time*1.7+sin(p.x*.026+u_time*.27)*1.7);
            float cross=sin(p.x*.056+p.y*.23+u_time*.92);
            color+=ocean*(vec3(.045,.105,.14)*wave+vec3(.025,.055,.06)*cross);
            float crest=pow(max(0.0,wave*.72+cross*.28),8.0);
            color=mix(color,vec3(.65,.91,.94),ocean*crest*(.16+.16*depth));
            float wash=exp(-pow((p.y-shore+2.1+sin(u_time*1.5+p.x*.037)*2.0)/1.5,2.0));
            color=mix(color,vec3(.79,.96,.93),wash*.53);
         } else if(u_kind>6.5&&u_kind<7.5) {
            float center=143.0+7.0*sin(p.x/43.0);
            float lava=smoothstep(center-1.0,center+1.0,p.y)*(1.0-smoothstep(center+10.0,center+12.0,p.y));
            float pulse=.5+.5*sin(u_time*1.3+p.x*.035+sin(p.y*.13));
            color+=lava*vec3(.23,.11,.015)*pulse;
            color+=vec3(.09,.027,.004)*(.35+.65*pulse)*exp(-abs(p.y-center-5.0)*.05);
            float heat=sin(p.x*.09+u_time*.9)*sin(p.y*.06-u_time*.5);
            color+=vec3(.035,.009,0.0)*heat*far;
         } else if(u_kind>7.5) {
            float mist=clouds(vec2(p.x*.025-u_time*.038,p.y*.046+u_time*.013));
            color=mix(color,vec3(.63,.74,.70),smoothstep(.31,.82,mist)*.23);
         } else if(u_kind>5.5) {
            color*=.93+.08*n;
            float mist=noise(vec2(p.x*.025+u_time*.017,p.y*.07));
            color+=vec3(.025,.042,.065)*mist;
         }
         gl_FragColor=vec4(mix(original.rgb,clamp(color,0.0,1.0),movable),original.a)*v_color;
      }
      """;
}
