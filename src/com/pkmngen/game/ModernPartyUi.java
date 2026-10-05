package com.pkmngen.game;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only presentation of setup, party and summary actions. Input remains in their original step methods. */
public final class ModernPartyUi {
   private static final Color SOFT = new Color(0.87f, 0.94f, 0.88f, 1f);
   private static final String PARTY_NESTED = DrawPokemonMenu.class.getName() + "$";
   private static final String STATS_NESTED = DrawStatsScreen.class.getName() + "$";

   public boolean covers(Action action) {
      if (action == null) return false;
      String name = action.getClass().getName();
      return action instanceof DrawSetupMenu || action instanceof DrawControls
         || action instanceof DrawPlayerMenu || action instanceof DrawPlayerMenu.Intro
         || action instanceof DrawPokemonMenu || action instanceof DrawStatsScreen
         || action instanceof GainExpAnimationGen2.ShowLevelUpStats
         || action instanceof Pokemon.SetNickname
         || name.startsWith(PARTY_NESTED) || name.startsWith(STATS_NESTED);
   }

   public void render(ModernUi ui, Game game, Action action) {
      if (action instanceof DrawSetupMenu) setup(ui, game, (DrawSetupMenu)action);
      else if (action instanceof GainExpAnimationGen2.ShowLevelUpStats) levelUp(ui, game, (GainExpAnimationGen2.ShowLevelUpStats)action);
      else if (action instanceof Pokemon.SetNickname) nickname(ui, game, (Pokemon.SetNickname)action);
      else if (action instanceof DrawControls) controls(ui, game, (DrawControls)action);
      else if (action instanceof DrawPlayerMenu) playerMenu(ui, game, (DrawPlayerMenu)action);
      else if (action instanceof DrawPokemonMenu) {
         party(ui, game, (DrawPokemonMenu)action, DrawPokemonMenu.currIndex, DrawPokemonMenu.scrollIndex, -1);
      } else if (action instanceof DrawStatsScreen) stats(ui, game, (DrawStatsScreen)action);
      else if (action instanceof DrawPokemonMenu.SelectedMenu) {
         DrawPokemonMenu.SelectedMenu menu = (DrawPokemonMenu.SelectedMenu)action;
         parent(ui, game, menu.prevMenu);
         if (menu.textboxDelay > 0) ui.popup(game, displayName(menu.pokemon), menu.words, menu.curr);
      } else if (action instanceof DrawPokemonMenu.SelectedMenu.Switch) {
         DrawPokemonMenu.SelectedMenu.Switch menu = (DrawPokemonMenu.SelectedMenu.Switch)action;
         if (menu.prevMenu instanceof DrawPokemonMenu) {
            party(ui, game, (DrawPokemonMenu)menu.prevMenu, menu.curr, menu.scrollIndex, menu.startPosition);
         } else parent(ui, game, menu.prevMenu);
         ui.footer(game, "Move to where?   " + confirmBack());
      } else if (action instanceof DrawPokemonMenu.SelectedMenu.ExitAfterActions) {
         parent(ui, game, ((DrawPokemonMenu.SelectedMenu.ExitAfterActions)action).prevMenu);
      } else if (action instanceof DrawPokemonMenu.SelectedMenu.Outro) {
         parent(ui, game, ((DrawPokemonMenu.SelectedMenu.Outro)action).prevMenu);
      } else if (action instanceof DrawPokemonMenu.SelectedMenu.Switch.Outro) {
         parent(ui, game, ((DrawPokemonMenu.SelectedMenu.Switch.Outro)action).prevMenu);
      } else if (action instanceof DrawPokemonMenu.Intro) {
         intro(ui, game, action.nextAction, "PARTY", ((DrawPokemonMenu.Intro)action).duration, 18);
      } else if (action instanceof DrawStatsScreen.Intro) {
         intro(ui, game, action.nextAction, "POKéMON SUMMARY", ((DrawStatsScreen.Intro)action).duration, 30);
      } else if (action instanceof DrawPlayerMenu.Intro) {
         DrawPlayerMenu.Intro intro = (DrawPlayerMenu.Intro)action;
         int remaining = intro.repeats.isEmpty() ? 0 : intro.repeats.get(0);
         intro(ui, game, action.nextAction, "EXPLORER", remaining, 17);
      } else if (action instanceof DrawPokemonMenu.Outro) {
         DrawPokemonMenu.Outro outro = (DrawPokemonMenu.Outro)action;
         outro(ui, game, outro.prevMenu, outro.duration);
      } else if (action instanceof DrawStatsScreen.Outro) {
         DrawStatsScreen.Outro outro = (DrawStatsScreen.Outro)action;
         outro(ui, game, outro.prevMenu, outro.duration);
      } else if (action instanceof Menu) {
         // Future nested party actions still keep their parent visible.
         parent(ui, game, ((Menu)action).prevMenu);
      }
   }

