package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.IdentityHashMap;
import java.util.Locale;

/** Presentation only: original battle actions own input, rules and frame timing. */
public final class JohtoBattleRenderer {
   private final IdentityHashMap<Action, Integer> transitionLengths = new IdentityHashMap<>();
   private final Color tint = new Color();
   private final Color oldColor = new Color();
   private Texture worldSnapshot;
   private Texture arena;
   private String arenaBiome;
   private SpriteBatch snapshotBatch;
   private int snapshotWidth, snapshotHeight;
   private long frame, lastBattleFrame = -1, lastTransitionFrame = -1;
   private long battleFrames, transitionFrames;
   private long snapshotFrames;
   private String phase = "none";
   private boolean hasSnapshot;

   public static JohtoBattleRenderer get(Game game) { return game.johtoBattleRenderer; }

   /** Called after the world pass, before the existing UI SpriteBatch begins. */
   public void prepareFrame(Game game, boolean worldRendered) {
      PmdBattleSprites.beginFrame();
      frame++;
      phase = "none";
      for (Action action : game.actionStack) {
         if (action instanceof BattleIntro) phase = "intro";
         else if (action instanceof EnterBuilding || action instanceof EscapeRope) phase = "travel";
         else if (action instanceof BattleIntroAnim1 || isSpecialIntro(action)) phase = "wipe";
         else if (action instanceof BattleFadeOut) phase = "out";
         else if (action instanceof FadeAnim && phase.equals("none")) phase = "fade";
         else if ((action instanceof DrawWhiteScreen || action instanceof DrawItemMenu.Intro
            || action instanceof DrawPokemonMenu.Intro) && phase.equals("none")) phase = "menu-fade";
      }
      if (worldRendered && (phase.equals("none") || phase.equals("travel"))) captureWorld();
      // The original simulation still renders its map before this point. Cover
      // that image throughout transitions, including their final action frame.
      if (!phase.equals("none")) {
         drawWorldSnapshot();
         countTransition();
      }
      transitionLengths.keySet().removeIf(action -> !game.actionStack.contains(action));
   }

   private static boolean isSpecialIntro(Action action) {
      return action != null && action.getClass().getSimpleName().equals("BattleIntro1");
   }

   private void captureWorld() {
      int width = Gdx.graphics.getBackBufferWidth();
      int height = Gdx.graphics.getBackBufferHeight();
      if (width <= 0 || height <= 0) return;
      if (worldSnapshot == null || snapshotWidth != width || snapshotHeight != height) {
         if (worldSnapshot != null) worldSnapshot.dispose();
         worldSnapshot = new Texture(width, height, Pixmap.Format.RGBA8888);
         worldSnapshot.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
         snapshotWidth = width;
         snapshotHeight = height;
      }
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      worldSnapshot.bind();
      Gdx.gl.glCopyTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
      hasSnapshot = true;
   }

