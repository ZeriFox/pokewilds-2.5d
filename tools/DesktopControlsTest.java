package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.utils.ScreenUtils;
import com.pkmngen.leaks.LeakTracer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Native GL summary rendering and controlled keyboard/mouse/gamepad polling. */
public final class DesktopControlsTest {
   public static void main(String[] args) {
      Game.leakTracer = LeakTracer.NoOp.INSTANCE;
      TestGame game = new TestGame();
      Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
      config.setInitialVisible(false); config.setWindowedMode(640, 576);
      config.setForegroundFPS(60); config.useVsync(false);
      new Lwjgl3Application(game, config);
      require(game.complete, "Native test did not complete");
      System.out.println("DESKTOP PASS: versioned settings migration/custom preservation, mouse press/hold/release, focus loss and release barrier, no desktop touch D-pad, WASD text entry, gamepad polling, full animated stats on all pages");
      System.exit(0);
   }

   private static void require(boolean result, String message) {
      if (!result) throw new IllegalStateException(message);
   }

   static class TestGame extends Game {
      Input nativeInput, testInput;
      final Set<Integer> keys = new HashSet<>(), buttons = new HashSet<>(), padButtons = new HashSet<>();
      final float[] axes = new float[4];
      InputProcessor processor = new InputProcessor();
      ModernPartyUi presentation = new ModernPartyUi();
      final ArrayList<DrawStatsScreen> summaries = new ArrayList<>();
      final Set<String> actorFrames = new HashSet<>();
      int frames;
      boolean complete;
      TestGame() { super(new String[0], 4); }