   private void setup(ModernUi ui, Game game, DrawSetupMenu menu) {
      ui.fullScreen(game, "POKéWILDS", "An island of your own  /  Adventure setup");
      ArrayList<SetupRow> rows = new ArrayList<>();
      int offset = menu.offset, offset2 = menu.offset2, offset3 = menu.offset3;
      for (int j = 0; j < 8; j++) {
         if (j == 0) rows.add(new SetupRow(j, "Play mode", pick(new String[] {"Local", "Host", "Join"}, menu.localHostJoinIndex), 0));
         else if (j == 1 + offset) rows.add(new SetupRow(j, "Adventure", menu.newLoadIndex == 0 ? "New island" : "Load save", 0));
         else if (j == 2 + offset + offset2) {
            boolean server = menu.localHostJoinIndex == 2;
            String value = letters(server ? menu.serverIp : menu.mapName);
            int selectedIndex = j - offset;
            if (value.isEmpty()) value = server ? "127.0.0.1" : "default";
            rows.add(new SetupRow(selectedIndex, server ? "Server IP" : "Save name", value, 1));
         } else if (j == 3 + offset + offset2 && menu.localHostJoinIndex != 2) {
            String[] sizes = {game.debugInputEnabled ? "Test island" : "Default (S)", "Small", "Medium", "Large", "Extra large", "Vast"};
            rows.add(new SetupRow(j - offset, "World size", pick(sizes, menu.sizeIndex), 0));
         } else if (j == 4 + offset + offset2 + offset3) {
            String name = letters(menu.name);
            rows.add(new SetupRow(j, "Your name", name.isEmpty() ? "Type your name" : name, 1));
         } else if (j == 5 + offset + offset2 + offset3 && menu.newLoadIndex != 1) {
            rows.add(new SetupRow(j, "Trainer", pick(menu.playerTypes, menu.playerTypeIndex), 2));
         } else if (j == 5 + offset + offset2 + offset3) {
            rows.add(new SetupRow(j, "Save file", menu.fileNames.isEmpty() ? "No saves found" : pick(menu.fileNames, menu.fileIndex), 0));
         } else if (j == 6 + offset + offset2 + offset3) {
            rows.add(new SetupRow(j, "", menu.localHostJoinIndex == 2 ? "Join adventure" : menu.newLoadIndex == 1 ? "Continue adventure" : "Begin adventure", 3));
         }
      }
      float gap = 14f, top = 116f;
      for (int i = 0; i < rows.size(); i++) {
         SetupRow row = rows.get(i);
         float y = top - (i + 1) * gap;
         boolean selected = DrawSetupMenu.currIndex == row.index;
         if (row.kind == 3) {
            ui.row(game, row.value + (selected ? "  →" : ""), 7, y, 146, 12.5f, selected);
            continue;
         }
         ui.panel(game, 7, y, 146, 12.5f);
         if (selected) { ui.rect(game, 8, y + 1, 144, 10.5f, SOFT); ui.rect(game, 7, y, 2, 12.5f, ModernUi.ACCENT); }
         ui.text(game, row.label, 12, y + 9.5f, 5.2f, ModernUi.MUTED);
         String value = row.value;
         if (selected && row.kind == 1 && DrawSetupMenu.avatarAnimCounter >= 12) value += "_";
         if (row.kind == 2) {
            ui.fitText(game, value, 62, y + 9.5f, 5.8f, 52, ModernUi.INK);
            Color swatch = menu.colors.isEmpty() ? ModernUi.GOLD : menu.colors.get(Math.floorMod(menu.avatarColorIndex, menu.colors.size()));
            if (selected && menu.playerColorIndex == 1) ui.rect(game, 118, y + 1, 10, 10, ModernUi.GOLD);
            ui.rect(game, 120, y + 3, 6, 6, swatch);
            if (selected && menu.playerColorIndex == 2) ui.rect(game, 133, y + 1, 17, 1, ModernUi.GOLD);
            TextureRegion trainer = BwAssets.get().trainer(value,"down",DrawSetupMenu.avatarAnimCounter/24f,false);
            if (trainer != null) region(game,trainer,134,y,14,14);
            else if (!menu.avatarSprites.isEmpty()) sprite(game, menu.avatarSprites.get(avatarFrame(menu.avatarSprites.size(), DrawSetupMenu.avatarAnimCounter)), 136, y + 1, 10, 12, false);
         } else ui.fitText(game, (selected && row.kind == 0 ? "← " : "") + value + (selected && row.kind == 0 ? " →" : ""), 62, y + 9.5f, 5.8f, 86, ModernUi.INK);
      }
      String help = "↑↓ Select   ←→ Change   " + key(InputProcessor.keyboardA) + " Start";
      for (SetupRow row : rows) if (row.index == DrawSetupMenu.currIndex) {
         if (row.kind == 1) help = "Type to edit   Backspace Delete   ↑↓ Select";
         if (row.kind == 2) help = "←→ Trainer / color   ↑↓ Change   ← Return";
      }
      ui.footer(game, help);
   }

