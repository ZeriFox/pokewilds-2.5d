package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.HashMap;
import java.util.Map;

/** Deterministic visual particles on the arena camera. No gameplay clock or RNG. */
final class ModernMoveEffects {
   private static final Map<String,Integer> durations=new HashMap<>();
   private Texture pixel;
   private ShaderProgram shader;
   private final Vector3 anchor=new Vector3();
   static String move(String name){return name.replace("_player_gsc","").replace("_enemy_gsc","").replace("_gsc","");}
   static int family(String animation){
      return switch(move(animation)){
         case "ember","flamethrower","fire_blast","fire_spin","flame_wheel","sacred_fire","heat_wave","overheat","lava_plume"->1;
         case "water_gun","bubble","bubblebeam","bubble_beam","hydro_pump","surf","whirlpool","water_pulse","aqua_jet","scald","rain_dance"->2;
         case "thunder","thundershock","thunder_shock","thunderbolt","spark","zap_cannon","thunder_punch","discharge","volt_switch","charge_beam"->3;
         case "razor_leaf","vine_whip","absorb","mega_drain","giga_drain","magical_leaf","leaf_storm","energy_ball","solar_beam","solarbeam"->4;
         case "ice_beam","icebeam","powder_snow","blizzard","icy_wind","ice_punch","hail","mist"->5;
         case "shadow_ball","night_shade","lick","shadow_claw","ominous_wind","haze"->6;
         case "psychic","confusion","psybeam","psywave","teleport","extrasensory","future_sight"->7;
         case "rock_throw","rock_slide","rock_tomb","stone_edge","sandstorm"->8;
         case "poison_sting","sludge","sludge_bomb","acid","toxic","poison_jab"->9;
         case "double_team","swift","sonicboom","sonic_boom","hyper_beam","tri_attack"->10;
         default->0;
      };
   }
   static int duration(String animation){
      Integer known=durations.get(animation);if(known!=null)return known;
      int n=0;
      while(n<4096&&Gdx.files.internal("attacks/"+animation+"/output/frame-"+String.format(java.util.Locale.ROOT,"%03d",n+1)+".png").exists())n++;
      n=Math.max(1,n);durations.put(animation,n);return n;
   }
   boolean draw(Game game,String animation,int frame,int duration){
      int family=family(animation);if(family==0)return false;
      ensure();float phase=MathUtils.clamp(frame/(float)Math.max(1,duration),0,1);
      float envelope=MathUtils.sin(MathUtils.PI*phase);boolean enemy=animation.contains("_enemy_gsc");
      String move=move(animation);boolean rain=move.equals("rain_dance")||move.equals("hail")||move.equals("sandstorm");
      SpriteBatch batch=game.uiBatch;ShaderProgram incoming=batch.getShader();float color=batch.getPackedColor();
      try{
         batch.setShader(shader);batch.setColor(Color.WHITE);shader.setUniformf("u_kind",family);shader.setUniformf("u_phase",phase);
         if(rain){
            float width=Math.max(160,144f*Gdx.graphics.getBackBufferWidth()/Math.max(1,Gdx.graphics.getBackBufferHeight()));
            float left=(160-width)*.5f;
            for(int i=0;i<44;i++){
               float x=left+width*(i*.618034f%1),y=50+(i*.381966f+phase*2)%1*105;
               particle(batch,x,y,move.equals("rain_dance")?.55f:1.4f,move.equals("rain_dance")?3f:1.4f,envelope*.65f,phase+i*.13f);
            }
         }else if(move.equals("surf")||move.equals("whirlpool")){
            // Translucent coherent crest travels over the actual floor. It never
            // copies, bends or inverts the panorama behind it.
            for(int i=0;i<40;i++){
               float x=-70+i*7.5f,y=56+phase*36+2*MathUtils.sin(i*.37f-phase*6);
               game.johtoBattleRenderer.projectScenePoint(game,x,y,anchor);
               particle(batch,anchor.x,anchor.y,4.3f*anchor.z,2.3f*anchor.z,envelope*.76f,phase+i*.08f);
            }
         }else{
            float sx=enemy?124:40,sy=enemy?88:48,tx=enemy?40:124,ty=enemy?48:88;
            boolean self=move.equals("teleport")||move.equals("double_team");
            for(int i=0;i<16;i++){
               float t=MathUtils.clamp(phase*1.8f-i*.045f,0,1);
               float arc=MathUtils.sin(t*MathUtils.PI)*(family==4?13:family==8?19:4);
               float x=self?tx+MathUtils.cos(i*MathUtils.PI2/16)*12:sx+(tx-sx)*t;
               float y=self?ty+9+MathUtils.sin(i*MathUtils.PI2/16+phase*6)*13:sy+(ty-sy)*t+8+arc;
               x+=MathUtils.sin(i*1.7f+phase*8)*(family==3?2:1.2f);
               game.johtoBattleRenderer.projectScenePoint(game,x,y,anchor);
               float radius=(family==6?4.8f:family==8?2.8f:2.5f)*anchor.z;
               particle(batch,anchor.x,anchor.y,radius,radius*(family==1?1.45f:1),envelope*.75f,phase+i*.16f);
            }
            if(phase>.4f){
               for(int i=0;i<10;i++){
                  float burst=MathUtils.clamp((phase-.4f)/.6f,0,1),angle=i*MathUtils.PI2/10;
                  game.johtoBattleRenderer.projectScenePoint(game,tx+MathUtils.cos(angle)*burst*17,ty+10+MathUtils.sin(angle)*burst*13,anchor);
                  particle(batch,anchor.x,anchor.y,1.6f*anchor.z,1.6f*anchor.z,envelope*.7f,phase+i*.2f);
               }
            }
         }
      }finally{batch.flush();batch.setShader(incoming);batch.setPackedColor(color);}
      return true;
   }
   private void particle(SpriteBatch batch,float x,float y,float rx,float ry,float alpha,float spin){
      shader.setUniformf("u_alpha",alpha);shader.setUniformf("u_spin",spin);
      batch.draw(pixel,x-rx,y-ry,rx*2,ry*2);batch.flush();
   }
   private void ensure(){
      if(pixel==null){Pixmap p=new Pixmap(1,1,Pixmap.Format.RGBA8888);p.setColor(Color.WHITE);p.fill();pixel=new Texture(p);p.dispose();}
      if(shader==null){shader=new ShaderProgram(VERTEX,FRAGMENT);if(!shader.isCompiled())throw new IllegalStateException("Modern move particles: "+shader.getLog());}
   }
   int shaderHandle(){return shader==null?0:shader.getHandle();}
   void dispose(){if(pixel!=null){pixel.dispose();pixel=null;}if(shader!=null){shader.dispose();shader=null;}}
   private static final String VERTEX="""
      attribute vec4 a_position;attribute vec4 a_color;attribute vec2 a_texCoord0;uniform mat4 u_projTrans;
      varying vec2 v_uv;void main(){v_uv=a_texCoord0;gl_Position=u_projTrans*a_position;}
      """;
   private static final String FRAGMENT="""
      #ifdef GL_ES
      precision mediump float;
      #endif
      varying vec2 v_uv;uniform float u_kind,u_alpha,u_phase,u_spin;
      void main(){
         vec2 p=v_uv*2.0-1.0;float r=length(p);vec3 c=vec3(.96,.86,.50);
         float shape=1.0-smoothstep(.56,.97,r),edge=smoothstep(.40,.64,r)*(1.0-smoothstep(.64,.87,r));
         if(u_kind<1.5){c=mix(vec3(1.0,.34,.08),vec3(1.0,.91,.48),1.0-r);shape*=1.0-smoothstep(.2,1.0,p.y+.25*sin(p.x*8.0+u_phase*15.0));}
         else if(u_kind<2.5){c=mix(vec3(.20,.65,.94),vec3(.85,.98,1.0),edge*.85);shape*=.8;}
         else if(u_kind<3.5){float bolt=abs(p.x-.17*sin(floor(p.y*5.0+u_spin)*3.0));shape=max(1.0-smoothstep(.10,.28,bolt),shape*.13);c=vec3(1.0,.90,.36);}
         else if(u_kind<4.5){float a=u_spin*3.0;vec2 q=mat2(cos(a),-sin(a),sin(a),cos(a))*p;shape=1.0-smoothstep(.62,.95,length(q*vec2(1.6,.85)));c=mix(vec3(.16,.54,.28),vec3(.69,.96,.35),smoothstep(-.4,.4,q.x));}
         else if(u_kind<5.5){shape=max(shape*.35,(1.0-smoothstep(.06,.2,min(abs(p.x),abs(p.y))))*(1.0-smoothstep(.6,.95,r)));c=vec3(.73,.96,1.0);}
         else if(u_kind<6.5){c=mix(vec3(.27,.12,.40),vec3(.71,.38,.94),edge);shape=1.0-smoothstep(.6,1.0,r);}
         else if(u_kind<7.5){shape=edge+.15*shape;c=vec3(.97,.49,.85);}
         else if(u_kind<8.5){float ridge=max(abs(p.x*.85+p.y*.28),abs(p.y*.8-p.x*.3));shape=1.0-smoothstep(.57,.82,ridge);c=mix(vec3(.42,.32,.23),vec3(.78,.69,.48),.5-p.y*.3);}
         else if(u_kind<9.5){c=mix(vec3(.51,.23,.70),vec3(.77,.60,.94),edge);}
         else{shape=max(shape*.22,(1.0-smoothstep(.07,.21,min(abs(p.x),abs(p.y))))*(1.0-smoothstep(.45,.97,r)));}
         gl_FragColor=vec4(c,clamp(shape*u_alpha,0.0,.85));
      }
      """;
}
