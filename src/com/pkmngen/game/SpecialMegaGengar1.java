package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.pkmngen.game.util.SpriteProxy;
import com.pkmngen.game.util.TextureCache;
import java.util.HashMap;

class SpecialMegaGengar1 extends Pokemon {
   public SpecialMegaGengar1(int level) {
      super("mgengar", level);
      this.nickname="Mega Gengar";
      this.baseStats.put("hp", 300);
      this.baseStats.put("attack", 65);
      this.baseStats.put("defense", 80);
      this.baseStats.put("specialAtk", 170);
      this.baseStats.put("specialDef", 95);
      this.baseStats.put("speed", 130);
      this.baseStats.put("catchRate", 3);
      Texture pokemonText = TextureCache.get(Gdx.files.internal("pokemon/mgengar_base1.png"));
      this.breathingSprite = new SpriteProxy(pokemonText, 0, 0, 56, 56);
      pokemonText = TextureCache.get(Gdx.files.internal("pokemon/mgengar_over1.png"));
      this.sprite = new SpriteProxy(pokemonText, 0, 0, 56, 56);
      this.learnSet.put(1, new String[]{"Shadow Claw", "Night Shade", "Lick", null});
      this.types.add("Ghost");
      this.types.add("Poison");
      this.getCurrentAttacks();
      this.calcMaxStats();
      this.currentStats = new HashMap<>(this.maxStats);
      this.initHabitatValues();
   }

   /** The shipped boss has art but no standalone ASM species definition. */
   static boolean initializeSpecies(Specie variant,String name,Pokemon.Generation generation) {
      if(name==null||!(name.equalsIgnoreCase("mgengar")||name.equalsIgnoreCase("mega gengar")))return false;
      // Enter through Specie.init as well as the encounter constructor, so a
      // caught boss can reload in a fresh process without any missing ASM file.
      variant.init("gengar",generation);
      variant.name="mgengar";variant.modNickname="Mega Gengar";
      variant.learnSet=new HashMap<>(variant.learnSet);
      variant.baseStats.put("hp",300);variant.baseStats.put("attack",65);variant.baseStats.put("defense",80);
      variant.baseStats.put("specialAtk",170);variant.baseStats.put("specialDef",95);variant.baseStats.put("speed",130);
      variant.baseStats.put("catchRate",3);
      variant.learnSet.put(1,new String[]{"Shadow Claw","Night Shade","Lick",null});
      Specie.gen2Attacks.put("mgengar",variant.learnSet);
      Specie.gen2Evos.put("mgengar",new HashMap<>(Specie.gen2Evos.get("gengar")));
      Pokemon.baseSpecies.put("mgengar",Pokemon.baseSpecies.get("gengar"));
      return true;
   }
}