   private void playerMenu(ModernUi ui, Game game, DrawPlayerMenu menu) {
      ui.panel(game, 77, 18, 77, 121);
      ui.rect(game, 78, 116, 75, 22, ModernUi.INK);
      ui.text(game, "EXPLORER", 84, 134, 8, ModernUi.PAPER);
      ui.fitText(game, game.player.name == null || game.player.name.isEmpty() ? game.player.character : game.player.name, 84, 123, 5, 61, ModernUi.GOLD);
      for (int i = 0; i < menu.entries.length; i++) {
         String title = menu.entries[i].equals("ITEM") ? "Bag" : title(menu.entries[i]);
         ui.row(game, title, 82, 96 - i * 16, 67, 14, i == menu.currIndex);
      }
      ui.panel(game, 7, 91, 63, 47);
      Sprite trainer = game.player.standingSprites.get("down");
      TextureRegion modernTrainer = BwAssets.get().trainer(game.player.character,"down",0,false);
      if (modernTrainer != null) region(game,modernTrainer,9,100,26,32);
      else sprite(game, trainer, 11, 103, 20, 26, false);
      ui.fitText(game, "FIELD JOURNAL", 34, 129, 4.1f, 32, ModernUi.MUTED);
      ui.text(game, "Party", 35, 117, 5.5f, ModernUi.INK);
      ui.text(game, game.player.pokemon.size() + " / 6", 35, 106, 7, ModernUi.ACCENT);
      ui.footer(game, "↑↓ Select   " + confirmBack());
   }

