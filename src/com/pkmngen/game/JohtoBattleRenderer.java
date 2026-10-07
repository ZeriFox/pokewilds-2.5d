package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.ArrayList;
import java.nio.IntBuffer;
import com.badlogic.gdx.utils.BufferUtils;

/** Presentation only: original battle actions own input, rules and frame timing. */
public final class JohtoBattleRenderer {
   private final IdentityHashMap<Action, Integer> transitionLengths = new IdentityHashMap<>();
   private final Color tint = new Color();
   private final Color oldColor = new Color();
   private Texture worldSnapshot;
   private Texture rippleSnapshot;
   private Texture arena;
   private String arenaBiome;
   private SpriteBatch snapshotBatch;
   private int snapshotWidth, snapshotHeight;
   private long frame, lastBattleFrame = -1, lastTransitionFrame = -1;
   private long battleFrames, transitionFrames;
   private long snapshotFrames;
   private String phase = "none";
   private boolean hasSnapshot;
   private boolean battleCanvas;
   private boolean projectionSaved;
   private final Matrix4 previousUiProjection = new Matrix4();
   private Texture overlayPixel;
   private int arenaWidth,arenaHeight;
   private float canvasWidth=160,canvasHeight=144;
   private final ArrayList<float[]> marginOverlays=new ArrayList<>();
   private final IntBuffer previousScissor=BufferUtils.newIntBuffer(4);
   private ShaderProgram arenaShader;

   public static JohtoBattleRenderer get(Game game) { return game.johtoBattleRenderer; }

   /** Called after the world pass, before the existing UI SpriteBatch begins. */
   public void prepareFrame(Game game, boolean worldRendered) {
      PmdBattleSprites.beginFrame();
      frame++;
      marginOverlays.clear();
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
      battleCanvas = phase.equals("intro") || phase.equals("wipe") || phase.equals("out")
         || game.player != null && game.player.dontDrawMapDuringBattle;
      for (Action action : game.actionStack) if (action instanceof DrawBattle) battleCanvas = true;
      if (worldRendered) captureWorld();
      // A cleanup can remove DrawBattle before the next world pass. Always
      // supply an opaque modern frame in that gap, even with no fade action.
      if (!worldRendered || !phase.equals("none")) {
         drawWorldSnapshot();
         countTransition();
      }
      if (battleCanvas) {
         previousUiProjection.set(game.uiBatch.getProjectionMatrix());
         projectionSaved = true;
         float aspect = Gdx.graphics.getBackBufferWidth() / (float)Math.max(1,Gdx.graphics.getBackBufferHeight());
         canvasWidth=Math.max(160f,144f*aspect);canvasHeight=Math.max(144f,160f/aspect);
         game.uiBatch.setProjectionMatrix(new Matrix4().setToOrtho2D((160-canvasWidth)*.5f,(144-canvasHeight)*.5f,canvasWidth,canvasHeight));
      }
      transitionLengths.keySet().removeIf(action -> !game.actionStack.contains(action));
   }

