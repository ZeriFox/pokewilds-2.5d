package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Desktop bindings shared by input, settings migration and visible control hints. */
public final class DesktopControls {
   public static final int MOUSE_LEFT = -2;
   public static final int MOUSE_RIGHT = -3;
   private static final String[][] LEGACY = {
      {"keyboard-A", "Z"}, {"keyboard-B", "X"}, {"keyboard-Left", "Left"},
      {"keyboard-Right", "Right"}, {"keyboard-Up", "Up"}, {"keyboard-Down", "Down"},
      {"keyboard-Start", "Enter"}, {"keyboard-L", "C"}, {"keyboard-R", "V"}
   };
   private static final Pattern ACTION_HINT = Pattern.compile("(?i)\\b(press(?:ing)?|hold(?:ing)?|release|tap)(\\s+)([ZX])\\b");

   private DesktopControls() {}

   /** Only the complete untouched preset is migrated; a custom preset keeps every binding. */
   public static boolean loadBindings(Map<String, String> values) {
      boolean legacy = true;
      for (String[] entry : LEGACY) {
         String value = values.get(entry[0]);
         if (value == null || !entry[1].equalsIgnoreCase(value.trim())) legacy = false;
      }
      if (legacy) {
         values.put("keyboard-A", "MouseLeft"); values.put("keyboard-B", "MouseRight");
         values.put("keyboard-Left", "A"); values.put("keyboard-Right", "D");
         values.put("keyboard-Up", "W"); values.put("keyboard-Down", "S");
         System.out.println("[Controls] Migrated legacy arrow/Z/X preset to WASD and left/right mouse. Custom presets are preserved.");
      }
      InputProcessor.keyboardA = binding(values.get("keyboard-A"), MOUSE_LEFT);
      InputProcessor.keyboardB = binding(values.get("keyboard-B"), MOUSE_RIGHT);
      InputProcessor.keyboardLeft = binding(values.get("keyboard-Left"), Keys.A);
      InputProcessor.keyboardRight = binding(values.get("keyboard-Right"), Keys.D);
      InputProcessor.keyboardUp = binding(values.get("keyboard-Up"), Keys.W);
      InputProcessor.keyboardDown = binding(values.get("keyboard-Down"), Keys.S);
      InputProcessor.keyboardStart = binding(values.get("keyboard-Start"), Keys.ENTER);
      InputProcessor.keyboardL = binding(values.get("keyboard-L"), Keys.C);
      InputProcessor.keyboardR = binding(values.get("keyboard-R"), Keys.V);
      return legacy;
   }

   static int binding(String value, int fallback) {
      if (value == null || value.trim().isEmpty()) return fallback;
      String name = value.trim();
      if (name.equalsIgnoreCase("MouseLeft")) return MOUSE_LEFT;
      if (name.equalsIgnoreCase("MouseRight")) return MOUSE_RIGHT;
      int code = Keys.valueOf(name.length() == 1 ? name.toUpperCase(java.util.Locale.ROOT) : Game.capitalize(name));
      return code < 0 ? fallback : code;
   }

   static boolean pressed(int binding, boolean textEntry) {
      if (binding == MOUSE_LEFT || binding == MOUSE_RIGHT) {
         // Android/iOS report touches as mouse button 0; their virtual controls own those touches.
         ApplicationType platform = Gdx.app.getType();
         return platform != ApplicationType.Android && platform != ApplicationType.iOS
            && Gdx.input.isButtonPressed(binding == MOUSE_LEFT ? Buttons.LEFT : Buttons.RIGHT);
      }
      // Leave printable keys to LibGDX's keyTyped callback / nickname editor.
      if (textEntry && isPrintable(binding)) return false;
      return binding >= 0 && Gdx.input.isKeyPressed(binding);
   }

   private static boolean isPrintable(int key) {
      return key >= Keys.A && key <= Keys.Z || key >= Keys.NUM_0 && key <= Keys.NUM_9
         || key == Keys.SPACE || key == Keys.COMMA || key == Keys.PERIOD || key == Keys.SEMICOLON
         || key == Keys.APOSTROPHE || key == Keys.SLASH || key == Keys.BACKSLASH
         || key == Keys.MINUS || key == Keys.EQUALS || key == Keys.LEFT_BRACKET || key == Keys.RIGHT_BRACKET;
   }

   static boolean textEntry(Game game) {
      for (Action action : game.actionStack) {
         if (action instanceof Pokemon.SetNickname) {
            Pokemon.SetNickname nickname = (Pokemon.SetNickname)action;
            if (!nickname.disabled && !nickname.done) return true;
         }
         if (action instanceof DrawSetupMenu) {
            DrawSetupMenu setup = (DrawSetupMenu)action;
            int joinOffset = setup.localHostJoinIndex == 2 ? -1 : 0;
            int loadOffset = setup.newLoadIndex == 1 && setup.localHostJoinIndex != 2 ? -3 : 0;
            int hostOffset = setup.localHostJoinIndex == 1 && setup.newLoadIndex == 0 ? -2 : 0;
            int row = DrawSetupMenu.currIndex;
            // Load mode has no text fields: its compressed row 1 is the New/Load selector.
            if (loadOffset == 0 && (row == 2 || row == 4 + joinOffset + hostOffset)) return true;
         }
      }
      return false;
   }

   public static String label(int binding) {
      if (binding == MOUSE_LEFT) return "LMB";
      if (binding == MOUSE_RIGHT) return "RMB";
      // LibGDX calls BACKSPACE "Delete"; show the actual physical desktop key.
      if (binding == Keys.BACKSPACE) return "Backspace";
      if (binding == Keys.FORWARD_DEL) return "Del";
      if (binding == Keys.PAGE_UP) return "PgUp";
      if (binding == Keys.PAGE_DOWN) return "PgDn";
      if (binding == Keys.CAPS_LOCK) return "Caps";
      if (binding == Keys.SCROLL_LOCK) return "Scrl";
      String name = Keys.toString(binding);
      return name == null ? "?" : name;
   }

   public static String confirm() { return label(InputProcessor.keyboardA); }
   public static String back() { return label(InputProcessor.keyboardB); }
   public static String vertical() { return label(InputProcessor.keyboardUp) + "/" + label(InputProcessor.keyboardDown); }
   public static String horizontal() { return label(InputProcessor.keyboardLeft) + "/" + label(InputProcessor.keyboardRight); }
   public static String movement() {
      return label(InputProcessor.keyboardUp)+"/"+label(InputProcessor.keyboardLeft)+"/"
         +label(InputProcessor.keyboardDown)+"/"+label(InputProcessor.keyboardRight);
   }
   public static String menu() { return label(InputProcessor.keyboardStart); }

   /** Only pass authored help text here; names, nicknames and player-written signs are never rewritten. */
   public static String hints(String text) {
      if (text == null) return "";
      Matcher match = ACTION_HINT.matcher(text);
      StringBuffer result = new StringBuffer();
      while (match.find()) {
         String key = match.group(3).equalsIgnoreCase("Z") ? confirm() : back();
         match.appendReplacement(result, Matcher.quoteReplacement(match.group(1) + " " + key));
      }
      match.appendTail(result);
      return result.toString();
   }
   public static String authoredHint(Game game,String text) { return game.modernUi==null?text:hints(text); }
}