   private void party(ModernUi ui, Game game, DrawPokemonMenu menu, int selected, int scroll, int moving) {
      List<Pokemon> pokemon = DrawPokemonMenu.allPokemon == null ? game.player.pokemon : DrawPokemonMenu.allPokemon;
      boolean storage = menu.isStorageChest && pokemon != game.player.pokemon;
      ui.fullScreen(game, storage ? "STORAGE" : "YOUR PARTY", (storage ? "Pokémon at this location" : "Your travelling companions") + "  /  " + pokemon.size());
      if (pokemon.isEmpty()) {
         ui.panel(game, 9, 58, 142, 45); ui.text(game, "No Pokémon here yet.", 17, 86, 7, ModernUi.MUTED);
      }
      for (int i = 0; i < 6 && scroll + i < pokemon.size(); i++) {
         int index = scroll + i;
         if (index < 0) continue;
         Pokemon p = pokemon.get(index);
         float y = 100 - i * 16;
         ui.panel(game, 6, y, 148, 15);
         boolean chosen = i == selected;
         if (chosen) ui.rect(game, 7, y + 1, 146, 13, SOFT);
         if (chosen || moving == index) ui.rect(game, 6, y, 2, 15, moving == index ? ModernUi.GOLD : ModernUi.ACCENT);
         if (PmdBattleSprites.portrait(game, p, 9, y, 17, 15)) {
            // PMDCollab portrait, independent of battle/overworld sprite transforms.
         } else if (!p.isEgg && p.sprite != null) {
            // Use the same original portrait as the summary: some upstream overworld frames
            // contain placeholder art, while battle portraits consistently identify the species.
            sprite(game, p.sprite, 9, y, 17, 15, true);
         } else if (p.avatarSprites != null && !p.avatarSprites.isEmpty()) {
            int frame = chosen ? avatarFrame(p.avatarSprites.size(), DrawPokemonMenu.avatarAnimCounter) : 0;
            sprite(game, p.avatarSprites.get(frame), 10, y + 1, 14, 14, false);
         }
         String name = i < menu.ableWords.size() ? menu.ableWords.get(i) : displayName(p);
         ui.fitText(game, name, 28, y + 12, 6.2f, 72, ModernUi.INK);
         if (p.isEgg) { ui.text(game, "EGG", 124, y + 10, 5.5f, ModernUi.GOLD); continue; }
         int hp = stat(p.currentStats, "hp"), max = stat(p.maxStats, "hp");
         ui.text(game, "Lv " + p.level + (p.isShiny ? "  *" : ""), 28, y + 5, 4.3f, ModernUi.MUTED);
         String condition = condition(p);
         if (!condition.equals("OK")) ui.text(game, condition, 64, y + 5, 4.3f, ModernUi.RED);
         ui.fitText(game, hp + " / " + max, 105, y + 12, 5.1f, 43, ModernUi.MUTED);
         ui.bar(game, 91, y + 3, 57, 3, max > 0 ? (float)hp / max : 0, healthColor(hp, max));
      }
      String navigation = menu.isStorageChest ? "←→ Party / storage   " : "↑↓ Select   ";
      if (pokemon.size() > 6) ui.text(game, (Math.max(0, scroll) + 1) + "–" + Math.min(scroll + 6, pokemon.size()) + " / " + pokemon.size(), 113, 18, 4.1f, ModernUi.MUTED);
      ui.footer(game, navigation + confirmBack());
   }

