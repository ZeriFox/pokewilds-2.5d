#!/usr/bin/env python3
"""One-time, checked presentation migration; never part of normal game startup."""
from pathlib import Path
import hashlib
import json
import re
import importlib.util

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'src/com/pkmngen/game'
MARKER=ROOT/'docs/PRESENTATION-REWORK-2026-10-07.md'
CHANGED=('BwAssets.java','JohtoRenderer.java','JohtoBattleRenderer.java','ModernPartyUi.java','ModernInventoryUi.java','PmdBattleSprites.java','util/SpriteProxy.java')

def replace_once(text, old, new):
    if text.count(old)!=1:
        raise ValueError('Expected one reviewed source anchor, found '+str(text.count(old))+': '+old[:100])
    return text.replace(old,new,1)

def section(text,start,end,new):
    a=text.index(start);b=text.index(end,a+len(start))
    return text[:a]+new+'\n'+text[b:]

def save(name,text):
    (SRC/name).write_text(text,encoding='utf-8',newline='\n')

def read(name):
    return (SRC/name).read_text(encoding='utf-8')

FRLG_HELPERS='''   private static final java.util.IdentityHashMap<TextureRegion, com.badlogic.gdx.graphics.g2d.NinePatch> frlgPatches = new java.util.IdentityHashMap<>();

   static void frlgScreen(ModernUi ui, Game game, String title, String subtitle) {
      ui.fullScreen(game,title,subtitle);
      TextureRegion background=BwAssets.get().named("ui_frlg_background");
      if(background!=null) {
         Color previous=new Color(game.uiBatch.getColor());
         game.uiBatch.setColor(Color.WHITE);
         game.uiBatch.draw(background,4,16,152,102);
         game.uiBatch.setColor(previous);
      }
   }

   static void frlgPanel(ModernUi ui, Game game, float x,float y,float w,float h,boolean selected,boolean leader) {
      TextureRegion art=BwAssets.get().named(leader?"ui_frlg_leader":"ui_frlg_row");
      if(art==null) {ui.panel(game,x,y,w,h);return;}
      com.badlogic.gdx.graphics.g2d.NinePatch patch=frlgPatches.get(art);
      if(patch==null) {
         if(frlgPatches.size()>4)frlgPatches.clear();
         patch=new com.badlogic.gdx.graphics.g2d.NinePatch(art,3,3,3,3);
         frlgPatches.put(art,patch);
      }
      Color previous=new Color(game.uiBatch.getColor());
      game.uiBatch.setColor(Color.WHITE);
      if(selected)ui.rect(game,x-.7f,y-.7f,w+1.4f,h+1.4f,ModernUi.GOLD);
      patch.draw(game.uiBatch,x,y,w,h);
      ui.rect(game,x+2,y+2,w-4,h-4,selected?new Color(.84f,.95f,.91f,1):new Color(.74f,.88f,.87f,1));
      game.uiBatch.setColor(previous);
   }

   static void frlgRow(ModernUi ui,Game game,String label,float x,float y,float w,float h,boolean selected,float reserve) {
      frlgPanel(ui,game,x,y,w,h,selected,false);
      ui.fitText(game,label,x+4,y+h-3.5f,Math.min(6f,h*.46f),Math.max(1,w-8-reserve),ModernUi.INK);
   }

'''

