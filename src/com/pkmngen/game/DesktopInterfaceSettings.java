package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/** Local interface preferences. Never writes a game save or rewrites settings.txt. */
final class DesktopInterfaceSettings {
   static final String FILE = "interface-settings.json";
   static final String[] ACTIONS = {"Move up", "Move down", "Move left", "Move right", "Confirm / interact", "Back / run", "Menu / sort", "Previous tab", "Next tab"};
   int[] bindings = currentBindings();
   boolean reducedMotion, openingScene = true, ambient = true, classicCast;
   String warning = "";

   static int[] currentBindings() { return new int[]{InputProcessor.keyboardUp, InputProcessor.keyboardDown,
      InputProcessor.keyboardLeft, InputProcessor.keyboardRight, InputProcessor.keyboardA, InputProcessor.keyboardB,
      InputProcessor.keyboardStart, InputProcessor.keyboardL, InputProcessor.keyboardR}; }
   static int[] defaults() { return new int[]{Keys.W,Keys.S,Keys.A,Keys.D,DesktopControls.MOUSE_LEFT,
      DesktopControls.MOUSE_RIGHT,Keys.ENTER,Keys.C,Keys.V}; }
   DesktopInterfaceSettings copy() {
      DesktopInterfaceSettings c = new DesktopInterfaceSettings(); c.bindings=bindings.clone();
      c.classicCast=classicCast;c.reducedMotion=reducedMotion; c.openingScene=openingScene; c.ambient=ambient; return c;
   }
   void apply() {
      InputProcessor.keyboardUp=bindings[0];InputProcessor.keyboardDown=bindings[1];
      InputProcessor.keyboardLeft=bindings[2];InputProcessor.keyboardRight=bindings[3];
      InputProcessor.keyboardA=bindings[4];InputProcessor.keyboardB=bindings[5];
      InputProcessor.keyboardStart=bindings[6];InputProcessor.keyboardL=bindings[7];InputProcessor.keyboardR=bindings[8];
   }
   static boolean allowed(int key) {
      return key==DesktopControls.MOUSE_LEFT || key==DesktopControls.MOUSE_RIGHT ||
         key>0 && key<=Keys.MAX_KEYCODE && key!=Keys.ESCAPE && key!=Keys.TAB && key!=Keys.F1
         && key!=Keys.SHIFT_LEFT && key!=Keys.SHIFT_RIGHT && key!=Keys.CONTROL_LEFT && key!=Keys.CONTROL_RIGHT
         && key!=Keys.ALT_LEFT && key!=Keys.ALT_RIGHT && key!=Keys.SYM && Keys.toString(key)!=null;
   }
   static boolean allowedFor(int action,int key) {
      if(!allowed(key))return false;
      // These are permanent menu escape routes, not rebindable second actions.
      if(key==Keys.UP||key==Keys.DOWN||key==Keys.LEFT||key==Keys.RIGHT)
         return action<4 && key==new int[]{Keys.UP,Keys.DOWN,Keys.LEFT,Keys.RIGHT}[action];
      if(key==Keys.ENTER||key==Keys.NUMPAD_ENTER)return action==6;
      return true;
   }
   String assign(int action,int key) {
      if(action<0||action>=bindings.length||!allowedFor(action,key))return "Reserved menu key. Esc cancels; F1 opens settings.";
      for(int i=0;i<bindings.length;i++)if(i!=action&&bindings[i]==key)return "Already used: "+ACTIONS[i];
      bindings[action]=key;return "";
   }
   void load() {
      if(!Gdx.files.local(FILE).exists())return;
      try {
         JsonValue root=new JsonReader().parse(Gdx.files.local(FILE));
         if(root.getInt("schema",0)!=1)throw new IllegalArgumentException("Unsupported interface settings version");
         int[] next=root.get("bindings").asIntArray();
         if(next.length!=ACTIONS.length)throw new IllegalArgumentException("Incomplete bindings");
         for(int i=0;i<next.length;i++) {
            if(!allowedFor(i,next[i]))throw new IllegalArgumentException("Reserved binding");
            for(int j=0;j<i;j++)if(next[j]==next[i])throw new IllegalArgumentException("Duplicate bindings");
         }
         boolean motion=root.getBoolean("reducedMotion",false),intro=root.getBoolean("openingScene",true),fx=root.getBoolean("ambientAccents",true);
         classicCast=root.getBoolean("classicTrainerCast",false);bindings=next;reducedMotion=motion;openingScene=intro;ambient=fx;apply();
      } catch(RuntimeException e) {
         warning="Invalid interface settings: existing controls retained.";
         Gdx.app.error("InterfaceSettings",warning,e);
      }
   }
   void save() throws java.io.IOException {
      JsonValue root=new JsonValue(JsonValue.ValueType.object),values=new JsonValue(JsonValue.ValueType.array);
      root.addChild("schema",new JsonValue(1));for(int key:bindings)values.addChild(new JsonValue(key));
      root.addChild("bindings",values);root.addChild("reducedMotion",new JsonValue(reducedMotion));
      root.addChild("classicTrainerCast",new JsonValue(classicCast));root.addChild("openingScene",new JsonValue(openingScene));root.addChild("ambientAccents",new JsonValue(ambient));
      Path path=Gdx.files.local(FILE).file().toPath().toAbsolutePath(),tmp=path.resolveSibling(FILE+".tmp");
      if(Files.isSymbolicLink(path)||Files.isSymbolicLink(tmp)||Files.isSymbolicLink(path.resolveSibling(FILE+".bak")))throw new java.io.IOException("Linked preferences are not overwritten");
      try {
         Files.writeString(tmp,root.toJson(JsonWriter.OutputType.json)+"\n",StandardCharsets.UTF_8);
         if(Files.exists(path))Files.copy(path,path.resolveSibling(FILE+".bak"),StandardCopyOption.REPLACE_EXISTING);
         try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
         catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
      } finally {Files.deleteIfExists(tmp);}
      warning="";
   }
}