   private void stats(ModernUi ui, Game game, DrawStatsScreen menu) {
      Pokemon p = menu.pokemon;
      if (p == null) return;
      String[] pages = {"Overview", "Moves", "Stats"};
      ui.fullScreen(game, displayName(p), pages[Math.max(0, Math.min(2, menu.currIndex))] + "  /  " + (menu.currIndex + 1) + " of 3");
      ui.panel(game, 6, 77, 148, 40);
      if (!PmdBattleSprites.portrait(game, p, 10, 81, 41, 32)) sprite(game, p.sprite, 10, 81, 41, 32, true);
      ui.text(game, "Lv " + p.level, 59, 111, 8, ModernUi.ACCENT);
      ui.text(game, p.gender == null ? "" : p.gender.equals("male") ? "♂" : p.gender.equals("female") ? "♀" : "", 94, 111, 7, ModernUi.MUTED);
      if (p.isShiny) ui.text(game, "SHINY", 114, 110, 5.2f, ModernUi.GOLD);
      ui.fitText(game, typeNames(p), 59, 99, 5.5f, 86, ModernUi.INK);
      String detail = menu.currIndex == 2 ? "Trainer  " + (p.previousOwner == null ? "—" : p.previousOwner.name) : "Status  " + condition(p);
      ui.fitText(game, detail, 59, 88, 5.1f, 86, ModernUi.MUTED);
      if (menu.currIndex == 0) {
         int hp = stat(p.currentStats, "hp"), max = stat(p.maxStats, "hp");
         ui.panel(game, 6, 16, 148, 56);
         ui.text(game, "HEALTH", 13, 65, 5.2f, ModernUi.MUTED);
         ui.text(game, hp + " / " + max, 103, 66, 7, ModernUi.INK);
         ui.bar(game, 13, 50, 134, 5, max > 0 ? (float)hp / max : 0, healthColor(hp, max));
         ui.text(game, "Experience", 13, 43, 5.5f, ModernUi.MUTED);
         ui.fitText(game, Integer.toString(p.exp), 102, 43, 5.5f, 45, ModernUi.INK);
         ui.text(game, "Next level", 13, 31, 5.5f, ModernUi.MUTED);
         ui.fitText(game, Integer.toString(Math.max(0, p.calcExpForLevel(p.level + 1) - p.exp)), 102, 31, 5.5f, 45, ModernUi.ACCENT);
      } else if (menu.currIndex == 1) {
         for (int i = 0; i < 4; i++) {
            String move = p.attacks != null && i < p.attacks.length ? p.attacks[i] : null;
            ui.panel(game, 6, 59 - i * 14, 148, 12);
            ui.text(game, String.valueOf(i + 1), 12, 67 - i * 14, 5, ModernUi.GOLD);
            ui.fitText(game, move == null ? "—" : title(move), 25, 68 - i * 14, 6.5f, 122, move == null ? ModernUi.MUTED : ModernUi.INK);
         }
      } else {
         String[] names = {"Attack", "Defense", "Sp. Attack", "Sp. Defense", "Speed"};
         String[] keys = {"attack", "defense", "specialAtk", "specialDef", "speed"};
         ui.panel(game, 6, 16, 148, 56);
         for (int i = 0; i < names.length; i++) {
            float y = 66 - i * 9.3f;
            ui.text(game, names[i], 13, y, 5.4f, ModernUi.MUTED);
            ui.text(game, Integer.toString(stat(p.maxStats, keys[i])), 123, y, 5.8f, ModernUi.INK);
            ui.bar(game, 64, y - 5, 49, 3, Math.min(1, stat(p.maxStats, keys[i]) / 300f), ModernUi.ACCENT);
         }
      }
      ui.footer(game, "←→ Page   ↑↓ Pokémon   " + key(InputProcessor.keyboardB) + " Back");
   }

   private void controls(ModernUi ui, Game game, DrawControls action) {
      if (action.remove) return;
      ui.fullScreen(game, "A NEW ADVENTURE", action.displayControls ? "While your island takes shape…" : "Field notes");
      ui.panel(game, 8, 43, 144, 73);
      if (action.displayControls) {
         String[][] controls = {
            {"Movement", key(InputProcessor.keyboardUp) + " / " + key(InputProcessor.keyboardDown) + " / " + key(InputProcessor.keyboardLeft) + " / " + key(InputProcessor.keyboardRight)},
            {"Confirm / interact", key(InputProcessor.keyboardA)}, {"Back / run", key(InputProcessor.keyboardB)},
            {"Menu", key(InputProcessor.keyboardStart)}, {"Field selection", key(InputProcessor.keyboardL) + " / " + key(InputProcessor.keyboardR)}
         };
         for (int i = 0; i < controls.length; i++) {
            ui.text(game, controls[i][0], 15, 108 - i * 12, 5.8f, ModernUi.INK);
            ui.fitText(game, controls[i][1], 95, 108 - i * 12, 5.8f, 47, ModernUi.ACCENT);
         }
      } else {
         String tip = action.currTrainerTip.replace("TRAINER TIPS!", "").trim();
         ui.text(game, "TRAINER TIP", 15, 108, 6, ModernUi.ACCENT);
         ui.wrapped(game, tip, 15, 95, 6.3f, 129, 5, ModernUi.INK);
      }
      ui.footer(game, key(InputProcessor.keyboardA) + " Next tip");
   }