PARTY='''   private void party(ModernUi ui, Game game, DrawPokemonMenu menu, int selected, int scroll, int moving) {
      List<Pokemon> pokemon=DrawPokemonMenu.allPokemon==null?game.player.pokemon:DrawPokemonMenu.allPokemon;
      boolean storage=menu.isStorageChest&&pokemon!=game.player.pokemon;
      frlgScreen(ui,game,storage?"STORAGE":"YOUR PARTY",(storage?"Pokemon at this location":"Choose a Pokemon")+"  /  "+pokemon.size());
      if(pokemon.isEmpty()) {
         frlgPanel(ui,game,9,55,142,43,false,true);
         ui.text(game,"No Pokemon here yet.",17,80,7,ModernUi.INK);
      }
      for(int i=0;i<6&&scroll+i<pokemon.size();i++) {
         int index=scroll+i;
         if(index<0)continue;
         Pokemon p=pokemon.get(index);
         boolean leader=i==0,chosen=i==selected;
         float x=leader?6:61,y=leader?45:96-(i-1)*19,w=leader?50:93,h=leader?70:18;
         frlgPanel(ui,game,x,y,w,h,chosen,leader);
         if(moving==index)ui.rect(game,x,y,w,1.2f,ModernUi.GOLD);
         float px=leader?x+10:x+2,py=leader?y+37:y+1,pw=leader?30:16,ph=leader?28:16;
         if(!PmdBattleSprites.portrait(game,p,px,py,pw,ph)) {
            if(!p.isEgg&&p.sprite!=null)sprite(game,p.sprite,px,py,pw,ph,true);
            else if(p.avatarSprites!=null&&!p.avatarSprites.isEmpty())
               sprite(game,p.avatarSprites.get(chosen?avatarFrame(p.avatarSprites.size(),DrawPokemonMenu.avatarAnimCounter):0),px,py,pw,ph,false);
         }
         String name=i<menu.ableWords.size()?menu.ableWords.get(i):displayName(p);
         ui.fitText(game,name,leader?x+4:x+20,leader?y+32:y+13.5f,leader?6:5.5f,leader?w-8:47,ModernUi.INK);
         if(p.isEgg) {
            ui.text(game,"EGG",leader?x+4:x+70,leader?y+23:y+6,4.4f,ModernUi.ACCENT);
            continue;
         }
         int hp=stat(p.currentStats,"hp"),max=stat(p.maxStats,"hp");
         String level="Lv "+p.level+(p.isShiny?" *":"");
         ui.fitText(game,level,leader?x+4:x+69,leader?y+23:y+13.5f,4.4f,leader?42:21,ModernUi.INK);
         String status=condition(p);
         if(!status.equals("OK"))ui.fitText(game,status,leader?x+28:x+20,leader?y+23:y+5,3.8f,20,ModernUi.RED);
         else if(!leader)ui.fitText(game,hp+"/"+max,x+20,y+5,3.8f,25,ModernUi.INK);
         ui.bar(game,leader?x+4:x+48,leader?y+13:y+4,leader?42:41,3.4f,max>0?(float)hp/max:0,healthColor(hp,max));
         if(leader)ui.fitText(game,hp+" / "+max,x+4,y+8,4.4f,42,ModernUi.INK);
      }
      if(pokemon.size()>6)ui.fitText(game,(Math.max(0,scroll)+1)+" - "+Math.min(scroll+6,pokemon.size())+" / "+pokemon.size(),7,24,4.2f,48,ModernUi.PAPER);
      ui.footer(game,(menu.isStorageChest?DesktopControls.horizontal()+" Party / storage   ":DesktopControls.vertical()+" Select   ")+confirmBack());
   }
'''

