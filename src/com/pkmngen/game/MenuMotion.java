package com.pkmngen.game;

import java.util.LinkedHashMap;
import java.util.Map;

/** Visual easing only. Layout and pointer hit boxes stay fixed throughout a transition. */
final class MenuMotion {
   private static final class Selection {float value,time;Selection(float now){time=now;}}
   private final Map<String,Selection> rows=new LinkedHashMap<>();
   private String screen="";
   private float started;
   void screen(String value,float now){if(!screen.equals(value)){screen=value;started=now;rows.clear();}}
   float reveal(float now,boolean reduced){return reduced?1:ease((now-started)/.38f);}
   float selection(float x,float y,boolean active,float now,boolean reduced){
      String key=Float.floatToIntBits(x)+":"+Float.floatToIntBits(y);
      Selection state=rows.get(key);
      if(state==null){if(rows.size()>=128)rows.clear();state=new Selection(now);state.value=active?.32f:0;rows.put(key,state);}
      float target=active?1:0;
      float dt=Math.max(0,Math.min(.1f,now-state.time));state.time=now;
      state.value=reduced?target:state.value+(target-state.value)*(1-(float)Math.exp(-dt*22));
      if(Math.abs(state.value-target)<.002f)state.value=target;
      return state.value;
   }
   static float ease(float value){float p=Math.max(0,Math.min(1,value));return 1-(1-p)*(1-p)*(1-p);}
}