   private void levelUp(ModernUi ui, Game game, GainExpAnimationGen2.ShowLevelUpStats action) {
      Pokemon p = game.player.currPokemon;
      if (p == null) return;
      ui.panel(game, 75, 43, 79, 74);
      ui.rect(game, 76, 96, 77, 20, ModernUi.INK);
      ui.fitText(game, displayName(p), 81, 112, 6, 66, ModernUi.PAPER);
      ui.text(game, "Level " + p.level, 81, 102, 5, ModernUi.GOLD);
      String[] labels = {"Attack", "Defense", "Sp. Attack", "Sp. Defense", "Speed"};
      for (int i = 0; i < action.allStats.length; i++) {
         float top = 89 - i * 8.4f;
         ui.text(game, labels[i], 81, top, 4.8f, ModernUi.MUTED);
         ui.fitText(game, Integer.toString(stat(p.maxStats, action.allStats[i])), 133, top, 5.2f, 15, ModernUi.INK);
      }
      if (action.timer >= 82) ui.footer(game, key(InputProcessor.keyboardA) + " Continue");
   }

   private void nickname(ModernUi ui, Game game, Pokemon.SetNickname action) {
      ui.panel(game, 9, 44, 142, 47);
      ui.text(game, "NICKNAME", 16, 84, 6, ModernUi.ACCENT);
      String name = letters(action.text);
      if (!action.disabled && action.avatarAnimCounter >= 12) name += "_";
      ui.fitText(game, name, 16, 71, 8, 128, ModernUi.INK);
      ui.fitText(game, action.disabled ? "Confirm your changes below" : "Type a name  /  Backspace Delete", 16, 54, 4.3f, 128, ModernUi.MUTED);
   }

   private void intro(ModernUi ui, Game game, Action next, String title, int remaining, int total) {
      Action target = next;
      for (int i = 0; i < 16 && target != null && !ui.covers(target); i++) target = target.nextAction;
      if (target != null && target != next && target == target.nextAction) target = null;
      if (target != null && !(target instanceof DrawPokemonMenu.Intro) && !(target instanceof DrawStatsScreen.Intro) && !(target instanceof DrawPlayerMenu.Intro)) {
         ui.renderAction(game, target);
      } else ui.fullScreen(game, title, "Adventure journal");
      float fraction = Math.max(0, Math.min(1, remaining / (float)Math.max(1, total)));
      if (fraction > 0) {
         ui.rect(game, -200, 12, 560, 108, new Color(ModernUi.PAPER.r, ModernUi.PAPER.g, ModernUi.PAPER.b, fraction));
         ui.rect(game, 0, 12, 160 * (1 - fraction), 1.5f, ModernUi.GOLD);
      }
   }

   private void outro(ModernUi ui, Game game, Menu previous, int remaining) {
      if (previous != null) parent(ui, game, previous);
      float alpha = Math.max(0, Math.min(1, remaining / 34f));
      if (alpha > 0) ui.rect(game, -200, -200, 560, 544, new Color(ModernUi.PAPER.r, ModernUi.PAPER.g, ModernUi.PAPER.b, alpha));
   }

