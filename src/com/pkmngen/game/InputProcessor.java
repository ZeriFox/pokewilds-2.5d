package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Vector2;

class InputProcessor extends Action {
   public Action.Layer layer = Action.Layer.map_5000;
   public static boolean acceptInput = true;
   public static boolean upPressed = false;
   public static boolean downPressed = false;
   public static boolean leftPressed = false;
   public static boolean rightPressed = false;
   public static boolean aPressed = false;
   public static boolean bPressed = false;
   public static boolean startPressed = false;
   public static boolean upJustPressed = false;
   public static boolean downJustPressed = false;
   public static boolean leftJustPressed = false;
   public static boolean rightJustPressed = false;
   public static boolean aJustPressed = false;
   public static boolean bJustPressed = false;
   public static boolean startJustPressed = false;
   public static boolean lPressed = false;
   public static boolean lJustPressed = false;
   public static boolean rPressed = false;
   public static boolean rJustPressed = false;
   Vector2 touchLoc = new Vector2();
   public static int keyboardA = DesktopControls.MOUSE_LEFT;
   public static int keyboardB = DesktopControls.MOUSE_RIGHT;
   public static int keyboardLeft = Keys.A;
   public static int keyboardRight = Keys.D;
   public static int keyboardUp = Keys.W;
   public static int keyboardDown = Keys.S;
   public static int keyboardStart = 66;
   public static int keyboardL = 31;
   public static int keyboardR = 50;
   boolean gamepadA;
   boolean gamepadB;
   boolean gamepadLeft;
   boolean gamepadRight;
   boolean gamepadUp;
   boolean gamepadDown;
   boolean gamepadStart;
   boolean gamepadL;
   boolean gamepadR;
   float gamepadAxisLeftX;
   float gamepadAxisLeftY;
   float gamepadAxisRightX;
   float gamepadAxisRightY;
   public static double gamepadMinAxis = 0.3;
   public static int gamepadA1;
   public static int gamepadA2;
   public static int gamepadB1;
   public static int gamepadB2;

   private int previousHeld;
   private static boolean windowFocused = true;
   private static long focusGeneration;
   private long observedFocusGeneration;
   private int blockedUntilRelease;
   public static boolean aJustReleased, bJustReleased;

   InputProcessor() {
      super();
      observedFocusGeneration = focusGeneration;
      if (!windowFocused) blockedUntilRelease = 511;
   }

   static void setWindowFocused(boolean focused) {
      if (windowFocused == focused) return;
      windowFocused = focused;
      focusGeneration++;
      // Clear immediately, even while an unfocused window has stopped rendering.
      aJustReleased = aPressed;
      bJustReleased = bPressed;
      upPressed = downPressed = leftPressed = rightPressed = aPressed = bPressed = startPressed = lPressed = rPressed = false;
      upJustPressed = downJustPressed = leftJustPressed = rightJustPressed = aJustPressed = bJustPressed = startJustPressed = lJustPressed = rJustPressed = false;
   }

