package com.pkmngen.game;

import com.badlogic.gdx.Input.Keys;
import java.util.ArrayList;

/** Explicit, transactional text focus: selection alone is never permission to type. */
final class MenuTextSession {
   enum Kind { NAME, SAVE, ADDRESS }
   private ArrayList<Character> destination;
   final StringBuilder draft=new StringBuilder();
   Kind kind;
   int cursor;
   String error="";
   boolean active(){return destination!=null;}
   void begin(ArrayList<Character> target,Kind type) {
      destination=target;kind=type;draft.setLength(0);for(char c:target)draft.append(c);
      cursor=draft.length();error="";
   }
   boolean typed(char c) {
      if(!active())return false;
      if(c<' '||c==127)return true; // deletion/Enter use keyDown, never twice via keyTyped
      int limit=kind==Kind.ADDRESS?15:11;
      boolean valid=kind==Kind.ADDRESS?c>='0'&&c<='9'||c=='.':Character.isLetterOrDigit(c)&&c<128;
      if(valid&&draft.length()<limit){draft.insert(cursor++,c);error="";}return true;
   }
   void key(int key) {
      if(key==Keys.LEFT)cursor=Math.max(0,cursor-1);
      else if(key==Keys.RIGHT)cursor=Math.min(draft.length(),cursor+1);
      else if(key==Keys.HOME)cursor=0;
      else if(key==Keys.END)cursor=draft.length();
      else if(key==Keys.BACKSPACE&&cursor>0)draft.deleteCharAt(--cursor);
      else if(key==Keys.FORWARD_DEL&&cursor<draft.length())draft.deleteCharAt(cursor);
   }
   boolean commit() {
      if(!active())return true;
      if(kind==Kind.ADDRESS&&!draft.isEmpty()) {
         String[] parts=draft.toString().split("\\.",-1);
         if(parts.length!=4){error="Enter four numbers, e.g. 127.0.0.1";return false;}
         for(String part:parts) {
            if(!part.matches("[0-9]{1,3}")||Integer.parseInt(part)>255){error="Each address number must be 0-255";return false;}
         }
      }
      destination.clear();for(int i=0;i<draft.length();i++)destination.add(draft.charAt(i));
      destination=null;return true;
   }
   void cancel(){destination=null;draft.setLength(0);error="";}
}
