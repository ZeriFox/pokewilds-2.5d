package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;

/** Opt-in packaging probe executed by the real launcher and delivered Game. */
public final class StartupVerification {
   private static final boolean ENABLED = Boolean.getBoolean("pokewilds.verifyStartup");
   private static int frames;

   private StartupVerification() { }

   public static void afterFrame(Game game, JohtoRenderer renderer) {
      if (!ENABLED || ++frames != 120) return;
      boolean menu = false;
      for (Action action : game.actionStack) if (action instanceof DrawSetupMenu) menu = true;
      if (!menu || game.modernUi == null || renderer == null)
         throw new IllegalStateException("Launcher did not reach the modern setup menu");
      Pixmap pixels = ScreenUtils.getFrameBufferPixmap(0, 0,
         Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      try {
         int first = pixels.getPixel(0, 0), changed = 0;
         for (int y = 0; y < pixels.getHeight(); y += 4)
            for (int x = 0; x < pixels.getWidth(); x += 4)
               if (pixels.getPixel(x, y) != first) changed++;
         if (changed < 100) throw new IllegalStateException("Launcher framebuffer is empty");
         PixmapIO.writePNG(Gdx.files.local("launcher-menu.png"), pixels, -1, true);
      } finally { pixels.dispose(); }
      System.out.println("POKEWILDS_LAUNCHER_VERIFIED frames=" + frames
         + " java.home=" + System.getProperty("java.home"));
      Gdx.app.exit();
   }
}