BUILDING='''   private boolean building(Game game, Tile tile, float x, float y, String name) {
      String identity=BwAssets.objectName(tile);
      if(identity!=null&&java.util.Arrays.asList("chair","couch","bed","desk","table","shelf","wardrobe","pot").contains(identity)) {
         TextureRegion art=assets.named(identity);
         if(art==null)return false;
         float scale=VisualGeometry.fitScale(art.getRegionWidth(),art.getRegionHeight(),24,29);
         float width=assets.worldWidth(art,art.getRegionWidth()*scale),height=assets.worldHeight(art,art.getRegionHeight()*scale);
         assetShadow(art,x+8,y+7,width*.28f,2.4f);
         upright(art,x+8-width*.5f,y+7,.16f,width,height,WHITE);
         return true;
      }
      if(name.contains("bridge")||name.contains("stairs")) {
         floor(assets.named(name.contains("stairs")?"steps":"wood_floor"),x,y,16,16,.1f,WHITE,geometry);return true;
      }
      if(name.contains("fence")||name.contains("gate")) {
         if(tile.isSolid)upright(assets.named("fence_wood"),x,y+8,.1f,16,12,WHITE);
         else floor(assets.named("wood_floor"),x,y,16,16,.1f,WHITE,geometry);
         return true;
      }
      if(name.contains("roof")) {
         floor(assets.named("roof"),x,y,16,16,22,WHITE,geometry);return true;
      }
      if(name.contains("door")||name.contains("pkmnmansion_ext_locked")) {
         floor(assets.named("steps"),x,y,16,12,.1f,WHITE,geometry);
         upright(assets.named(name.contains("locked")?"door_locked":tile.isSolid?"door":"door_open"),x,y+14,.1f,16,22,WHITE);
         return true;
      }
      String upper=tile.nameUpper==null?"":tile.nameUpper.toLowerCase(java.util.Locale.ROOT);
      String lower=tile.name==null?"":tile.name.toLowerCase(java.util.Locale.ROOT);
      boolean caveWall=lower.startsWith("cave")&&upper.isEmpty()&&!lower.contains("regi");
      boolean structural=tile.isSolid&&("wall".equals(identity)||"window".equals(identity)||name.contains("pkmnmansion_ext")
         ||caveWall&&VisualGeometry.buildingWall(lower,upper,true));
      if(structural) {
         TextureRegion face=assets.named(caveWall?"cliff_dark":name.contains("ruin")?"ruin_wall":"wall");
         TextureRegion cap=assets.named(caveWall?"mountain":name.contains("ruin")?"ruin_floor":"wall_cap");
         if(cap==null)cap=assets.named("wood_floor");
         float height=caveWall?16:24,z=-y;
         Geometry mesh=batch(geometry,face.getTexture());
         if(!structuralNeighbour(game,x,y-16))quad(mesh,x,0,z,x+16,0,z,x+16,height,z,x,height,z,face,WHITE);
         if(!structuralNeighbour(game,x,y+16))quad(mesh,x+16,0,z-16,x,0,z-16,x,height,z-16,x+16,height,z-16,face,color(.82f,.85f,.87f));
         if(!structuralNeighbour(game,x-16,y))quad(mesh,x,0,z-16,x,0,z,x,height,z,x,height,z-16,face,color(.88f,.90f,.91f));
         if(!structuralNeighbour(game,x+16,y))quad(mesh,x+16,0,z,x+16,0,z-16,x+16,height,z-16,x+16,height,z,face,color(.73f,.78f,.81f));
         floor(cap,x,y,16,16,height,WHITE,geometry);
         if(name.contains("window")) {
            TextureRegion window=assets.named("window");
            if(window!=null)quad(batch(geometry,window.getTexture()),x+3,7,z+.05f,x+13,7,z+.05f,x+13,19,z+.05f,x+3,19,z+.05f,window,WHITE);
         }
         return true;
      }
      if(upper.isEmpty()&&lower.contains("floor"))return true;
      return false;
   }

   private boolean structuralNeighbour(Game game,float x,float y) {
      Tile tile=game.map.tiles.get(lookup.set(x,y));
      if(tile==null||!tile.isSolid)return false;
      String identity=BwAssets.objectName(tile);
      return "wall".equals(identity)||"window".equals(identity);
   }
'''

BATTLE_FINISH='''   /** Restore projection only: painting margins here used to erase screen-wide effects. */
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

   // Existing boss/night overlays already draw across the full canvas in order.
   private void marginOverlay(float x,float y,float w,float h,Color color) {}
'''

