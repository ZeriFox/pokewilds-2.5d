package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import java.util.IdentityHashMap;
import java.util.Locale;
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
   private final BattleScenery scenery=new BattleScenery();
   private float scenerySeconds;
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
   private int arenaWidth,arenaHeight;
   private float canvasWidth=160,canvasHeight=144;
   private final IntBuffer previousScissor=BufferUtils.newIntBuffer(4);
   private boolean previousScissorEnabled;

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
      battleCanvas = phase.equals("intro") || phase.equals("wipe") || phase.equals("out")
         || game.player != null && game.player.dontDrawMapDuringBattle;
      for (Action action : game.actionStack) if (action instanceof DrawBattle) battleCanvas = true;
      ModernUi modernUi=ModernUi.get(game);
      if(battleCanvas&&(modernUi==null||!modernUi.reducedMotion()))
         scenerySeconds+=Math.min(.05f,Math.max(0,Gdx.graphics.getDeltaTime()));
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

   /** Restore only the UI projection: selection and attacks share one uninterrupted clock. */
   public void finishFrame(Game game) {
      if(projectionSaved) {
         game.uiBatch.setProjectionMatrix(previousUiProjection);
         projectionSaved=false;
      }
   }

   private final float[] fullScreenVertices=new float[20];
   private long fullScreenEffectDraws;
   public long getFullScreenEffectDraws(){return fullScreenEffectDraws;}

   /** Expand authored screen overlays, not targeted particles or Pokemon.
    * Source vertices, animation state, texture UVs and the original action are read-only. */
   public boolean drawScreenEffect(com.badlogic.gdx.graphics.g2d.Sprite source,com.badlogic.gdx.graphics.g2d.Batch batch) {
      Game game=Game.staticGame;
      if(!battleCanvas||game==null||batch!=game.uiBatch||source==null)return false;
      if(batch instanceof ModernBatch&&((ModernBatch)batch).suppressed)return true;
      if(source.getWidth()<128||source.getHeight()<96||source.getX()>16||source.getY()>48
         ||source.getX()+source.getWidth()<144||source.getY()+source.getHeight()<136)return false;
      float factor=Math.max(canvasWidth/160f,canvasHeight/144f);
      System.arraycopy(source.getVertices(),0,fullScreenVertices,0,20);
      for(int i=0;i<20;i+=5) {
         fullScreenVertices[i]=80+(fullScreenVertices[i]-80)*factor;
         fullScreenVertices[i+1]=72+(fullScreenVertices[i+1]-72)*factor;
      }
      batch.draw(source.getTexture(),fullScreenVertices,0,20);
      fullScreenEffectDraws++;
      return true;
   }

   /** Clip staged actor slides before they draw, never repaint over later effects. */
   boolean beginActorClip(com.badlogic.gdx.graphics.g2d.Batch batch) {
      if (!battleCanvas) return false;
      batch.flush();
      previousScissorEnabled=Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
      previousScissor.clear();Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX,previousScissor);
      int w=Gdx.graphics.getBackBufferWidth(),h=Gdx.graphics.getBackBufferHeight();
      float scale=Math.min(w/160f,h/144f);
      int left=(int)Math.floor((w-160*scale)*.5f),bottom=(int)Math.floor((h-144*scale)*.5f);
      int right=(int)Math.ceil((w+160*scale)*.5f),top=(int)Math.ceil((h+144*scale)*.5f);
      if(previousScissorEnabled) {
         left=Math.max(left,previousScissor.get(0));bottom=Math.max(bottom,previousScissor.get(1));
         right=Math.min(right,previousScissor.get(0)+previousScissor.get(2));
         top=Math.min(top,previousScissor.get(1)+previousScissor.get(3));
      }
      Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
      Gdx.gl.glScissor(left,bottom,Math.max(0,right-left),Math.max(0,top-bottom));
      return true;
   }

   void endActorClip(com.badlogic.gdx.graphics.g2d.Batch batch) {
      batch.flush();
      Gdx.gl.glScissor(previousScissor.get(0),previousScissor.get(1),previousScissor.get(2),previousScissor.get(3));
      if(!previousScissorEnabled)Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
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
      oldColor.set(game.uiBatch.getColor());
      game.uiBatch.setColor(Color.WHITE);
      float aspect=Gdx.graphics.getBackBufferWidth()/(float)Math.max(1,Gdx.graphics.getBackBufferHeight());
      float w=Math.max(160,144*aspect),h=Math.max(144,160/aspect);
      clearActorDepth(game);
      scenery.draw(game.uiBatch,arena,biome,ui.reducedMotion()?0:scenerySeconds,cameraYaw(game),(160-w)*.5f,(144-h)*.5f,w,h);
      game.uiBatch.setColor(oldColor);
      if (lastBattleFrame != frame) { battleFrames++; lastBattleFrame = frame; }
   }

   public void drawBossRock(Game game,com.badlogic.gdx.graphics.g2d.Sprite source) {
      TextureRegion art=BwAssets.get().named("rock");
      com.badlogic.gdx.graphics.g2d.Sprite rock=new com.badlogic.gdx.graphics.g2d.Sprite(art);
      com.badlogic.gdx.math.Vector3 point=projectScenePoint(game,source.getX()+16,source.getY()+16,new com.badlogic.gdx.math.Vector3());
      float size=18*point.z*.68f;
      rock.setBounds(point.x-size*.5f,point.y-size*.5f,size,size*art.getRegionHeight()/art.getRegionWidth());
      rock.setOriginCenter();rock.setRotation(source.getRotation());rock.setColor(source.getColor());rock.draw(game.uiBatch);
   }

   public void drawBossShockwave(Game game,double clock,float fade) {
      if(clock>=Math.PI*8)return;
      float radius=8+(float)clock*7;
      Color color=tint.set(.93f,.77f,.43f,MathUtils.clamp(1-(float)(clock/(Math.PI*8)),0,1)*.6f);
      for(int i=0;i<64;i++){
         float angle=i*MathUtils.PI2/64;
         com.badlogic.gdx.math.Vector3 point=projectActor(game,124+MathUtils.cos(angle)*radius,
            88+MathUtils.sin(angle)*radius*.22f,new com.badlogic.gdx.math.Vector3());
         ModernUi.get(game).rect(game,point.x,point.y,2.1f*point.z,.65f*point.z,color);
      }
   }

   public void drawBossFade(Game game,float alpha) {
      if(alpha<=0)return;
      Color color=tint.set(ModernUi.INK).mul(1,1,1,MathUtils.clamp(alpha,0,1));
      ModernUi.get(game).rect(game,-320,-288,800,720,color);
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
   }

   public void drawFriendlyHealth(Game game, DrawFriendlyHealthGen2 health) {
      ModernUi ui = ModernUi.get(game);
      Pokemon pokemon = game.player.currPokemon;
      ui.panel(game,83,122,74,19);
      ui.fitText(game,pokemon.nickname,87,138,4.5f,47,ModernUi.INK);
      ui.text(game,"Lv "+pokemon.level,136,138,2.8f,ModernUi.MUTED);
      String status=status(pokemon);
      ui.text(game,status.isEmpty()?gender(pokemon):status,87,132,2.7f,status.isEmpty()?ModernUi.MUTED:ModernUi.RED);
      ui.bar(game,98,129,54,2.5f,health.currHealth/48f,healthColor(health.currHealth));
      ui.text(game,health.currHealthRemaining+" / "+pokemon.maxStats.get("hp"),115,127.5f,2.8f,ModernUi.INK);
      ui.text(game,"EXP",87,125,2.2f,ModernUi.MUTED);
      ui.bar(game,98,123.4f,54,1.0f,DrawFriendlyHealthGen2.xpBarIndex/68f,ModernUi.GOLD);
   }

   public void drawEnemyHealth(Game game, DrawEnemyHealthGen2 health) {
      ModernUi ui=ModernUi.get(game);Pokemon pokemon=game.battle.oppPokemon;
      float x=health.translateAmt.x,y=health.translateAmt.y;
      ui.panel(game,3+x,122+y,74,19);
      ui.fitText(game,pokemon.nickname,7+x,138+y,4.5f,47,ModernUi.INK);
      ui.text(game,"Lv "+pokemon.level,56+x,138+y,2.8f,ModernUi.MUTED);
      ui.text(game,"HP",7+x,132+y,2.7f,ModernUi.MUTED);
      ui.bar(game,18+x,129+y,54,2.5f,health.currHealth/48f,healthColor(health.currHealth));
      String status=status(pokemon);
      if(!pokemon.isGhost)ui.text(game,status.isEmpty()?gender(pokemon):status,7+x,126+y,2.7f,
         status.isEmpty()?ModernUi.MUTED:ModernUi.RED);
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
      float left=(160-canvasWidth)*.5f,bottom=(144-canvasHeight)*.5f;
      float entrance=ease(p/.20f),close=ease((p-.77f)/.23f);
      boolean still=ui.reducedMotion();
      if(!still&&hasSnapshot) {
         // A short lens push into the captured modern world leads into the arena.
         // This is presentation only; the original encounter timers stay unchanged.
         float zoom=1f+.13f*ease(p/.77f);
         oldColor.set(game.uiBatch.getColor());game.uiBatch.setColor(Color.WHITE);
         game.uiBatch.draw(worldSnapshot,left-canvasWidth*(zoom-1)*.5f,
            bottom-canvasHeight*(zoom-1)*.5f,canvasWidth*zoom,canvasHeight*zoom,
            0,0,snapshotWidth,snapshotHeight,false,true);
         game.uiBatch.setColor(oldColor);
      }
      // One continuous darkening replaces the old repeating flashes. The
      // modern world capture remains underneath for every encounter frame.
      ui.rect(game,left,bottom,canvasWidth,canvasHeight,tint.set(ModernUi.INK).mul(1,1,1,.36f*entrance));
      float bandWidth=still?canvasWidth:canvasWidth*entrance;
      ui.rect(game,left,53,bandWidth,38,tint.set(ModernUi.INK).mul(1,1,1,still?entrance:.94f));
      if(entrance>.99f) {
         ui.rect(game,14,61,1.5f,22,ModernUi.GOLD);
         ui.text(game,"WILD ENCOUNTER",22,83,4,ModernUi.LINE);
         ui.fitText(game,"Something is approaching...",22,74,6.4f,116,ModernUi.PAPER);
         ui.rect(game,22,59,116,.6f,ModernUi.MUTED);
         if(!still)ui.rect(game,22,59,116*MathUtils.clamp(p/.77f,0,1),.6f,ModernUi.GOLD);
      }
      if(still)ui.rect(game,left,bottom,canvasWidth,canvasHeight,tint.set(ModernUi.INK).mul(1,1,1,close));
      else shutters(game,ui,close,false);
      countTransition();
   }

   public void drawWipe(Game game, Action action, int remaining) {
      ModernUi ui = ModernUi.get(game);
      int total = total(action, remaining);
      float elapsed=total-remaining;
      float reveal=ease((elapsed-5f)/Math.max(1,Math.min(32,total-6)));
      // The arena is already complete behind the opening shutters. No old
      // world tiles or 160x144 margin masks can leak through the reveal.
      drawBackground(game,isSpecialIntro(action)?"cave":game.map==null?"grassland":game.map.currBiome);
      if(ui.reducedMotion()) {
         ui.rect(game,(160-canvasWidth)*.5f,(144-canvasHeight)*.5f,canvasWidth,canvasHeight,
            tint.set(ModernUi.INK).mul(1,1,1,1-reveal));
      } else shutters(game,ui,1-reveal,true);
      countTransition();
   }

   /** Exact viewport halves meet without a seam, including portrait/ultrawide. */
   private void shutters(Game game,ModernUi ui,float coverage,boolean edge) {
      float left=(160-canvasWidth)*.5f,bottom=(144-canvasHeight)*.5f;
      float height=canvasHeight*.5f*MathUtils.clamp(coverage,0,1);
      if(height<=0)return;
      ui.rect(game,left,bottom,canvasWidth,height,ModernUi.INK);
      ui.rect(game,left,bottom+canvasHeight-height,canvasWidth,height,ModernUi.INK);
      if(edge&&coverage>0&&coverage<1) {
         ui.rect(game,left,bottom+height-.4f,canvasWidth,.4f,ModernUi.GOLD);
         ui.rect(game,left,bottom+canvasHeight-height,canvasWidth,.4f,ModernUi.GOLD);
      }
   }

   private static float ease(float value) {
      float t=MathUtils.clamp(value,0,1);return t*t*(3-2*t);
   }

   public void drawFadeOut(Game game, Action action, int remaining) {
      ModernUi ui = ModernUi.get(game);
      float alpha = ease(remaining / (float)total(action, remaining));
      ui.rect(game,(160-canvasWidth)*.5f,(144-canvasHeight)*.5f,canvasWidth,canvasHeight,
         tint.set(ModernUi.INK).mul(1f, 1f, 1f, alpha));
      countTransition();
   }

   public void drawFade(Game game, FadeAnim action) {
      int timer = action.timer;
      int slow = action.slow;
      float alpha = timer < 2 * slow ? 0f : timer < 4 * slow ? 0.25f : timer < 6 * slow ? 0.5f
         : timer < 12 * slow ? 1f : timer < 14 * slow ? 0.75f : timer < 16 * slow ? 0.5f : timer < 18 * slow ? 0.25f : 0f;
      if (alpha > 0f) {
         ModernUi.get(game).rect(game, -320, -288, 800, 720, tint.set(ModernUi.PAPER).mul(1f, 1f, 1f, alpha));
      }
      countTransition();
   }

   public void drawTravelFade(Game game, float alpha) {
      ModernUi.get(game).rect(game, -320, -288, 800, 720, tint.set(ModernUi.INK).mul(1f,1f,1f,alpha));
      countTransition();
   }

   private float progress(Action action, int remaining) {
      return MathUtils.clamp((total(action,remaining)-remaining)/(float)Math.max(1,total(action,remaining)-1),0,1);
   }
   private int total(Action action, int remaining) {
      Integer total = transitionLengths.get(action);
      if (total == null) { total = Math.max(1, remaining); transitionLengths.put(action, total); }
      return total;
   }
   private void countTransition() {
      if (lastTransitionFrame != frame) { transitionFrames++; lastTransitionFrame = frame; }
   }

   /** Opaque continuous arena, independent of map tiles and viewport size. */
   private void ensureArena(String biome) {
      String key=BattleScenery.asset(biome);
      if(arena!=null&&key.equals(arenaBiome))return;
      if(arena!=null)arena.dispose();
      arena=new Texture(Gdx.files.internal("visual/battle-scenes/"+key+".png"));
      arena.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
      arena.setWrap(Texture.TextureWrap.ClampToEdge,Texture.TextureWrap.ClampToEdge);
      arenaBiome=key;arenaWidth=arena.getWidth();arenaHeight=arena.getHeight();
   }

   /** One clock for intros, selection, turns and captures; only reduced motion stops it. */
   float cameraYaw(Game game) {
      ModernUi ui=ModernUi.get(game);
      return battleCanvas&&ui!=null&&!ui.reducedMotion()?BattleScenery.orbit(scenerySeconds):0;
   }
   com.badlogic.gdx.math.Vector3 projectActor(Game game,float x,float y,com.badlogic.gdx.math.Vector3 out) {
      return BattleScenery.project(x,y,cameraYaw(game),out);
   }
   public com.badlogic.gdx.math.Vector3 projectScenePoint(Game game,float x,float y,com.badlogic.gdx.math.Vector3 out) {
      return BattleScenery.projectEffect(x,y,cameraYaw(game),out);
   }
   String actorFacing(Game game,boolean friendly){return BattleScenery.facing(friendly,cameraYaw(game));}

   private void clearActorDepth(Game game) {
      game.uiBatch.flush();
      java.nio.IntBuffer state=BufferUtils.newIntBuffer(1);
      Gdx.gl.glGetIntegerv(GL20.GL_DEPTH_WRITEMASK,state);
      Gdx.gl.glDepthMask(true);Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
      Gdx.gl.glDepthMask(state.get(0)!=0);
   }

   /** A particle sprite stays upright while its ground anchor follows the camera. */
   public boolean drawSceneEffect(com.badlogic.gdx.graphics.g2d.Sprite source,com.badlogic.gdx.graphics.g2d.Batch batch) {
      Game game=Game.staticGame;
      if(!battleCanvas||game==null||batch!=game.uiBatch||source==null)return false;
      if(batch instanceof ModernBatch&&((ModernBatch)batch).suppressed)return true;
      com.badlogic.gdx.math.Vector3 anchor=projectScenePoint(game,source.getX()+source.getWidth()*.5f,
         source.getY()+source.getHeight()*.5f,new com.badlogic.gdx.math.Vector3());
      System.arraycopy(source.getVertices(),0,fullScreenVertices,0,20);
      float cx=source.getX()+source.getWidth()*.5f,cy=source.getY()+source.getHeight()*.5f;
      for(int i=0;i<20;i+=5){
         fullScreenVertices[i]=anchor.x+(fullScreenVertices[i]-cx)*anchor.z*.68f;
         fullScreenVertices[i+1]=anchor.y+(fullScreenVertices[i+1]-cy)*anchor.z*.68f;
      }
      batch.draw(source.getTexture(),fullScreenVertices,0,20);return true;
   }

   public long getBattleFrames() { return battleFrames; }
   public long getTransitionFrames() { return transitionFrames; }
   public long getSnapshotFrames() { return snapshotFrames; }
   public String getPhase() { return phase; }
   public void dispose() {
      if (worldSnapshot != null) worldSnapshot.dispose();
      if (rippleSnapshot != null) rippleSnapshot.dispose();
      if (arena != null) arena.dispose();
      scenery.dispose();
      if (snapshotBatch != null) snapshotBatch.dispose();
      transitionLengths.clear();
   }
}