   private void parent(ModernUi ui, Game game, Action action) {
      if (action != null) ui.renderAction(game, action);
   }

   private static void sprite(Game game, Sprite source, float x, float y, float maxWidth, float maxHeight, boolean flipX) {
      if (source == null || source.getRegionWidth() <= 0 || source.getRegionHeight() <= 0) return;
      float scale = Math.min(maxWidth / source.getRegionWidth(), maxHeight / source.getRegionHeight());
      float width = source.getRegionWidth() * scale, height = source.getRegionHeight() * scale;
      // Copy before changing any transform/flip: these sprites also belong to the world and battles.
      Sprite copy = new Sprite(source);
      copy.setRotation(0);
      copy.setScale(1);
      if (flipX) copy.flip(true, false);
      copy.setBounds(x + (maxWidth - width) / 2, y + (maxHeight - height) / 2, width, height);
      copy.setOriginCenter();
      copy.draw(game.uiBatch);
   }

   private static void region(Game game, TextureRegion source, float x,float y,float w,float h) {
      float scale = Math.min(w/source.getRegionWidth(),h/source.getRegionHeight());
      game.uiBatch.draw(source,x+(w-source.getRegionWidth()*scale)/2,y,source.getRegionWidth()*scale,source.getRegionHeight()*scale);
   }

   private static int avatarFrame(int count, int timer) { return Math.floorMod((24 - timer) / 6, Math.max(1, count)); }
   private static int stat(Map<String, Integer> stats, String key) { return stats == null ? 0 : Math.max(0, stats.getOrDefault(key, 0)); }
   private static Color healthColor(int hp, int max) { return hp <= 0 || hp * 5 <= max ? ModernUi.RED : hp * 2 <= max ? ModernUi.GOLD : ModernUi.ACCENT; }
   private static String displayName(Pokemon pokemon) { return pokemon == null ? "POKéMON" : pokemon.nickname == null ? "POKéMON" : pokemon.nickname; }
   private static String condition(Pokemon pokemon) {
      if (pokemon.isEgg) return "Egg";
      if (stat(pokemon.currentStats, "hp") <= 0) return "FNT";
      if (pokemon.status == null || pokemon.status.equals("confuse") || pokemon.status.equals("attract")) return "OK";
      switch (pokemon.status) {
         case "poison": case "toxic": return "PSN";
         case "paralyze": return "PAR";
         case "freeze": return "FRZ";
         case "sleep": return "SLP";
         case "burn": return "BRN";
         default: return title(pokemon.status);
      }
   }
   private static String typeNames(Pokemon pokemon) {
      if (pokemon.types == null || pokemon.types.isEmpty()) return "";
      String first = pokemon.types.get(0);
      if (pokemon.types.size() < 2 || first.equals(pokemon.types.get(1))) return title(first);
      return title(first) + " / " + title(pokemon.types.get(1));
   }
   private static String title(String word) {
      if (word == null || word.isEmpty()) return "";
      String normalized = word.replace('_', ' ').toLowerCase(Locale.ROOT);
      return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
   }
   private static String key(int code) { String name = Keys.toString(code); return name == null ? "?" : name; }
   private static String confirmBack() { return key(InputProcessor.keyboardA) + " Confirm   " + key(InputProcessor.keyboardB) + " Back"; }
   private static String letters(List<Character> letters) { StringBuilder text = new StringBuilder(); for (Character letter : letters) text.append(letter); return text.toString(); }
   private static String pick(String[] values, int index) { return values[Math.max(0, Math.min(values.length - 1, index))]; }
   private static String pick(List<String> values, int index) { return values.isEmpty() ? "" : values.get(Math.max(0, Math.min(values.size() - 1, index))); }

   private static final class SetupRow {
      final int index, kind;
      final String label, value;
      SetupRow(int index, String label, String value, int kind) { this.index = index; this.label = label; this.value = value; this.kind = kind; }
   }
}
