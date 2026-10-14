package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local sandbox rules. The virtual supply never becomes inventory in a save or network packet. */
final class CreativeMode {
   static final String FILE="creative-mode.json";
   static final int SUPPLY=999999;
   private static PkmnMap world;
   private static Player owner;
   private static boolean enabled;
   private static Set<String> materials=Collections.emptySet();
   private static List<String> catalog;
   static String warning="";
   private CreativeMode() {}

   static boolean local(Game game) {return game!=null&&game.type==null&&game.map!=null&&game.player!=null&&game.player.type==Player.Type.LOCAL;}
   static void sync(Game game) {
      if(game==null||game.map==null||game.player==null)return;
      if(world==game.map&&owner==game.player)return;
      world=game.map;owner=game.player;enabled=false;warning="";materials=collectMaterials(owner);
      if(!local(game))return;
      Path path=path(game);
      if(!Files.exists(path))return;
      try {
         checkPath(path);
         JsonValue root=new JsonReader().parse(Files.readString(path,StandardCharsets.UTF_8));
         if(root.getInt("schema",0)!=1||!root.has("enabled")||!root.get("enabled").isBoolean())throw new IOException("Invalid creative mode state");
         if(Objects.equals(root.getString("player",""),identity(owner)))enabled=root.getBoolean("enabled");
      } catch(IOException|RuntimeException error) {warning="Creative settings could not be read. Adventure mode retained.";}
   }
   static void newWorld(Game game) {world=game.map;owner=game.player;enabled=false;warning="";materials=collectMaterials(owner);}
   static void reload(Game game) {world=null;owner=null;sync(game);}
   static boolean active(Player player) {
      Game game=Game.staticGame;
      if(!local(game)||game.player!=player)return false;
      sync(game);return enabled;
   }
   static boolean infinite(Player player,String item) {return active(player)&&materials.contains(item);}
   static Set<String> materials(Player player) {return active(player)?materials:Collections.emptySet();}
   static String setEnabled(Game game,boolean value) {
      if(!local(game))return "Creative mode is available in Local adventures only.";
      sync(game);enabled=value;warning="";
      return value?"Creative ON. Resources are unlimited; save the adventure to keep this mode.":"Adventure mode restored. Your owned inventory is unchanged.";
   }
   static void save(Game game)throws IOException {
      if(!local(game))return;
      sync(game);Path path=path(game),temp=path.resolveSibling(FILE+".tmp"),backup=path.resolveSibling(FILE+".bak");
      if(!enabled&&!Files.exists(path))return;
      checkPath(path);checkPath(temp);checkPath(backup);
      JsonValue root=new JsonValue(JsonValue.ValueType.object);
      root.addChild("schema",new JsonValue(1));root.addChild("enabled",new JsonValue(enabled));root.addChild("player",new JsonValue(identity(game.player)));
      try {
         Files.writeString(temp,root.toJson(JsonWriter.OutputType.json)+"\n",StandardCharsets.UTF_8);
         if(Files.exists(path))Files.copy(path,backup,StandardCopyOption.REPLACE_EXISTING);
         try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
         catch(AtomicMoveNotSupportedException error){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
      }finally{Files.deleteIfExists(temp);}
   }
   private static String identity(Player player){return player.network!=null&&player.network.id!=null?player.network.id:player.name==null?"":player.name;}
   private static Path path(Game game){return Gdx.files.local(game.map.id+".sav/"+FILE).file().toPath().toAbsolutePath();}
   private static void checkPath(Path path)throws IOException {
      for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Linked creative settings are not followed");
   }
   private static Set<String> collectMaterials(Player player) {
      Set<String> result=new LinkedHashSet<>();
      for(Map<String,Integer> recipe:player.buildTileRequirements.values())result.addAll(recipe.keySet());
      collectRecipes(result,Player.crafts);collectRecipes(result,Player.kilnCrafts);collectRecipes(result,player.fossilCrafts);
      if(player.regiCrafts!=null)collectRecipes(result,player.regiCrafts);
      // These are consumed by planting and construction independently of a crafting recipe.
      Collections.addAll(result,"berry seed","miracle seed","grass","log","soft sand","hard stone","manure","damp soil","dry soil","dry sand","mystic soil","cursed soil","ashen soil");
      return Collections.unmodifiableSet(result);
   }
   private static void collectRecipes(Set<String> result,List<Player.Craft> recipes) {for(Player.Craft recipe:recipes)for(Player.Craft ingredient:recipe.requirements)result.add(ingredient.name);}

   static List<String> catalog() {
      if(catalog!=null)return catalog;
      Set<String> names=new TreeSet<>(ExpansionDex.names());
      for(String name:Pokemon.baseSpecies.keySet()) {
         String source=name.contains("unown")?"unown":name.equals("combee_female")?"combee":name;
         if(Gdx.files.internal("pokemon/base_stats/"+source+".asm").exists()
            ||Gdx.files.internal("pokemon/credited/base_stats/"+source+".asm").exists()
            ||Gdx.files.local("mods/pokemon/"+source+"/base_stats.asm").exists())names.add(name);
      }
      catalog=Collections.unmodifiableList(new ArrayList<>(names));return catalog;
   }
   static List<String> search(String query) {
      String term=query.trim().toLowerCase(Locale.ROOT).replace(' ','_');List<String> matches=new ArrayList<>();
      for(String name:catalog())if(term.isEmpty()||name.contains(term))matches.add(name);return matches;
   }
   static String grant(Game game,String name,int level,boolean shiny) {
      if(!active(game.player))return "Enable Creative mode first.";
      if(!catalog().contains(name)||level<1||level>100)return "Select a valid Pokemon and a level from 1 to 100.";
      if(game.player.pokemon.size()>=6)return "Party full (6/6). Use Pokemon > Drop to make room; nobody was replaced.";
      Pokemon pokemon=new Pokemon(name,level,Pokemon.Generation.CRYSTAL,shiny);
      pokemon.previousOwner=game.player;pokemon.position.set(game.player.position);pokemon.mapTiles=game.map.tiles;
      game.player.pokemon.add(pokemon);
      if(game.player.currPokemon==null)game.player.currPokemon=pokemon;
      return Game.capitalize(name.replace('_',' '))+" joined your party at Lv "+level+(shiny?" (Shiny).":".");
   }

   static Map<String,Integer> inventory(Player player,Map<String,Integer> stored) {
      if(!active(player))return stored;
      return new AbstractMap<String,Integer>() {
         @Override public Integer get(Object key){return key instanceof String&&infinite(player,(String)key)?SUPPLY:stored.get(key);}
         @Override public boolean containsKey(Object key){return key instanceof String&&infinite(player,(String)key)||stored.containsKey(key);}
         @Override public Integer put(String key,Integer value){Integer prior=get(key);if(!infinite(player,key))stored.put(key,value);return prior;}
         @Override public Integer remove(Object key){Integer prior=get(key);if(!(key instanceof String)||!infinite(player,(String)key))stored.remove(key);return prior;}
         // Legacy bag sorting clears its old map before replacing it. Keep the real stacks for that replacement.
         @Override public void clear() {}
         @Override public Set<Entry<String,Integer>> entrySet(){
            Set<String> keys=new LinkedHashSet<>(stored.keySet());keys.addAll(materials(player));
            Set<Entry<String,Integer>> entries=new LinkedHashSet<>();for(String key:keys)entries.add(new SimpleImmutableEntry<>(key,get(key)));return entries;
         }
      };
   }
   static Map<String,Integer> storedAfterSort(Player player,Map<String,Integer> next,Map<String,Integer> stored) {
      if(!active(player))return next;
      Map<String,Integer> result=new LinkedHashMap<>();
      for(Map.Entry<String,Integer> entry:next.entrySet()) {
         String item=entry.getKey();if(!infinite(player,item))result.put(item,entry.getValue());
         else if(stored.containsKey(item))result.put(item,stored.get(item));
      }return result;
   }
}