   /** After uiBatch.end(): contain sliding actors/effects and restore the world UI projection. */
   public void finishFrame(Game game) {
      if (battleCanvas && lastBattleFrame==frame) {
         if (snapshotBatch == null) snapshotBatch = new SpriteBatch();
         if (overlayPixel == null) {
            Pixmap pixel = new Pixmap(1,1,Pixmap.Format.RGBA8888);
            pixel.setColor(Color.WHITE); pixel.fill(); overlayPixel = new Texture(pixel); pixel.dispose();
         }
         int width=Gdx.graphics.getBackBufferWidth(), height=Gdx.graphics.getBackBufferHeight();
         float scale=Math.min(width/160f,height/144f);
         float left=(width-160*scale)*.5f, bottom=(height-144*scale)*.5f;
         Gdx.gl.glViewport(0,0,width,height);
         Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); Gdx.gl.glDisable(GL20.GL_CULL_FACE);
         // Use the exact same projection and UV interpolation as the arena pass;
         // cropping UVs separately can shift nearest-neighbour sampling by one texel.
         snapshotBatch.setProjectionMatrix(game.uiBatch.getProjectionMatrix());
         snapshotBatch.setShader(arenaShader);
         boolean scissorEnabled=Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
         previousScissor.clear();Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX,previousScissor);
         Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
         if (left>0) {
            paintMargin(0,0,(int)Math.floor(left),height);
            int right=(int)Math.ceil(width-left);paintMargin(right,0,width-right,height);
         }
         if (bottom>0) {
            paintMargin(0,0,width,(int)Math.floor(bottom));
            int top=(int)Math.ceil(height-bottom);paintMargin(0,top,width,height-top);
         }
         Gdx.gl.glScissor(previousScissor.get(0),previousScissor.get(1),previousScissor.get(2),previousScissor.get(3));
         if(!scissorEnabled)Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
         snapshotBatch.setShader(null);
      }
      if (projectionSaved) { game.uiBatch.setProjectionMatrix(previousUiProjection); projectionSaved=false; }
   }

   private void paintMargin(int x,int y,int w,int h) {
      if(w<=0||h<=0)return;
      Gdx.gl.glScissor(x,y,w,h);
      snapshotBatch.setColor(Color.WHITE);snapshotBatch.begin();
      snapshotBatch.draw(arena,(160-canvasWidth)*.5f,(144-canvasHeight)*.5f,canvasWidth,canvasHeight);
      for(float[] overlay:marginOverlays){
         snapshotBatch.setColor(overlay[4],overlay[5],overlay[6],overlay[7]);
         snapshotBatch.draw(overlayPixel,overlay[0],overlay[1],overlay[2],overlay[3]);
      }
      snapshotBatch.end();
   }

   private void marginOverlay(float x,float y,float w,float h,Color color){
      marginOverlays.add(new float[]{x,y,w,h,color.r,color.g,color.b,color.a});
   }

   private static boolean isSpecialIntro(Action action) {
      return action != null && action.getClass().getSimpleName().equals("BattleIntro1");
   }

   private void captureWorld() {
      int width = Gdx.graphics.getBackBufferWidth();
      int height = Gdx.graphics.getBackBufferHeight();
      Gdx.gl.glViewport(0,0,width,height);
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
      Gdx.gl.glViewport(0,0,width,height);
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
         if(arena==null)ensureArena("grassland");
         snapshotBatch.setColor(Color.WHITE);
         snapshotBatch.draw(arena, 0, 0, width, height);
      }
      snapshotBatch.end();
   }

   public void drawBackground(Game game) {
      drawBackground(game,game.map==null?"grassland":game.map.currBiome);
   }

   /** Special encounters may have multiple ordered background/effect actions. */
   public void drawBossBackground(Game game) {
      if(lastBattleFrame!=frame)drawBackground(game,"cave");
   }

   private void drawBackground(Game game,String biome) {
      ModernUi ui = ModernUi.get(game);
      if (ui == null) return;
      ensureArena(biome);
      arenaShader=game.uiBatch.getShader();
      oldColor.set(game.uiBatch.getColor());
      game.uiBatch.setColor(Color.WHITE);
      float aspect=Gdx.graphics.getBackBufferWidth()/(float)Math.max(1,Gdx.graphics.getBackBufferHeight());
      float w=Math.max(160,144*aspect),h=Math.max(144,160/aspect);
      game.uiBatch.draw(arena,(160-w)*.5f,(144-h)*.5f,w,h);
      game.uiBatch.setColor(oldColor);
      if (lastBattleFrame != frame) { battleFrames++; lastBattleFrame = frame; }
   }

   public void drawBossRock(Game game,com.badlogic.gdx.graphics.g2d.Sprite source) {
      TextureRegion art=BwAssets.get().named("rock");
      com.badlogic.gdx.graphics.g2d.Sprite rock=new com.badlogic.gdx.graphics.g2d.Sprite(art);
      rock.setBounds(source.getX()+7,source.getY()+7,18,18f*art.getRegionHeight()/art.getRegionWidth());
      rock.setOriginCenter();rock.setRotation(source.getRotation());rock.setColor(source.getColor());rock.draw(game.uiBatch);
   }

   public void drawBossShockwave(Game game,double clock,float fade) {
      if(clock>=Math.PI*8)return;
      float radius=8+(float)clock*7;
      Color color=tint.set(.93f,.77f,.43f,MathUtils.clamp(1-(float)(clock/(Math.PI*8)),0,1)*.6f);
      for(int i=0;i<64;i++){
         float angle=i*MathUtils.PI2/64;
         ModernUi.get(game).rect(game,124+MathUtils.cos(angle)*radius,88+MathUtils.sin(angle)*radius*.22f,2.1f,.65f,color);
      }
   }

   public void drawBossFade(Game game,float alpha) {
      if(alpha<=0)return;
      Color color=tint.set(ModernUi.INK).mul(1,1,1,MathUtils.clamp(alpha,0,1));
      ModernUi.get(game).rect(game,-320,-288,800,720,color);marginOverlay(-320,-288,800,720,color);
   }

   /** Preserve Mewtwo's moving distortion with a GPU copy at the actual viewport size. */
   public void drawBossRipple(Game game,int y,int[] offsets) {
      if(y<0||y>144)return;
      game.uiBatch.flush();
      int width=Gdx.graphics.getBackBufferWidth(),height=Gdx.graphics.getBackBufferHeight();
      if(rippleSnapshot==null||rippleSnapshot.getWidth()!=width||rippleSnapshot.getHeight()!=height){
         if(rippleSnapshot!=null)rippleSnapshot.dispose();rippleSnapshot=new Texture(width,height,Pixmap.Format.RGBA8888);
      }
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);rippleSnapshot.bind();
      Gdx.gl.glCopyTexSubImage2D(GL20.GL_TEXTURE_2D,0,0,0,0,0,width,height);
      float scale=Math.min(width/160f,height/144f),left=(width-160*scale)*.5f,bottom=(height-144*scale)*.5f;
      for(int row=0;row<offsets.length&&y+row<144;row++){
         float py=bottom+(y+row)*scale;
         game.uiBatch.draw(rippleSnapshot,offsets[row],y+row,160,1,left/width,py/height,(left+160*scale)/width,(py+scale)/height);
      }
   }

   /** PMD frames carry their ground anchor; the old 56x40 mask cut off their feet. */
   public void drawEnemyMask(Game game) {
   }

   public void drawNightTint(Game game) {
      ModernUi ui = ModernUi.get(game);
      if (ui != null) ui.rect(game, -320, -288, 800, 720, tint.set(0.08f, 0.16f, 0.27f, 0.16f));
      marginOverlay(-320,-288,800,720,tint);
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
      ui.fitText(game, DesktopControls.confirm() + " Select  " + DesktopControls.back() + " Back",
         8, y + 9.5f, 2.7f, 50, ModernUi.MUTED);
   }

   public void drawIntro(Game game, Action action, int remaining) {
      ModernUi ui = ModernUi.get(game);
      float p = progress(action, remaining);
      float pulse = 0.10f + 0.13f * (0.5f + 0.5f * MathUtils.sin(p * MathUtils.PI * 6f));
      ui.rect(game, -320, -288, 800, 720, tint.set(0.08f, 0.22f, 0.26f, pulse));
      marginOverlay(-320,-288,800,720,tint);
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
      marginOverlay(-320,-288,800,288+72*p,ModernUi.INK);
      marginOverlay(-320,144-72*p,800,432,ModernUi.INK);
      ui.rect(game, 0, 72 * p, 160, 1, ModernUi.GOLD);
      ui.rect(game, 0, 143 - 72 * p, 160, 1, ModernUi.GOLD);
      countTransition();
   }

   public void drawFadeOut(Game game, Action action, int remaining) {
      ModernUi ui = ModernUi.get(game);
      float alpha = MathUtils.clamp(remaining / (float)total(action, remaining), 0f, 1f);
      ui.rect(game, -320, -288, 800, 720, tint.set(ModernUi.PAPER).mul(1f, 1f, 1f, alpha));
      marginOverlay(-320,-288,800,720,tint);
      countTransition();
   }

   public void drawFade(Game game, FadeAnim action) {
      int timer = action.timer;
      int slow = action.slow;
      float alpha = timer < 2 * slow ? 0f : timer < 4 * slow ? 0.25f : timer < 6 * slow ? 0.5f
         : timer < 12 * slow ? 1f : timer < 14 * slow ? 0.75f : timer < 16 * slow ? 0.5f : timer < 18 * slow ? 0.25f : 0f;
      if (alpha > 0f) {
         ModernUi.get(game).rect(game, -320, -288, 800, 720, tint.set(ModernUi.PAPER).mul(1f, 1f, 1f, alpha));
         marginOverlay(-320,-288,800,720,tint);
      }
      countTransition();
   }

   public void drawTravelFade(Game game, float alpha) {
      ModernUi.get(game).rect(game, -320, -288, 800, 720, tint.set(ModernUi.INK).mul(1f,1f,1f,alpha));
      marginOverlay(-320,-288,800,720,tint);
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

   /** Compose authored landscape tiles and props into a fixed battle stage. */
   private void ensureArena(String biome) {
      if (biome == null) biome = "grassland";
      float aspect=Gdx.graphics.getBackBufferWidth()/(float)Math.max(1,Gdx.graphics.getBackBufferHeight());
      int width=(int)Math.ceil(Math.max(320,288*aspect)),height=(int)Math.ceil(Math.max(288,320/aspect));
      if (arena != null && biome.equals(arenaBiome) && width==arenaWidth && height==arenaHeight) return;
      if (arena != null) arena.dispose();
      arenaBiome = biome;
      arenaWidth=width;arenaHeight=height;
      int offsetX=(width-320)/2,offsetY=(height-288)/2;
      Pixmap p = new Pixmap(width,height,Pixmap.Format.RGBA8888);
      boolean sand = biome.contains("desert") || biome.contains("beach");
      boolean snow = biome.contains("tundra") || biome.contains("snow");
      boolean cave = biome.contains("cave") || biome.contains("volcan") || biome.contains("dungeon");
      boolean volcano = biome.contains("volcan");
      boolean graveyard=biome.contains("graveyard");
      BiomeProfiles.Profile profile=BiomeProfiles.named(volcano?"volcano":graveyard?"graveyard":snow?"snow":sand?"desert":cave?"cave":biome.contains("forest")?"forest":"plains");
      Color sky = graveyard?new Color(.20f,.28f,.28f,1):volcano ? new Color(.22f,.09f,.14f,1) : cave ? new Color(.12f,.18f,.26f,1)
         : snow ? new Color(.69f,.79f,.88f,1) : sand ? new Color(.76f,.83f,.80f,1) : new Color(.49f,.69f,.64f,1);
      Color horizon = graveyard?new Color(.46f,.53f,.49f,1):volcano ? new Color(.48f,.16f,.13f,1) : cave ? new Color(.25f,.34f,.40f,1)
         : new Color(.83f,.88f,.69f,1);
      for (int y = 0; y < height; y++) {
         tint.set(sky).lerp(horizon,MathUtils.clamp((y-offsetY)/105f,0,1));
         p.setColor(tint); p.drawLine(0,y,width-1,y);
      }
      Pixmap atlas=new Pixmap(Gdx.files.internal("visual/landscape/world-atlas.png"));
      BwAssets assets=BwAssets.get();
      TextureRegion ground=assets.cell(profile.terrain("ground"),0,0);
      paintGround(p,atlas,ground,horizon);
      if (cave) {
         TextureRegion wall=assets.named(profile.cliff("front"));
         Color wallTint=volcano?Color.WHITE:new Color(.58f,.64f,.72f,1);
         for (int x=-8-offsetX;x<320+offsetX;x+=32) stamp(p,atlas,wall,x+offsetX,-24-(x&16),40,122+offsetY,wallTint);
         TextureRegion stone=assets.named(profile.decor("rock"));
         if (stone==null) stone=assets.named("rock");
         for (int x=-18-offsetX;x<340+offsetX;x+=43) stamp(p,atlas,stone,x+offsetX,offsetY+53+(x&15),36,48,new Color(.72f,.80f,.84f,1));
      } else {
         TextureRegion tree=assets.named(snow||sand||graveyard?profile.decor("tree"):"forest_tree");
         if (tree==null) tree=assets.named("tree");
         for (int x=-30-offsetX;x<350+offsetX;x+=37) {
            int treeHeight=64+(Math.floorMod(x*7,19));
            stamp(p,atlas,tree,x+offsetX,offsetY+92-treeHeight,Math.max(1,(int)(treeHeight*tree.getRegionWidth()/(float)tree.getRegionHeight())),treeHeight,
               new Color(.57f,.69f,.64f,1));
         }
         for (int x=-38-offsetX;x<360+offsetX;x+=73) {
            int treeHeight=90+Math.floorMod(x*3,19);
            stamp(p,atlas,tree,x+offsetX,offsetY+103-treeHeight,Math.max(1,(int)(treeHeight*tree.getRegionWidth()/(float)tree.getRegionHeight())),treeHeight,Color.WHITE);
         }
      }
      if(graveyard) {
         for(int x=-12-offsetX;x<340+offsetX;x+=44)
            stamp(p,atlas,assets.named(profile.decor("gravestone")),x+offsetX,offsetY+80+(x&7),17,24,Color.WHITE);
      }
      if(volcano)paintLava(p,atlas,assets.namedAnimated(profile.string("fluid","lava","lava_bright"),0));
      TextureRegion platform=assets.cell(profile.terrain("path"),0,0);
      if(platform==null) platform=ground;
      paintPlatform(p,atlas,platform,offsetX+188,offsetY+103,120,24,cave);
      paintPlatform(p,atlas,platform,offsetX-9,offsetY+179,173,34,cave);
      if(graveyard) {
         // Haze belongs to the scenery; it never veils UI text or the Pokemon.
         Color pixel=new Color(),mist=Color.valueOf(profile.string("ambient","fogColor","a5b5b0"));
         for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            Color.rgba8888ToColor(pixel,p.getPixel(x,y));
            pixel.lerp(mist,.09f+.05f*MathUtils.sin(y*.12f));p.drawPixel(x,y,Color.rgba8888(pixel));
         }
      }
      atlas.dispose();
      arena = new Texture(p);
      arena.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
      p.dispose();
   }

   private void paintGround(Pixmap destination, Pixmap atlas, TextureRegion region, Color haze) {
      if (region==null) return;
      int ox=region.getRegionX(), oy=region.getRegionY(), width=region.getRegionWidth(), height=region.getRegionHeight();
      Color pixel = new Color();
      int offsetY=(destination.getHeight()-288)/2;
      for (int y=76+offsetY;y<destination.getHeight();y++) {
         float depth=(y-76-offsetY)/212f, perspective=.5f+depth*.9f;
         int ty=Math.floorMod((int)((y-76-offsetY)/perspective),height);
         for (int x=0;x<destination.getWidth();x++) {
            int tx=Math.floorMod((int)((x-destination.getWidth()/2)/perspective),width);
            int rgba=atlas.getPixel(ox+tx,oy+ty);
            if ((rgba&255)<128) continue;
            Color.rgba8888ToColor(pixel,rgba);
            pixel.lerp(haze,Math.max(0,(1-depth)*.18f));
            destination.drawPixel(x,y,Color.rgba8888(pixel));
         }
      }
   }

   private static void stamp(Pixmap destination,Pixmap atlas,TextureRegion region,int x,int y,int width,int height,Color tint) {
      if(region==null)return;
      Color color=new Color();
      for(int row=0;row<height;row++) for(int col=0;col<width;col++) {
         int rgba=atlas.getPixel(region.getRegionX()+col*region.getRegionWidth()/width,
            region.getRegionY()+row*region.getRegionHeight()/height);
         if((rgba&255)==0)continue;
         Color.rgba8888ToColor(color,rgba); color.mul(tint);
         destination.drawPixel(x+col,y+row,Color.rgba8888(color));
      }
   }

   private static void paintPlatform(Pixmap p,Pixmap atlas,TextureRegion material,int x,int y,int width,int height,boolean cave) {
      ellipse(p,x,y+4,width,height,new Color(.09f,.14f,.15f,.55f));
      if(material==null)return;
      Color color=new Color();
      for(int row=0;row<height;row++) {
         float dy=(row+.5f)/height*2-1, half=(float)Math.sqrt(Math.max(0,1-dy*dy))*width*.5f;
         int left=Math.round(x+width*.5f-half), right=Math.round(x+width*.5f+half);
         for(int col=left;col<=right;col++) {
            int rgba=atlas.getPixel(material.getRegionX()+Math.floorMod(col,material.getRegionWidth()),
               material.getRegionY()+Math.floorMod(row*2,material.getRegionHeight()));
            Color.rgba8888ToColor(color,rgba);
            color.mul(cave?.84f:1.04f,cave?.86f:1.02f,cave?.92f:.94f,1);
            if(row>height-4)color.mul(.75f,.80f,.77f,1);
            p.drawPixel(col,y+row,Color.rgba8888(color));
         }
      }
   }

   private static void paintLava(Pixmap destination,Pixmap atlas,TextureRegion lava) {
      if(lava==null)return;
      int offsetY=(destination.getHeight()-288)/2,offsetX=(destination.getWidth()-320)/2;
      for(int x=0;x<destination.getWidth();x++){
         int top=offsetY+143+Math.round(7*MathUtils.sin((x-offsetX)/43f));
         for(int y=top-2;y<top+14;y++){
            int color=y<top||y>top+11?0x402127FF:atlas.getPixel(lava.getRegionX()+x%lava.getRegionWidth(),
               lava.getRegionY()+(y-top)%lava.getRegionHeight());
            destination.drawPixel(x,y,color);
         }
      }
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
      if (rippleSnapshot != null) rippleSnapshot.dispose();
      if (arena != null) arena.dispose();
      if (snapshotBatch != null) snapshotBatch.dispose();
      if (overlayPixel != null) overlayPixel.dispose();
      transitionLengths.clear();
   }
}