   @Override
   public void step(Game game) {
      if (observedFocusGeneration != focusGeneration) {
         observedFocusGeneration = focusGeneration;
         previousHeld = 0;
         // Neither the click which focuses the window nor a held controller/key
         // may act on the game. Each action becomes usable after its own release.
         blockedUntilRelease = 511;
      }
      if (!windowFocused) {
         updateState(0);
         return;
      }
      boolean mobile = Gdx.app.getType() == ApplicationType.Android || Gdx.app.getType() == ApplicationType.iOS;
      // Desktop mouse clicks must never hit the legacy virtual D-pad rectangles.
      if (mobile && Gdx.input.isTouched()) {
         float scaleX = 160.0F / game.currScreen.x;
         int offsetY = (int)((game.currScreen.y - 144.0F / scaleX) / 2.0F * scaleX);
         this.touchLoc.set(Gdx.input.getX() * scaleX, (game.currScreen.y - Gdx.input.getY()) * scaleX - offsetY);
      } else this.touchLoc.set(-1f, -1f);

      this.gamepadA = this.gamepadB = this.gamepadLeft = this.gamepadRight = this.gamepadUp = this.gamepadDown = this.gamepadStart = this.gamepadL = this.gamepadR = false;
      if (Game.gamepad != null) {
         if (Game.gamepad.getButton(gamepadA1) || Game.gamepad.getButton(gamepadA2)) {
            this.gamepadA = true;
         }

         if (Game.gamepad.getButton(gamepadB1) || Game.gamepad.getButton(gamepadB2)) {
            this.gamepadB = true;
         }

         if (Game.gamepad.getButton(Game.gamepad.getMapping().buttonStart)) {
            this.gamepadStart = true;
         }

         if (Game.gamepad.getButton(Game.gamepad.getMapping().buttonL1) || Game.gamepad.getButton(Game.gamepad.getMapping().buttonL2)) {
            this.gamepadL = true;
         }

         if (Game.gamepad.getButton(Game.gamepad.getMapping().buttonR1) || Game.gamepad.getButton(Game.gamepad.getMapping().buttonR2)) {
            this.gamepadR = true;
         }

         if (Game.gamepad.getButton(Game.gamepad.getMapping().buttonDpadLeft)) {
            this.gamepadLeft = true;
         }

         if (Game.gamepad.getButton(Game.gamepad.getMapping().buttonDpadRight)) {
            this.gamepadRight = true;
         }

         if (Game.gamepad.getButton(Game.gamepad.getMapping().buttonDpadUp)) {
            this.gamepadUp = true;
         }

         if (Game.gamepad.getButton(Game.gamepad.getMapping().buttonDpadDown)) {
            this.gamepadDown = true;
         }

         this.gamepadAxisLeftX = Game.gamepad.getAxis(Game.gamepad.getMapping().axisLeftX);
         this.gamepadAxisLeftY = Game.gamepad.getAxis(Game.gamepad.getMapping().axisLeftY);
         if (this.gamepadAxisLeftX > gamepadMinAxis || this.gamepadAxisLeftX < -gamepadMinAxis) {
            if (this.gamepadAxisLeftX > 0.0F) {
               this.gamepadRight = true;
            } else {
               this.gamepadLeft = true;
            }
         }

         if (this.gamepadAxisLeftY > gamepadMinAxis || this.gamepadAxisLeftY < -gamepadMinAxis) {
            if (this.gamepadAxisLeftY > 0.0F) {
               this.gamepadDown = true;
            } else {
               this.gamepadUp = true;
            }
         }

         this.gamepadAxisRightX = Game.gamepad.getAxis(Game.gamepad.getMapping().axisRightX);
         this.gamepadAxisRightY = Game.gamepad.getAxis(Game.gamepad.getMapping().axisRightY);
         if (this.gamepadAxisRightX > gamepadMinAxis || this.gamepadAxisRightX < -gamepadMinAxis) {
            if (this.gamepadAxisRightX > 0.0F) {
               this.gamepadRight = true;
            } else {
               this.gamepadLeft = true;
            }
         }

         if (this.gamepadAxisRightY > gamepadMinAxis || this.gamepadAxisRightY < -gamepadMinAxis) {
            if (this.gamepadAxisRightY > 0.0F) {
               this.gamepadDown = true;
            } else {
               this.gamepadUp = true;
            }
         }
      }

      boolean typing = DesktopControls.textEntry(game);
      int held = 0;
      if (DesktopControls.pressed(keyboardUp, typing) || this.gamepadUp || touch(DrawMobileControls.upArrowSprite)
         || typing && Gdx.input.isKeyPressed(Keys.UP)) held |= 1;
      if (DesktopControls.pressed(keyboardDown, typing) || this.gamepadDown || touch(DrawMobileControls.downArrowSprite)
         || typing && Gdx.input.isKeyPressed(Keys.DOWN)) held |= 2;
      if (DesktopControls.pressed(keyboardLeft, typing) || this.gamepadLeft || touch(DrawMobileControls.leftArrowSprite)
         || typing && Gdx.input.isKeyPressed(Keys.LEFT)) held |= 4;
      if (DesktopControls.pressed(keyboardRight, typing) || this.gamepadRight || touch(DrawMobileControls.rightArrowSprite)
         || typing && Gdx.input.isKeyPressed(Keys.RIGHT)) held |= 8;
      if (DesktopControls.pressed(keyboardA, typing) || this.gamepadA || touch(DrawMobileControls.aSprite)) held |= 16;
      if (DesktopControls.pressed(keyboardB, typing) || this.gamepadB || touch(DrawMobileControls.bSprite)) held |= 32;
      if (DesktopControls.pressed(keyboardStart, typing) || this.gamepadStart || touch(DrawMobileControls.startSprite)) held |= 64;
      if (DesktopControls.pressed(keyboardL, typing) || this.gamepadL) held |= 128;
      if (DesktopControls.pressed(keyboardR, typing) || this.gamepadR) held |= 256;
      blockedUntilRelease &= held;
      held &= ~blockedUntilRelease;
      updateState(held);
   }

   private boolean touch(Sprite sprite) {
      return touchLoc.x >= 0 && sprite != null && sprite.getBoundingRectangle().contains(touchLoc);
   }

   private void updateState(int held) {
      // Separate physical history from public flags: menus consume those flags.
      // Holding a button through a menu transition must not manufacture another click.
      int pressed = acceptInput ? held & ~previousHeld : 0;
      int released = previousHeld & ~held;
      previousHeld = held;
      if (!acceptInput) held = 0;
      upPressed = (held & 1) != 0; upJustPressed = (pressed & 1) != 0;
      downPressed = (held & 2) != 0; downJustPressed = (pressed & 2) != 0;
      leftPressed = (held & 4) != 0; leftJustPressed = (pressed & 4) != 0;
      rightPressed = (held & 8) != 0; rightJustPressed = (pressed & 8) != 0;
      aPressed = (held & 16) != 0; aJustPressed = (pressed & 16) != 0; aJustReleased = (released & 16) != 0;
      bPressed = (held & 32) != 0; bJustPressed = (pressed & 32) != 0; bJustReleased = (released & 32) != 0;
      startPressed = (held & 64) != 0; startJustPressed = (pressed & 64) != 0;
      lPressed = (held & 128) != 0; lJustPressed = (pressed & 128) != 0;
      rPressed = (held & 256) != 0; rJustPressed = (pressed & 256) != 0;
   }

   @Override
   public String getCamera() {
      return "map";
   }

   @Override
   public Action.Layer getLayer() {
      return this.layer;
   }
}