      public void create() {
         super.create();
         nativeInput = Gdx.input;
         testInput = (Input)Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class}, (proxy, method, args) -> {
            switch (method.getName()) {
               case "isKeyPressed": case "isKeyJustPressed": return keys.contains((Integer)args[0]);
               case "isButtonPressed": case "isButtonJustPressed": return buttons.contains((Integer)args[0]);
               case "isTouched": return !buttons.isEmpty();
               case "getX": case "getY": return 0;
               default: try { return method.invoke(nativeInput, args); } catch (InvocationTargetException e) { throw e.getCause(); }
            }
         });
         Gdx.input = testInput;
         actionStack.clear(); Game.gamepad = null;
         settings(); input(); typing(); gamepad(); controlScreen();
         keys.clear(); buttons.clear(); padButtons.clear(); Game.gamepad = null; actionStack.clear();
         processor.step(this);
         DrawPokemonMenu.allPokemon = new ArrayList<>();
         for (String name : new String[]{"machop", "gyarados", "sprigatito"}) {
            Pokemon p = new Pokemon(name, 28); p.previousOwner = player;
            DrawPokemonMenu.allPokemon.add(p);
            summaries.add(new DrawStatsScreen(this, p, null));
            checkAnimationBounds(p);
         }
         System.out.println("DESKTOP: input, migration, typing and animation bounds checks passed");
      }

      void settings() {
         HashMap<String, String> legacy = new HashMap<>();
         for (String[] pair : Game.defaultSettings) legacy.put(pair[0], pair[1]);
         legacy.put("keyboard-A", "Z"); legacy.put("keyboard-B", "X");
         legacy.put("keyboard-Up", "Up"); legacy.put("keyboard-Down", "Down");
         legacy.put("keyboard-Left", "Left"); legacy.put("keyboard-Right", "Right");
         legacy.put("keyboard-Start", "Enter"); legacy.put("keyboard-L", "C"); legacy.put("keyboard-R", "V");
         legacy.remove(DesktopControls.VERSION_KEY);
         require(DesktopControls.loadBindings(legacy), "Legacy preset was not migrated");
         require(legacy.get(DesktopControls.VERSION_KEY).equals("2"), "Migration decision was not versioned");
         require(InputProcessor.keyboardA == DesktopControls.MOUSE_LEFT && InputProcessor.keyboardB == DesktopControls.MOUSE_RIGHT
            && InputProcessor.keyboardUp == Keys.W && InputProcessor.keyboardLeft == Keys.A, "Default desktop preset incorrect");
         require(!DesktopControls.loadBindings(legacy), "Migration must be idempotent");
         HashMap<String, String> custom = new HashMap<>(legacy);
         custom.put("keyboard-Up", "I"); custom.put("keyboard-A", "Space");
         HashMap<String, String> before = new HashMap<>(custom);
         require(!DesktopControls.loadBindings(custom) && before.equals(custom), "Custom binding was overwritten");
         require(InputProcessor.keyboardUp == Keys.I && InputProcessor.keyboardA == Keys.SPACE, "Custom keys were not applied");
         HashMap<String, String> oldCustom = new HashMap<>(custom);
         oldCustom.remove(DesktopControls.VERSION_KEY);
         require(DesktopControls.loadBindings(oldCustom), "Unversioned custom preset was not stamped");
         require(oldCustom.equals(custom), "Versioning an old custom preset changed its bindings");
         HashMap<String, String> explicitClassic = new HashMap<>(legacy);
         explicitClassic.put("keyboard-A", "Z"); explicitClassic.put("keyboard-B", "X");
         explicitClassic.put("keyboard-Up", "Up"); explicitClassic.put("keyboard-Down", "Down");
         explicitClassic.put("keyboard-Left", "Left"); explicitClassic.put("keyboard-Right", "Right");
         HashMap<String, String> explicitBefore = new HashMap<>(explicitClassic);
         require(!DesktopControls.loadBindings(explicitClassic) && explicitClassic.equals(explicitBefore),
            "An explicitly chosen current classic preset was migrated again");
         require(InputProcessor.keyboardA == Keys.Z && InputProcessor.keyboardUp == Keys.UP, "Explicit current bindings ignored");
         explicitClassic.put(DesktopControls.VERSION_KEY, "99");
         require(!DesktopControls.loadBindings(explicitClassic) && explicitClassic.get(DesktopControls.VERSION_KEY).equals("99"),
            "A future settings version was downgraded");
         if (!Boolean.getBoolean("controls.sourceMode")) {
            // Exercise Game's actual parser and persistence on an isolated local fixture.
            legacy.put("keyboard-A", "Z"); legacy.put("keyboard-B", "X");
            legacy.put("keyboard-Up", "Up"); legacy.put("keyboard-Down", "Down");
            legacy.put("keyboard-Left", "Left"); legacy.put("keyboard-Right", "Right");
            legacy.remove(DesktopControls.VERSION_KEY);
            StringBuilder text = new StringBuilder();
            for (Map.Entry<String, String> pair : legacy.entrySet()) text.append(pair.getKey()).append('=').append(pair.getValue()).append('\n');
            com.badlogic.gdx.files.FileHandle file = Gdx.files.local("migration-fixture.txt");
            file.writeString(text.toString(), false);
            loadSettings(file);
            require(file.readString().contains("keyboard-A=MouseLeft") && file.readString().contains("controlsVersion=2") && InputProcessor.keyboardDown == Keys.S,
               "Game parser did not persist migrated preset");
            StringBuilder customText = new StringBuilder();
            for (Map.Entry<String, String> pair : custom.entrySet()) customText.append(pair.getKey()).append('=').append(pair.getValue()).append('\n');
            file.writeString(customText.toString(), false); loadSettings(file);
            require(file.readString().equals(customText.toString()) && InputProcessor.keyboardUp == Keys.I,
               "Game parser rewrote custom settings");
         }
         DesktopControls.loadBindings(legacy);
         require(InputProcessor.keyboardA == DesktopControls.MOUSE_LEFT, "Desktop preset not restored");
      }

      void input() {
         InputProcessor.acceptInput = true;
         // Put all virtual mobile controls underneath the simulated desktop click.
         for (com.badlogic.gdx.graphics.g2d.Sprite sprite : new com.badlogic.gdx.graphics.g2d.Sprite[]{
            DrawMobileControls.upArrowSprite, DrawMobileControls.downArrowSprite, DrawMobileControls.leftArrowSprite,
            DrawMobileControls.rightArrowSprite, DrawMobileControls.aSprite, DrawMobileControls.bSprite, DrawMobileControls.startSprite}) {
            sprite.setBounds(-1000, -1000, 2000, 2000);
         }
         processor.step(this);
         buttons.add(Buttons.LEFT); processor.step(this);
         require(InputProcessor.aJustPressed && InputProcessor.aPressed, "Left mouse press missed");
         require(!InputProcessor.upPressed && !InputProcessor.downPressed && !InputProcessor.leftPressed && !InputProcessor.rightPressed
            && !InputProcessor.bPressed && !InputProcessor.startPressed, "Desktop click activated virtual touch controls");
         InputProcessor.aPressed = false; InputProcessor.aJustPressed = false;
         processor.step(this);
         require(InputProcessor.aPressed && !InputProcessor.aJustPressed, "Consumed flag generated a second click");
         buttons.add(Buttons.RIGHT); keys.add(Keys.W); processor.step(this);
         require(InputProcessor.bJustPressed && InputProcessor.bPressed && InputProcessor.upPressed, "Right mouse + W run state missed");
         for (int i = 0; i < 30; i++) { processor.step(this); require(InputProcessor.bPressed && !InputProcessor.bJustPressed, "Held right mouse repeated cancel"); }
         buttons.remove(Buttons.RIGHT); processor.step(this);
         require(InputProcessor.bJustReleased && !InputProcessor.bPressed && InputProcessor.aPressed, "Independent mouse release failed");
         buttons.clear(); keys.clear(); processor.step(this);
         require(InputProcessor.aJustReleased && !InputProcessor.aPressed && !InputProcessor.upPressed, "Release left stale input");
         buttons.add(Buttons.LEFT); InputProcessor.acceptInput = false; processor.step(this);
         require(!InputProcessor.aPressed && !InputProcessor.aJustPressed, "Input suspension kept pressed state");
         InputProcessor.acceptInput = true; processor.step(this);
         require(InputProcessor.aPressed && !InputProcessor.aJustPressed, "Held button retriggered after suspension");
         buttons.clear(); processor.step(this); buttons.add(Buttons.LEFT); processor.step(this);
         require(InputProcessor.aJustPressed, "New click after release missed");
         buttons.clear(); processor.step(this);
         for (int key : new int[]{Keys.W, Keys.S, Keys.A, Keys.D}) {
            keys.add(key); processor.step(this);
            require(key == Keys.W ? InputProcessor.upJustPressed : key == Keys.S ? InputProcessor.downJustPressed
               : key == Keys.A ? InputProcessor.leftJustPressed : InputProcessor.rightJustPressed, "WASD direction failed: " + key);
            keys.clear(); processor.step(this);
         }
      }

      void typing() {
         Pokemon pokemon = new Pokemon("eevee", 6);
         Pokemon.SetNickname nickname = pokemon.new SetNickname(null);
         nickname.text.clear(); actionStack.add(nickname);
         uiBatch.begin();
         for (int key : new int[]{Keys.W, Keys.A, Keys.S, Keys.D}) {
            keys.add(key); processor.step(this);
            require(!InputProcessor.upPressed && !InputProcessor.downPressed && !InputProcessor.leftPressed && !InputProcessor.rightPressed,
               "Typing nickname activated WASD movement");
            nickname.step(this); keys.clear(); processor.step(this);
         }
         uiBatch.end();
         require(nickname.text.toString().equals("[w, a, s, d]"), "Nickname did not retain typed WASD: " + nickname.text);
         actionStack.clear();
         DrawSetupMenu setup = new DrawSetupMenu(this, null); actionStack.add(setup);
         setup.localHostJoinIndex = 0; setup.newLoadIndex = 0;
         for (int row : new int[]{2, 4}) {
            DrawSetupMenu.currIndex = row;
            for (int key : new int[]{Keys.W, Keys.A, Keys.S, Keys.D}) {
               keys.add(key); processor.step(this);
               require(!InputProcessor.upPressed && !InputProcessor.downPressed && !InputProcessor.leftPressed && !InputProcessor.rightPressed,
                  "Typing setup activated navigation");
               keys.clear(); processor.step(this);
            }
            keys.add(Keys.DOWN); processor.step(this);
            require(InputProcessor.downJustPressed, "Arrow navigation out of text field is unavailable");
            keys.clear(); processor.step(this);
         }
         setup.newLoadIndex = 1; DrawSetupMenu.currIndex = 1;
         keys.add(Keys.A); processor.step(this);
         require(InputProcessor.leftJustPressed, "Load setup selector was mistaken for a text field");
         keys.clear(); processor.step(this);
         actionStack.clear();
      }

      void gamepad() {
         ControllerMapping mapping = new ControllerMapping(0,1,2,3,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15) {};
         Game.gamepad = (Controller)Proxy.newProxyInstance(Controller.class.getClassLoader(), new Class<?>[]{Controller.class}, (proxy, method, args) -> {
            switch (method.getName()) {
               case "getMapping": return mapping;
               case "getButton": return padButtons.contains((Integer)args[0]);
               case "getAxis": return axes[(Integer)args[0]];
               case "isConnected": return true;
               default: throw new UnsupportedOperationException(method.getName());
            }
         });
         InputProcessor.gamepadA1 = mapping.buttonA; InputProcessor.gamepadA2 = mapping.buttonX;
         InputProcessor.gamepadB1 = mapping.buttonB; InputProcessor.gamepadB2 = mapping.buttonY;
         padButtons.add(mapping.buttonA); axes[0] = .9f; processor.step(this);
         require(InputProcessor.aJustPressed && InputProcessor.rightPressed, "Gamepad button / stick regressed");
         buttons.add(Buttons.LEFT); processor.step(this);
         require(!InputProcessor.aJustPressed, "Mouse + gamepad generated duplicate confirm");
         padButtons.clear(); axes[0] = 0; buttons.clear(); processor.step(this);
         require(!InputProcessor.aPressed && !InputProcessor.rightPressed, "Gamepad release regressed");
         focusLoss(mapping);
         Game.gamepad = null;
      }

      void focusLoss(ControllerMapping mapping) {
         buttons.add(Buttons.LEFT); buttons.add(Buttons.RIGHT); keys.add(Keys.W);
         padButtons.add(mapping.buttonA); axes[0] = .9f; processor.step(this);
         require(InputProcessor.aPressed && InputProcessor.bPressed && InputProcessor.upPressed && InputProcessor.rightPressed,
            "Focus fixture did not hold keyboard/mouse/controller");
         DesktopControls.focusChanged(false);
         require(!InputProcessor.aPressed && !InputProcessor.bPressed && !InputProcessor.upPressed && !InputProcessor.rightPressed
            && InputProcessor.aJustReleased && InputProcessor.bJustReleased, "Focus loss did not immediately release input");
         processor.step(this);
         require(!InputProcessor.aPressed && !InputProcessor.aJustPressed && !InputProcessor.bPressed && !InputProcessor.upPressed,
            "Unfocused input activated gameplay");
         DesktopControls.focusChanged(true); processor.step(this);
         require(!InputProcessor.aPressed && !InputProcessor.aJustPressed && !InputProcessor.bPressed && !InputProcessor.upPressed
            && !InputProcessor.rightPressed, "Refocus reused a held input or manufactured a click");
         // Mouse release alone cannot re-arm Confirm while the controller also holds it.
         buttons.clear(); processor.step(this); buttons.add(Buttons.LEFT); processor.step(this);
         require(!InputProcessor.aPressed && !InputProcessor.aJustPressed, "Focus barrier ignored held controller confirm");
         buttons.clear(); padButtons.clear(); keys.clear(); axes[0] = 0; processor.step(this);
         buttons.add(Buttons.LEFT); keys.add(Keys.W); processor.step(this);
         require(InputProcessor.aJustPressed && InputProcessor.upJustPressed, "Fresh input after focus release was lost");
         buttons.clear(); keys.clear(); processor.step(this);
         if (!Boolean.getBoolean("controls.sourceMode")) {
            buttons.add(Buttons.RIGHT); processor.step(this);
            pause(); require(!InputProcessor.bPressed, "Game.pause kept a held action");
            resume(); processor.step(this);
            require(!InputProcessor.bPressed && !InputProcessor.bJustPressed, "Game.resume generated a phantom cancel");
            buttons.clear(); processor.step(this);
         }
         System.out.println("DESKTOP: focus loss releases all actions; refocus requires independent release before new input");
      }

      void controlScreen() {
         // The original step still runs under modern presentation; its bitmap alphabet lacks '/'.
         DrawControls controls = new DrawControls();
         uiBatch.begin(); controls.step(this); presentation.render(modernUi, this, controls); uiBatch.end();
         int previous = InputProcessor.keyboardUp;
         InputProcessor.keyboardUp = Keys.SLASH;
         uiBatch.begin(); controls.step(this); uiBatch.end();
         InputProcessor.keyboardUp = previous;
         require(DesktopControls.hints("Press Z; pressing Z; holding X").equals("Press LMB; pressing LMB; holding RMB"),
            "Field hints do not reflect current mouse bindings");
         System.out.println("DESKTOP: original controls step and modern overlay support configured labels");
      }

      void checkAnimationBounds(Pokemon p) {
         PmdPokemonSprites sprites = PmdPokemonSprites.get();
         PmdPokemonSprites.AnimationBounds bounds = sprites.bounds(p, "down", "Idle");
         require(bounds != null && bounds.width > 0 && bounds.height > 0, "Missing summary bounds: " + p.specie.name);
         float scale = Math.min(52 / bounds.width, 64 / bounds.height);
         float originX = 10 + (52 - bounds.width * scale) / 2 - bounds.minX * scale;
         float originY = 23 + (64 - bounds.height * scale) / 2 - bounds.minY * scale;
         Set<String> different = new HashSet<>();
         for (int tick = 0; tick < 600; tick++) {
            PmdPokemonSprites.Frame frame = sprites.frame(p, "down", "Idle", tick / 60f);
            require(frame != null, "Missing animated full actor");
            float x = originX - frame.anchorX * scale, y = originY - frame.anchorY * scale;
            require(x >= 9.999f && y >= 22.999f && x + frame.width * scale <= 62.001f && y + frame.height * scale <= 87.001f,
               "Summary actor clips: " + p.specie.name + " at " + tick);
            different.add(frame.region.getRegionX() + ":" + frame.region.getRegionY());
         }
         require(different.size() > 1, "Test species has no animation: " + p.specie.name);
         System.out.println("DESKTOP: " + p.specie.name + " animation fits entire 52x64 box, " + different.size() + " frames");
      }

      public void render() {
         Gdx.input = testInput;
         int specimen = frames / 90, page = frames / 30 % 3;
         if (specimen >= summaries.size()) { complete = true; Gdx.app.exit(); return; }
         DrawStatsScreen menu = summaries.get(specimen); menu.currIndex = page;
         ScreenUtils.clear(.1f, .1f, .1f, 1);
         uiBatch.getProjectionMatrix().setToOrtho2D(0, 0, 160, 144);
         uiBatch.begin(); presentation.render(modernUi, this, menu); uiBatch.end();
         if (frames % 30 == 20) screenshot("summary-" + menu.pokemon.specie.name + "-" + page + ".png");
         frames++;
      }

      void screenshot(String name) {
         int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
         byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, false);
         BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
         for (int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int i = (y*w+x)*4; image.setRGB(x,h-1-y,(pixels[i]&255)<<16|(pixels[i+1]&255)<<8|(pixels[i+2]&255));
         }
         try { ImageIO.write(image, "png", new File(name)); } catch (Exception e) { throw new IllegalStateException(e); }
      }
   }
}