def apply():
    if MARKER.exists():
        print('Checked presentation migration already applied; source left untouched.')
        return
    before={name:hashlib.sha256((SRC/name).read_bytes()).hexdigest() for name in CHANGED}
    old_manifest=(ROOT/'baseline/modernization-scope.json').read_bytes()
    old_verifier=(ROOT/'tools/verify_visual_scope.py').read_bytes()
    text=read('BwAssets.java')
    text=replace_once(text,'"visual/landscape/world-atlas.png"','"visual/stardew/world-atlas.png"')
    text=replace_once(text,'"visual/landscape/world-atlas.json"','"visual/stardew/world-atlas.json"')
    save('BwAssets.java',text)

    text=read('JohtoRenderer.java')
    text=replace_once(text,'if (building(tile, x, y, names)) return;','if (building(game, tile, x, y, names)) return;')
    text=section(text,'   private boolean building(', '   private void prism(',BUILDING)
    save('JohtoRenderer.java',text)

    text=read('ModernPartyUi.java')
    text=replace_once(text,'   private void party(',FRLG_HELPERS+'   private void party(')
    text=section(text,'   private void party(', '   private void stats(',PARTY)
    save('ModernPartyUi.java',text)

    text=read('ModernInventoryUi.java')
    text=text.replace('ui.fullScreen(game,','ModernPartyUi.frlgScreen(ui,game,')
    text=replace_once(text,'ui.row(game,(sorting && i+offset==sortingIndex?"* ":"")+name,9,y,142,rowHeight-1,i==cursor,hasCount?24:0);',
        'ModernPartyUi.frlgRow(ui,game,(sorting && i+offset==sortingIndex?"* ":"")+name,9,y,142,rowHeight-1,i==cursor,hasCount?24:0);')
    text=text.replace('i==cursor?ModernUi.PAPER:ModernUi.MUTED','ModernUi.INK')
    save('ModernInventoryUi.java',text)

    text=read('JohtoBattleRenderer.java')
    text=section(text,'   /** After uiBatch.end():', '   private static boolean isSpecialIntro(',BATTLE_FINISH)
    text=replace_once(text,'"visual/landscape/world-atlas.png"','"visual/stardew/world-atlas.png"')
    # Physically meaningful projected ground: stable horizon and finer far texels.
    text=replace_once(text,'float depth=(y-76-offsetY)/212f, perspective=.5f+depth*.9f;',
        'float depth=(y-76-offsetY)/212f, perspective=.28f+depth*1.55f;')
    save('JohtoBattleRenderer.java',text)

    text=read('util/SpriteProxy.java')
    text=replace_once(text,'      super.draw(batch);',
        '      com.pkmngen.game.Game game=com.pkmngen.game.Game.staticGame;\n'
        '      if(game!=null&&game.modernUi!=null&&game.johtoBattleRenderer!=null\n'
        '         &&game.johtoBattleRenderer.drawScreenEffect(this,batch))return;\n'
        '      super.draw(batch);')
    save('util/SpriteProxy.java',text)

    text=read('PmdBattleSprites.java')
    text=replace_once(text,'      float scale = baseScale * source.getWidth() / (back ? 48f : pokemon.specie.sprite.getWidth());',
        '      float scale = baseScale * source.getWidth() / (back ? 48f : pokemon.specie.sprite.getWidth()) * (back ? 1f : .90f);')
    text=replace_once(text,'      draw.setBounds(source.getX() + source.getWidth()*.5f - frame.anchorX*scale,\n         groundY - frame.anchorY*scale*fraction, width, height);',
        '      PmdPokemonSprites.AnimationBounds contact=PmdPokemonSprites.get().bounds(pokemon,facing,"Idle");\n'
        '      float contactLift=contact==null?0:-Math.min(0,contact.minY)*scale;\n'
        '      if(fraction>=.99f&&source.getColor().a>0) {\n'
        '         ModernUi ui=ModernUi.get(game);\n'
        '         float radius=Math.min(back?13:10,Math.max(4,width*.32f));\n'
        '         Color shade=new Color(.08f,.12f,.13f,.22f*source.getColor().a);\n'
        '         for(int row=0;row<5;row++){\n'
        '            float dy=(row-2)/2.5f,half=radius*(float)Math.sqrt(Math.max(0,1-dy*dy));\n'
        '            ui.rect(game,source.getX()+source.getWidth()*.5f-half,groundY-.7f+row*.4f,half*2,.4f,shade);\n'
        '         }\n'
        '      }\n'
        '      draw.setBounds(source.getX() + source.getWidth()*.5f - frame.anchorX*scale,\n'
        '         groundY + contactLift - frame.anchorY*scale*fraction, width, height);')
    save('PmdBattleSprites.java',text)

    # Archive the previous review. A normal build/audit NEVER renews approvals.
    # Only these seven explicitly reviewed presentation sources may differ.
    archive=ROOT/'baseline/presentation-before-stardew';archive.mkdir(exist_ok=True)
    (archive/'modernization-scope.json').write_bytes(old_manifest)
    (archive/'verify_visual_scope.py').write_bytes(old_verifier)
    manifest=json.loads(old_manifest)
    allowed={'src/com/pkmngen/game/'+n for n in CHANGED}
    for group in ('modified_original_sources','added_sources'):
        for path, entry in manifest[group].items():
            value=hashlib.sha256((ROOT/path).read_bytes()).hexdigest()
            if value!=entry['reviewed_sha256'] and path not in allowed:
                raise ValueError('Unreviewed source change outside presentation migration: '+path)
            if path in allowed:entry['reviewed_sha256']=value
    # Keep the source allowlists and protected gameplay/save checks unchanged.
    spec=importlib.util.spec_from_file_location('scope_review',ROOT/'tools/verify_visual_scope.py')
    verifier=importlib.util.module_from_spec(spec);spec.loader.exec_module(verifier)
    report,patch=verifier.audit(inventory=True)
    manifest['reviewed_patch_sha256']=report['complete_patch_sha256']
    manifest['purpose']+=' Presentation follow-up: pinned Stardew atlas, FRLG reusable UI frames, structural house walls, full-viewport move overlays and battle contact shadows; seven source files reviewed, no rule/save/input source changes.'
    out=json.dumps(manifest,indent=2,ensure_ascii=False)+'\n'
    (ROOT/'baseline/modernization-scope.json').write_text(out,encoding='utf-8',newline='\n')
    frozen=hashlib.sha256(out.encode()).hexdigest()
    verifier_text=old_verifier.decode()
    verifier_text=re.sub(r'FROZEN_MANIFEST_SHA256 = "[0-9a-f]{64}"','FROZEN_MANIFEST_SHA256 = "'+frozen+'"',verifier_text,count=1)
    (ROOT/'tools/verify_visual_scope.py').write_text(verifier_text,encoding='utf-8',newline='\n')
    (archive/'migration-review.json').write_text(json.dumps({'allowedSources':list(CHANGED),'before':before,
        'after':{n:hashlib.sha256((SRC/n).read_bytes()).hexdigest() for n in CHANGED},'previousManifestSha256':hashlib.sha256(old_manifest).hexdigest(),
        'protectedGameplaySourcesUnchanged':True,'completePatchSha256':report['complete_patch_sha256']},indent=2)+'\n')
    MARKER.parent.mkdir(exist_ok=True)
    MARKER.write_text('''# Presentation rework, 7 October 2026

Implemented from feature/stardew-world-battle, preserving the terrain/habitat/input work on main.

- Curated Stardew source crops for grass, desert, snow, cave, volcano, rocks, trees and house materials/furniture. Alpha is trimmed for props; rectangular sprites are not sliced into 16px pieces. Every crop and recolor has provenance. Mechanical assets not replaced in this pass retain the previous credited landscape art.
- Reusable FireRed/LeafGreen menu frames for party/storage and bag. Party uses a lead slot plus five rows without changing the original menu state or selection rules. The supplied sheet is Pokemon Menu, not the FRLG bag screen.
- Full structural walls and caps, neighbour-aware exposed sides, proper full furniture sprites, intact door collision semantics.
- Wide battle overlays survive the final composition pass. Authored large screen effects expand proportionally to cover the viewport; small targeted effects and Pokemon are not stretched. Their original actions/timings are preserved.
- Diagonal PMD poses retained; stable contact offset, contact shadows and a slightly smaller distant opponent. Arena ground uses stronger perspective. This is layered 2.5D, not a new 3D combat simulator.
- Existing WASD/mouse bindings, graveyard atmosphere, world elevation, field-action rendering and habitat filtering are preserved, not reimplemented.

No new source PNGs are claimed as original or freely licensed. Read the exact source/rights records before redistribution. All existing credits are retained.

Verification commands and outcomes belong to the matching CI run and release BUILD-INFO. This document alone is not evidence that a test passed. Remaining work includes a full manual campaign, all special move effects and exhaustive map-generation visual review.
''',encoding='utf-8')
    print('Applied checked presentation changes:', ', '.join(CHANGED))

if __name__=='__main__':
    apply()