   private void drawWorldSnapshot() {
      if (snapshotBatch == null) snapshotBatch = new SpriteBatch();
      int width = Gdx.graphics.getBackBufferWidth();
      int height = Gdx.graphics.getBackBufferHeight();
      Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
      Gdx.gl.glDisable(GL20.GL_CULL_FACE);
      snapshotBatch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, width, height));
      snapshotBatch.begin();
      if (hasSnapshot) {
         snapshotBatch.setColor(Color.WHITE);
         snapshotBatch.draw(worldSnapshot, 0, 0, width, height, 0, 0, snapshotWidth, snapshotHeight, false, true);
         snapshotFrames++;
      } else {
         // An encounter opened before the first supported world frame still has
         // a coherent opaque transition, rather than exposing the classic map.
         ensureArena("grassland");
         snapshotBatch.setColor(Color.WHITE);
         snapshotBatch.draw(arena, 0, 0, width, height);
      }
      snapshotBatch.end();
   }

   public void drawBackground(Game game) {
      ModernUi ui = ModernUi.get(game);
      if (ui == null) return;
      ensureArena(game.map == null ? "grassland" : game.map.currBiome);
      oldColor.set(game.uiBatch.getColor());
      game.uiBatch.setColor(Color.WHITE);
      ui.rect(game, -320, -288, 800, 720, tint.set(0.78f, 0.86f, 0.82f, 1f));
      game.uiBatch.draw(arena, 0, 0, 160, 144);
      game.uiBatch.setColor(oldColor);
      if (lastBattleFrame != frame) { battleFrames++; lastBattleFrame = frame; }
   }

   /** Preserve the original enemy faint/slide clipping region with matching art. */
   public void drawEnemyMask(Game game) {
      if (arena == null) return;
      oldColor.set(game.uiBatch.getColor());
      game.uiBatch.setColor(Color.WHITE);
      game.uiBatch.draw(arena, 96, 48, 56, 40, 192, 112, 112, 80, false, false);
      game.uiBatch.setColor(oldColor);
   }

   public void drawNightTint(Game game) {
      ModernUi ui = ModernUi.get(game);
      if (ui != null) ui.rect(game, -320, -288, 800, 720, tint.set(0.08f, 0.16f, 0.27f, 0.16f));
   }

   public void drawFriendlyHealth(Game game, DrawFriendlyHealthGen2 health) {
      ModernUi ui = ModernUi.get(game);
      Pokemon pokemon = game.player.currPokemon;
      ui.panel(game, 82, 47, 75, 35);
      ui.fitText(game, pokemon.nickname, 87, 79, 6f, 47, ModernUi.INK);
      ui.text(game, "Lv " + pokemon.level, 135, 78, 3.6f, ModernUi.MUTED);
      String status = status(pokemon);
      ui.text(game, status.isEmpty() ? gender(pokemon) : status, 88, 71, 3.8f, status.isEmpty() ? ModernUi.MUTED : ModernUi.RED);
      ui.text(game, "HP", 87, 65.5f, 3.2f, ModernUi.MUTED);
      ui.bar(game, 99, 62.5f, 51, 3.4f, health.currHealth / 48f, healthColor(health.currHealth));
      ui.text(game, health.currHealthRemaining + " / " + pokemon.maxStats.get("hp"), 115, 59.5f, 4.2f, ModernUi.INK);
      ui.text(game, "EXP", 87, 52.5f, 2.8f, ModernUi.MUTED);
      ui.bar(game, 100, 49.5f, 50, 1.7f, DrawFriendlyHealthGen2.xpBarIndex / 68f, ModernUi.GOLD);
   }

   public void drawEnemyHealth(Game game, DrawEnemyHealthGen2 health) {
      ModernUi ui = ModernUi.get(game);
      Pokemon pokemon = game.battle.oppPokemon;
      float x = health.translateAmt.x;
      float y = health.translateAmt.y;
      ui.panel(game, 4 + x, 113 + y, 82, 26);
      ui.fitText(game, pokemon.nickname, 9 + x, 135 + y, 6f, 53, ModernUi.INK);
      ui.text(game, "Lv " + pokemon.level, 65 + x, 134 + y, 3.6f, ModernUi.MUTED);
      ui.text(game, "HP", 9 + x, 124 + y, 3.2f, ModernUi.MUTED);
      ui.bar(game, 21 + x, 120 + y, 57, 3.4f, health.currHealth / 48f, healthColor(health.currHealth));
      String status = status(pokemon);
      if (!pokemon.isGhost) ui.text(game, status.isEmpty() ? gender(pokemon) : status, 64 + x, 118 + y, 3.2f,
         status.isEmpty() ? ModernUi.MUTED : ModernUi.RED);
   }

   private static Color healthColor(int health) {
      return health > 23 ? ModernUi.ACCENT : health > 10 ? ModernUi.GOLD : ModernUi.RED;
   }

   private static String gender(Pokemon pokemon) {
      return "male".equals(pokemon.gender) ? "M" : "female".equals(pokemon.gender) ? "F" : "";
   }

   private static String status(Pokemon pokemon) {
      String value = pokemon.status;
      if ("poison".equals(value) || "toxic".equals(value)) return "PSN";
      if ("paralyze".equals(value)) return "PAR";
      if ("freeze".equals(value)) return "FRZ";
      if ("sleep".equals(value)) return "SLP";
      if ("burn".equals(value)) return "BRN";
      return "";
   }

   public void drawCommandMenu(Game game, String selected, boolean safari) {
      ModernUi ui = ModernUi.get(game);
      ui.panel(game, 2, 2, 156, 39);
      if (safari) {
         ui.row(game, "SAFARI BALL", 6, 22, 72, 14, "tl".equals(selected));
         ui.row(game, "BAIT", 82, 22, 72, 14, "tr".equals(selected));
         ui.row(game, "ROCK", 6, 6, 72, 14, "bl".equals(selected));
         ui.row(game, "RUN", 82, 6, 72, 14, "br".equals(selected));
      } else {
         ui.text(game, "Your next move", 8, 34, 4f, ModernUi.MUTED);
         ui.fitText(game, game.player.currPokemon.nickname, 8, 26, 5.5f, 57, ModernUi.INK);
         ui.row(game, "FIGHT", 68, 22, 42, 14, "tl".equals(selected));
         ui.row(game, "POKEMON", 112, 22, 42, 14, "tr".equals(selected));
         ui.row(game, "BAG", 68, 6, 42, 14, "bl".equals(selected));
         ui.row(game, "RUN", 112, 6, 42, 14, "br".equals(selected));
      }
   }

   /** Repaints a battle menu used as another menu's backdrop, without stepping it. */
   public boolean renderMenu(Game game, Action action) {
      if (action instanceof DrawBattleMenuNormal) {
         DrawBattleMenuNormal menu = (DrawBattleMenuNormal)action;
         if (!menu.disabled) drawCommandMenu(game, menu.curr, false);
         return true;
      }
      if (action instanceof DrawBattleMenuSafariZone) {
         drawCommandMenu(game, ((DrawBattleMenuSafariZone)action).curr, true);
         return true;
      }
      if (action instanceof DrawAttacksMenu) {
         drawAttackMenu(game, (DrawAttacksMenu)action);
         return true;
      }
      return false;
   }

   public void drawAttackMenu(Game game, DrawAttacksMenu menu) {
      ModernUi ui = ModernUi.get(game);
      float y = menu.offset.y;
      ui.panel(game, 2, y + 2, 156, 39);
      ui.text(game, menu.isSorting ? "SWAP MOVE" : "CHOOSE MOVE", 8, y + 36, 3.3f, ModernUi.MUTED);
      for (int i = 0; i < menu.pokemon.attacks.length; i++) {
         String move = menu.pokemon.attacks[i];
         if (move == null) move = "-";
         if (menu.isSorting && menu.sortingIndex == i) move = "* " + move;
         float rowY = y + 28 - i * 8;
         boolean selected = menu.cursorDelay >= 2 && DrawAttacksMenu.curr == i;
         ui.rect(game, 63, rowY, 90, 8, selected ? ModernUi.ACCENT : tint.set(1f, 1f, 1f, 0.35f));
         if (selected) ui.rect(game, 63, rowY, 1.3f, 8, ModernUi.GOLD);
         ui.fitText(game, move.toUpperCase(Locale.ROOT), 67, rowY + 7, 6f, 82, selected ? ModernUi.PAPER : ModernUi.INK);
      }
      Attack attack = game.battle.attacks.get(menu.pokemon.attacks[DrawAttacksMenu.curr]);
      if (attack != null) {
         String type = "curse_t".equalsIgnoreCase(attack.type) ? "???" : attack.type.toUpperCase(Locale.ROOT);
         ui.fitText(game, type, 8, y + 29, 4.3f, 50, ModernUi.ACCENT);
         if (Game.specialPhysicalSplitEnabled) ui.fitText(game, attack.category.name(), 8, y + 22.5f, 3.1f, 50, ModernUi.MUTED);
         ui.text(game, "PWR " + (attack.power > 0 ? attack.power : "-") + "   ACC " + (attack.accuracy > 0 ? attack.accuracy : "-"),
            8, y + 16.5f, 3.1f, ModernUi.INK);
      }
      ui.fitText(game, Keys.toString(InputProcessor.keyboardA) + " Select  " + Keys.toString(InputProcessor.keyboardB) + " Back",
         8, y + 9.5f, 2.7f, 50, ModernUi.MUTED);
   }

   public void drawIntro(Game game, Action action, int remaining) {
      ModernUi ui = ModernUi.get(game);
      float p = progress(action, remaining);
      float pulse = 0.10f + 0.13f * (0.5f + 0.5f * MathUtils.sin(p * MathUtils.PI * 6f));
      ui.rect(game, -320, -288, 800, 720, tint.set(0.08f, 0.22f, 0.26f, pulse));
      float band = MathUtils.clamp(p * 5f, 0f, 1f);
      ui.rect(game, 0, 60, 160 * band, 25, tint.set(0.08f, 0.24f, 0.28f, 0.94f));
      ui.rect(game, 0, 59, 160 * band, 1, ModernUi.GOLD);
      if (band > 0.9f) {
         ui.text(game, "WILD ENCOUNTER", 18, 80, 4f, ModernUi.PAPER);
         if (game.battle.oppPokemon != null) ui.fitText(game, game.battle.oppPokemon.nickname, 18, 72, 6f, 126, ModernUi.PAPER);
      }
      countTransition();
   }

   public void drawWipe(Game game, Action action, int remaining) {
      ModernUi ui = ModernUi.get(game);
      int total = total(action, remaining);
      float p = MathUtils.clamp((total - remaining + 1f) / Math.min(28f, total), 0f, 1f);
      ui.rect(game, -320, -288, 800, 288 + 72 * p, ModernUi.INK);
      ui.rect(game, -320, 144 - 72 * p, 800, 432, ModernUi.INK);
      ui.rect(game, 0, 72 * p, 160, 1, ModernUi.GOLD);
      ui.rect(game, 0, 143 - 72 * p, 160, 1, ModernUi.GOLD);
      countTransition();
   }

   public void drawFadeOut(Game game, Action action, int remaining) {
      ModernUi ui = ModernUi.get(game);
      float alpha = MathUtils.clamp(remaining / (float)total(action, remaining), 0f, 1f);
      ui.rect(game, -320, -288, 800, 720, tint.set(ModernUi.PAPER).mul(1f, 1f, 1f, alpha));
      countTransition();
   }

   public void drawFade(Game game, FadeAnim action) {
      int timer = action.timer;
      int slow = action.slow;
      float alpha = timer < 2 * slow ? 0f : timer < 4 * slow ? 0.25f : timer < 6 * slow ? 0.5f
         : timer < 12 * slow ? 1f : timer < 14 * slow ? 0.75f : timer < 16 * slow ? 0.5f : timer < 18 * slow ? 0.25f : 0f;
      if (alpha > 0f) ModernUi.get(game).rect(game, -320, -288, 800, 720, tint.set(ModernUi.PAPER).mul(1f, 1f, 1f, alpha));
      countTransition();
   }

   public void drawTravelFade(Game game, float alpha) {
      ModernUi.get(game).rect(game, -320, -288, 800, 720, tint.set(ModernUi.INK).mul(1f,1f,1f,alpha));
      countTransition();
   }

   private float progress(Action action, int remaining) { return 1f - remaining / (float)total(action, remaining); }
   private int total(Action action, int remaining) {
      Integer total = transitionLengths.get(action);
      if (total == null) { total = Math.max(1, remaining); transitionLengths.put(action, total); }
      return total;
   }
   private void countTransition() {
      if (lastTransitionFrame != frame) { transitionFrames++; lastTransitionFrame = frame; }
   }

   /** Crisp, restrained 2D landscape, using the same logical battle coordinates. */
   private void ensureArena(String biome) {
      if (biome == null) biome = "grassland";
      if (arena != null && biome.equals(arenaBiome)) return;
      if (arena != null) arena.dispose();
      arenaBiome = biome;
      Pixmap p = new Pixmap(320, 288, Pixmap.Format.RGBA8888);
      boolean sand = biome.contains("desert") || biome.contains("beach");
      boolean snow = biome.contains("tundra") || biome.contains("snow");
      boolean cave = biome.contains("cave") || biome.contains("volcan");
      boolean volcano = biome.contains("volcan");
      Color sky = new Color(cave ? 0.39f : 0.77f, cave ? 0.48f : 0.89f, cave ? 0.53f : 0.88f, 1f);
      if (volcano) sky.set(.24f,.12f,.19f,1);
      Color ground = sand ? new Color(0.84f,0.78f,0.57f,1f) : snow ? new Color(0.87f,0.92f,0.90f,1f)
         : cave ? new Color(0.55f,0.59f,0.56f,1f) : new Color(0.65f,0.76f,0.55f,1f);
      for (int y = 0; y < 288; y++) {
         float f = y / 288f;
         tint.set(sky).lerp(ground, MathUtils.clamp((f - 0.16f) * 2.3f, 0f, 1f));
         p.setColor(tint); p.drawLine(0,y,319,y);
      }
      p.setColor(tint.set(ground).mul(0.85f,0.92f,0.88f,1f));
      for (int x = -20; x < 340; x += 29) p.fillCircle(x, 56 + (x * 7 & 7), 27);
      p.setColor(tint.set(ground).mul(0.95f,1.0f,0.95f,1f));
      p.fillRectangle(0,72,320,216);
      // Sparse deterministic texture: no gameplay RNG or asset mutation.
      for (int i = 0; i < 210; i++) {
         int x = (i * 73 + 17) % 320;
         int y = 80 + (i * 43 + 5) % 198;
         p.setColor(tint.set(ground).mul(i % 2 == 0 ? 0.97f : 1.04f, i % 2 == 0 ? 0.98f : 1.03f, 1f, 1f));
         p.drawLine(x,y,Math.min(319,x+3),y);
      }
      paintBwGround(p, volcano ? "basalt" : cave ? "mountain" : snow ? "snow" : sand ? "sand" : "grass_light", sky);
      if (volcano) {
         p.setColor(.92f,.28f,.08f,1);
         for (int i=0;i<15;i++) {
            int x=(i*67+13)%320, y=90+(i*47)%95;
            p.drawLine(x,y,x+9,y+2); p.drawLine(x+9,y+2,x+13,y-1);
         }
      }
      ellipse(p, 190, 105, 117, 25, new Color(0.37f,0.49f,0.36f,1f));
      ellipse(p, 190, 101, 117, 23, snow ? new Color(0.94f,0.97f,0.94f,1f) : new Color(0.86f,0.84f,0.65f,1f));
      ellipse(p, -8, 181, 168, 36, new Color(0.40f,0.49f,0.35f,1f));
      ellipse(p, -8, 176, 168, 34, snow ? new Color(0.94f,0.97f,0.94f,1f) : new Color(0.88f,0.85f,0.67f,1f));
      arena = new Texture(p);
      arena.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
      p.dispose();
   }

   /** Tile only the verified environmental material, projected towards the horizon. */
   private void paintBwGround(Pixmap destination, String material, Color haze) {
      if (!Gdx.files.internal("visual/unova/world-atlas.png").exists()) return;
      JsonValue entry = new JsonReader().parse(Gdx.files.internal("visual/unova/world-atlas.json")).get(material);
      if (entry == null) return;
      Pixmap atlas = new Pixmap(Gdx.files.internal("visual/unova/world-atlas.png"));
      int ox=entry.getInt("x"), oy=entry.getInt("y"), width=entry.getInt("width"), height=entry.getInt("height");
      Color pixel = new Color();
      for (int y=73;y<288;y++) {
         float depth=(y-73)/215f, perspective=.35f+depth*.8f;
         int ty=Math.floorMod((int)(90f/perspective),height);
         for (int x=0;x<320;x++) {
            int tx=Math.floorMod((int)((x-160)/perspective),width);
            int rgba=atlas.getPixel(ox+tx,oy+ty);
            if ((rgba&255)<128) continue;
            Color.rgba8888ToColor(pixel,rgba);
            pixel.lerp(haze,(1-depth)*.27f);
            destination.drawPixel(x,y,Color.rgba8888(pixel));
         }
      }
      atlas.dispose();
   }

   private static void ellipse(Pixmap p, int x, int y, int width, int height, Color color) {
      p.setColor(color);
      for (int row = 0; row < height; row++) {
         float dy = (row + 0.5f) / height * 2f - 1f;
         float half = (float)Math.sqrt(Math.max(0f, 1f - dy * dy)) * width * 0.5f;
         p.drawLine(Math.round(x + width * 0.5f - half), y + row, Math.round(x + width * 0.5f + half), y + row);
      }
   }

   public long getBattleFrames() { return battleFrames; }
   public long getTransitionFrames() { return transitionFrames; }
   public long getSnapshotFrames() { return snapshotFrames; }
   public String getPhase() { return phase; }
   public void dispose() {
      if (worldSnapshot != null) worldSnapshot.dispose();
      if (arena != null) arena.dispose();
      if (snapshotBatch != null) snapshotBatch.dispose();
      transitionLengths.clear();
   }
}
