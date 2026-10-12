#!/usr/bin/env python3
"""One-use reviewed edit. Never run by normal builds; removed from final tree."""
from pathlib import Path
import ast, hashlib, importlib.util, json, re, subprocess, sys
R=Path(__file__).resolve().parents[1]
P='src/com/pkmngen/game/'
BASE='ac526d14df8409fa365e65bedf8758784ddc2d68'

def read(path): return (R/path).read_text(encoding='utf-8')
def write(path,text):
    p=R/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8',newline='\n')
def replace(text,old,new,count=1):
    if text.count(old)!=count: raise RuntimeError('Unexpected replacement count: '+old[:100])
    return text.replace(old,new)
def blob(path): return subprocess.check_output(['git','hash-object',path],cwd=R,text=True).strip()
for path,sha in {
    P+'JohtoRenderer.java':'e6795afd754e7335814672597549050784574763',
    P+'PmdPokemonSprites.java':'2bac4abce97971e0993c56ec445d270ab07647d5',
    P+'JohtoBattleRenderer.java':'c3b24541153468cf6b873f0ddb838a30a15ea0e4',
    'tools/verify_visual_scope.py':'95cdc20593e327ae980da34d7a28f2fecaa7350c',
    'baseline/modernization-scope.json':'608ad71262e25cd077c6c08725233f06853961c1'
}.items():
    if blob(path)!=sha: raise RuntimeError('Base changed: '+path)

# Preserve the preceding source review, not just its replacement digest.
archive={}
for src in ('baseline/modernization-scope.json','tools/verify_visual_scope.py'):
    dst='baseline/java-readability-step4/'+Path(src).name
    data=(R/src).read_bytes();write(dst,data.decode());archive[dst]=hashlib.sha256(data).hexdigest()

# Animated creatures share one complete Idle/Walk envelope in every direction.
s=read(P+'PmdPokemonSprites.java')
pos='   /** PMD cells are timed at 60 ticks/second; stateTime is elapsed seconds. */'
method='''   /** One species/form envelope for overworld locomotion: no frame-dependent scale
    * or foot correction. This only reads metadata and shares the existing cache. */
   public AnimationBounds overworldBounds(Pokemon pokemon) { return overworldBounds(key(pokemon)); }
   public AnimationBounds overworldBounds(String species) {
      Entry entry=entry(species);if(entry==null)return null;
      AnimationBounds found=entry.bounds.get("overworld-envelope");if(found!=null)return found;
      float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
      for(String animation:new String[]{"Idle","Walk"})
         for(String direction:new String[]{"down","down-right","right","up-right","up","up-left","left","down-left"}) {
            AnimationBounds b=bounds(species,direction,animation);if(b==null)continue;
            minX=Math.min(minX,b.minX);minY=Math.min(minY,b.minY);
            maxX=Math.max(maxX,b.maxX);maxY=Math.max(maxY,b.maxY);
         }
      if(!Float.isFinite(minX))return null;
      found=new AnimationBounds(minX,minY,maxX,maxY);entry.bounds.put("overworld-envelope",found);return found;
   }

'''
s=replace(s,pos,method+pos);write(P+'PmdPokemonSprites.java',s)

s=read(P+'JohtoRenderer.java')
s=replace(s,'   private float actorPlaneZ;', '   private float actorPlaneZ;\n   private long featheredEdges, markedRamps;\n   public long getFeatheredEdges(){return featheredEdges;}\n   public long getMarkedRamps(){return markedRamps;}')
s=replace(s,'      if(!tile.isWaterfall&&!names.contains("lavafall")) terrainSurface(tile,ground);','''      if(WorldReadability.enabled() && elevation.isSloped(tile) && !tile.isWater && !tile.isLava && !tile.isWaterfall)
         ground=assets.cell(profile.terrain("path"),x,y,seconds);
      if(!tile.isWaterfall&&!names.contains("lavafall")) terrainSurface(tile,ground);
      if(WorldReadability.enabled()) terrainFeather(game,tile);''')
s=replace(s,'      if (ModernWorldGenerator.isRamp(tile) && elevation.isSloped(tile)) ramp(tile,profile);','''      if (elevation.isSloped(tile) && !tile.isWater && !tile.isLava && !tile.isWaterfall
         && (ModernWorldGenerator.isRamp(tile) || WorldReadability.enabled())) ramp(tile,profile);''')
s=replace(s,'         float anchorY = frame.anchorY;', '''         PmdPokemonSprites.AnimationBounds envelope=WorldReadability.enabled()?sprites.overworldBounds(pokemon):null;
         if(envelope!=null)scale=Math.min(.85f,30f/Math.max(1,Math.max(envelope.width,envelope.height)));
         float contactLift=.15f;
         if(envelope!=null && !(pokemon.inWater && pokemon.drawUpper!=null))
            contactLift-=Math.min(0,envelope.minY)*scale*MathUtils.cosDeg(50f);
         float anchorY = frame.anchorY;''')
s=replace(s,'         anchored(region, pokemon.position.x + 8, pokemon.position.y + 7, .15f,','         anchored(region, pokemon.position.x + 8, pokemon.position.y + 7, contactLift,')
s=replace(s,'      upright(region, x, y, base, width, height, tint, 0f);','''      // Use the same vertical depth plane as actors. Mixing a camera-facing
      // tree plane with an actor foot plane made leaves slice through bodies.
      actorPlaneZ=-y;
      upright(region, x, y, base, width, height, tint, WorldReadability.enabled()?4f:0f);''')
start=s.index('   private void ramp(Tile tile,BiomeProfiles.Profile profile) {')
end=s.index('   private void waterfall(',start)
s=s[:start]+'''   private void ramp(Tile tile,BiomeProfiles.Profile profile) {
      float x=tile.position.x,y=tile.position.y,previousLift=surfaceLift;surfaceLift=0;
      float a=elevation.height(x+.01f,y+.01f),b=elevation.height(x+15.99f,y+.01f);
      float c=elevation.height(x+15.99f,y+15.99f),d=elevation.height(x+.01f,y+15.99f);
      boolean horizontal=Math.abs((b+c)-(a+d))>Math.abs((c+d)-(a+b));
      boolean clear=WorldReadability.enabled();
      if(clear)markedRamps++;
      for(int step=1;step<5;step++)for(int layer=0;layer<(clear?2:1);layer++) {
         float f=step/5f,margin=clear?1f:0f,thickness=clear?(layer==0?.95f:.35f):.45f;
         float shift=layer==0?0f:.95f;
         float px=horizontal?x+16*f+shift:x+margin,py=horizontal?y+margin:y+16*f+shift;
         float ex=px+(horizontal?thickness:16-2*margin),ey=py+(horizontal?16-2*margin:thickness);
         float h0=elevation.height(MathUtils.clamp(px,x+.01f,x+15.99f),MathUtils.clamp(py,y+.01f,y+15.99f));
         float h1=elevation.height(MathUtils.clamp(ex,x+.01f,x+15.99f),MathUtils.clamp(py,y+.01f,y+15.99f));
         float h2=elevation.height(MathUtils.clamp(ex,x+.01f,x+15.99f),MathUtils.clamp(ey,y+.01f,y+15.99f));
         float h3=elevation.height(MathUtils.clamp(px,x+.01f,x+15.99f),MathUtils.clamp(ey,y+.01f,y+15.99f));
         float tint=clear?(layer==0?Color.toFloatBits(.22f,.26f,.25f,.68f):Color.toFloatBits(.87f,.87f,.74f,.60f))
            :Color.toFloatBits(.72f,.69f,.62f,.34f);
         quad(translucent.buffer(whiteTexture),px,h0+.06f,-py,ex,h1+.06f,-py,ex,h2+.06f,-ey,px,h3+.06f,-ey,white,tint);
      }
      surfaceLift=previousLift;
   }

   /** Only one side owns a seam. Never feather a cliff, a wet boundary, a puzzle
    * or a building; the four vertices follow the actual shared upper surface. */
   private void terrainFeather(Game game,Tile tile) {
      if(WorldReadability.materialRank(tile)<0 || elevation.isSloped(tile))return;
      float x=tile.position.x,y=tile.position.y,previous=surfaceLift;surfaceLift=0;
      try {
         for(int side=0;side<4;side++) {
            int dx=side==2?-1:side==3?1:0,dy=side==0?-1:side==1?1:0;
            Tile other=game.map.tiles.get(lookup.set(x+16*dx,y+16*dy));
            if(!WorldReadability.feather(tile,other)||elevation.isSloped(other))continue;
            float cx=x+8+8*dx,cy=y+8+8*dy;
            float h=elevation.height(cx-dx*.01f,cy-dy*.01f),nh=elevation.height(cx+dx*.01f,cy+dy*.01f);
            if(Math.abs(h-nh)>.08f)continue;
            float x0=x,y0=y,x1=x+16,y1=y+16,w=3f;
            if(side==0)y1=y+w;else if(side==1)y0=y+16-w;else if(side==2)x1=x+w;else x0=x+16-w;
            TextureRegion uv=assets.cell(WorldMaterials.terrainName(other),x,y,seconds);if(uv==null)continue;
            float u0=MathUtils.lerp(uv.getU(),uv.getU2(),(x0-x)/16),u1=MathUtils.lerp(uv.getU(),uv.getU2(),(x1-x)/16);
            float v0=MathUtils.lerp(uv.getV2(),uv.getV(),(y0-y)/16),v1=MathUtils.lerp(uv.getV2(),uv.getV(),(y1-y)/16);
            float a0=(side==0||side==2)?1:0,a1=(side==0||side==3)?1:0;
            float a2=(side==1||side==3)?1:0,a3=(side==1||side==2)?1:0;
            Buffer buffer=translucent.buffer(uv.getTexture());
            seamVertex(buffer,x0,y0,h,u0,v0,a0);seamVertex(buffer,x1,y0,h,u1,v0,a1);seamVertex(buffer,x1,y1,h,u1,v1,a2);
            seamVertex(buffer,x1,y1,h,u1,v1,a2);seamVertex(buffer,x0,y1,h,u0,v1,a3);seamVertex(buffer,x0,y0,h,u0,v0,a0);
            featheredEdges++;
         }
      } finally {surfaceLift=previous;}
   }
   private void seamVertex(Buffer mesh,float x,float y,float height,float u,float v,float alpha) {
      vertex(mesh,x,height+.035f,-y,Color.toFloatBits(1,1,1,alpha*.85f),u,v,0);
   }

'''+s[end:]
write(P+'JohtoRenderer.java',s)

# Shared slate/teal UI palette; hitboxes, fonts, navigation and rules are untouched.
s=read(P+'ModernUi.java')
for old,new in [('203e4c','203249'),('667c80','65758b'),('fffbed','f6f9fd'),('287e78','24647f'),('d7a955','dfb774'),('c85252','c34f66'),('cfddd4','d4dfeb'),('e7eee2','e8eff7')]:
    s=replace(s,'Color.valueOf("'+old+'")','Color.valueOf("'+new+'")')
s=replace(s,'      rect(game,x+1,y-1,w,h,new Color(INK.r,INK.g,INK.b,0.18f));\n      rect(game,x,y,w,h,LINE); rect(game,x+0.65f,y+0.65f,w-1.3f,h-1.3f,PAPER);','''      rect(game,x+1,y-1,w,h,new Color(INK.r,INK.g,INK.b,0.12f));
      rect(game,x,y,w,h,LINE); rect(game,x+0.65f,y+0.65f,w-1.3f,h-1.3f,PAPER);
      // A thin upper highlight keeps panel hierarchy without extra ornaments.
      if(w>4 && h>4)rect(game,x+1,y+h-1.2f,w-2,.35f,Color.WHITE);''')
write(P+'ModernUi.java',s)

# Full viewport encounter entrance, eased but driven by the unchanged action clock.
s=read(P+'JohtoBattleRenderer.java')
a=s.index('   public void drawIntro(Game game, Action action, int remaining) {');b=s.index('   public void drawFadeOut(',a)
s=s[:a]+'''   public void drawIntro(Game game, Action action, int remaining) {
      ModernUi ui=ModernUi.get(game);
      float p=progress(action,remaining),enter=WorldReadability.ease(p/.30f);
      boolean reduced=ui.desktop.preferences.reducedMotion;
      float w=canvasWidth,h=canvasHeight,left=(160-w)*.5f,bottom=(144-h)*.5f;
      ui.rect(game,left,bottom,w,h,tint.set(ModernUi.INK).mul(1,1,1,.12f+.14f*enter));
      float slide=reduced?0:(1-enter)*w;
      ui.rect(game,left-slide,70,w,21,tint.set(ModernUi.INK).mul(1,1,1,.96f));
      ui.rect(game,left+slide,52,w,18,tint.set(ModernUi.ACCENT).mul(1,1,1,.96f));
      ui.rect(game,left-slide,90,w,.8f,ModernUi.GOLD);
      ui.rect(game,left+slide,52,w,.8f,ModernUi.GOLD);
      if(enter>.96f) {
         float angle=reduced?0:p*1.5f;
         for(int i=0;i<32;i++) {
            float a=i*MathUtils.PI2/32+angle;
            ui.rect(game,19+MathUtils.cos(a)*8,71+MathUtils.sin(a)*8,1.25f,1.25f,ModernUi.PAPER);
         }
         ui.rect(game,11,70.5f,16,1.2f,ModernUi.PAPER);ui.panel(game,16.5f,68.5f,5,5);
         ui.fitText(game,"WILD ENCOUNTER",36,84,4,113,ModernUi.GOLD);
         String name=game.battle!=null&&game.battle.oppPokemon!=null?
            (game.battle.oppPokemon.isGhost?"Unknown Pokemon":game.battle.oppPokemon.nickname):"A challenger appears";
         ui.fitText(game,name,36,76,7,113,ModernUi.PAPER);
         ui.fitText(game,"Get ready",36,63,4,113,ModernUi.PAPER);
      }
      countTransition();
   }

   public void drawWipe(Game game, Action action, int remaining) {
      ModernUi ui=ModernUi.get(game);int total=total(action,remaining);
      float p=WorldReadability.ease((total-remaining+1f)/Math.min(28f,total));
      float w=canvasWidth,h=canvasHeight,left=(160-w)*.5f,bottom=(144-h)*.5f;
      if(ui.desktop.preferences.reducedMotion)ui.rect(game,left,bottom,w,h,tint.set(ModernUi.INK).mul(1,1,1,p));
      else {
         float cover=h*.5f*p;
         ui.rect(game,left,bottom,w,cover,ModernUi.INK);
         ui.rect(game,left,bottom+h-cover,w,cover,ModernUi.INK);
         ui.rect(game,left,bottom+cover-.8f,w,.8f,ModernUi.GOLD);
         ui.rect(game,left,bottom+h-cover,w,.8f,ModernUi.GOLD);
      }
      countTransition();
   }

'''+s[b:];write(P+'JohtoBattleRenderer.java',s)

# Coherent, low-frequency lawn: retain a very small amount of source texture,
# but use one 64px patch across 4x4 world cells, not a repeated loud 16px stamp.
s=read('tools/prepare_stardew_presentation.py')
needle='    # Full isolated props: exclude adjacent terrain/props, preserve source canvas.'
new='''    # Readability step 4: palette-controlled derived surface, not a full-scene filter.
    # Integer-only periodic field: reproducible across Python/platform versions.
    def calm_surface(key, base):
        source=materials[key]
        art=Image.new('RGBA',(64,64))
        def wave(n):
            n=n%64
            return (n if n<32 else 63-n)-16
        for y in range(64):
            for x in range(64):
                r,g,b,a=source.getpixel((x%source.width,y%source.height))
                residual=((r*77+g*150+b*29)//256-128)//48
                broad=(wave(x)+wave(y)+wave(x+y))//12
                value=broad+residual
                art.putpixel((x,y),tuple(max(0,min(255,c+value)) for c in base)+(255,))
        # Sparse tiny blades, away from cell borders. No solid checkerboard tiles.
        for j in range(12):
            x=3+(j*29)%57;y=3+(j*17)%57
            for dx,dy in ((0,0),(1,-1),(1,-2)):
                art.putpixel((x+dx,y+dy),tuple(min(255,c+7) for c in base)+(255,))
        materials[key]=art
        metadata[key].update(sampleWidth=16,sampleHeight=16,sourceCanvas=[64,64])
        provenance[key]['transformations'].append({'calmPeriodicSurface':{'size':[64,64],'baseRgb':list(base),'sourceDetailDivisor':48,'sparseTufts':12}})
    calm_surface('grass',(88,130,78))
    calm_surface('grass_light',(96,139,84))
    calm_surface('grass_dry',(155,147,94))
    calm_surface('path',(157,144,108))
    alias('dirt','path')

'''
s=replace(s,needle,new+needle);write('tools/prepare_stardew_presentation.py',s)

# Historical mesh parity tests compare the old geometry, explicitly without
# the new terrain/prop-depth changes; the new tests below exercise them ON.
s=read('tools/world_mesh_batch_test.py')
needle='"-Dpokewilds.ambientOverlays=off"'
if needle not in s: needle="'-Dpokewilds.ambientOverlays=off'"
if needle not in s: raise RuntimeError('Missing explicit parity accent switch')
s=s.replace(needle,needle+', "-Dpokewilds.readability=off"');write('tools/world_mesh_batch_test.py',s)

# Exact scope review. All originals outside these presentation helpers stay frozen.
changed=['JohtoRenderer.java','PmdPokemonSprites.java','ModernUi.java','JohtoBattleRenderer.java']
new_scopes={'WorldReadability.java':'Presentation-only natural-ground boundary eligibility, diagnostic A/B switch and smooth easing. No Tile, movement, input, collision, RNG, combat or save mutation.'}
manifest=json.loads(read('baseline/modernization-scope.json'))
vs=read('tools/verify_visual_scope.py')
scope_ast=ast.parse(vs)
for node in scope_ast.body:
    if isinstance(node,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='ADDED_SCOPES' for t in node.targets): scopes=ast.literal_eval(node.value);break
else: raise RuntimeError('No added scope map')
for name in changed:
    scopes[name]+=' Step 4: explicitly reviewed calmer presentation, whole-actor depth/locomotion anchoring, visible ramps/seams, slate/teal panels and eased full-viewport encounter entrance as applicable; original rule/action clocks and save data unchanged.'
scopes.update(new_scopes)
a=vs.index('ADDED_SCOPES = {');node_end=node.end_lineno
lines=vs.splitlines(True);start_line=node.lineno
vs=''.join(lines[:start_line-1])+'ADDED_SCOPES = '+repr(scopes)+'\n'+''.join(lines[node_end:])
vs=replace(vs,'STEP3_ARCHIVE = ', 'STEP4_ARCHIVE = '+repr(archive)+'\n\nSTEP3_ARCHIVE = ')
# Extend both archive enumeration call sites, retaining every preceding digest.
vs=vs.replace('**STEP3_ARCHIVE','**STEP3_ARCHIVE, **STEP4_ARCHIVE')
vs=vs.replace('*STEP3_ARCHIVE.items()', '*STEP3_ARCHIVE.items(), *STEP4_ARCHIVE.items()')
# Actual existing archive loop is a union expression in prior checkpoint.
if vs.count('STEP4_ARCHIVE')<2:
    vs=vs.replace('STEP3_ARCHIVE)', 'STEP3_ARCHIVE | STEP4_ARCHIVE)')
if vs.count('STEP4_ARCHIVE')<2: raise RuntimeError('Did not extend immutable archive validation')
for name in changed+list(new_scopes):
    manifest['added_sources'][P+name]={'scope':scopes[name],'reviewed_sha256':hashlib.sha256((R/(P+name)).read_bytes()).hexdigest()}
manifest['review_notes'].append('Step 4 on ac526d14: calm periodic lawn/path atlas surfaces, flat natural boundary feathering, readable true slopes, consistent prop/actor depth and complete PMD locomotion envelope; shared slate/teal UI and eased full-viewport encounter entrance. No player/battle-rule/world-save/input/height solver changes. Source sheets and original test assertions retained; new ON tests separate from historical geometry parity OFF.')
write('baseline/modernization-scope.json',json.dumps(manifest,indent=2)+'\n');write('tools/verify_visual_scope.py',vs)
spec=importlib.util.spec_from_file_location('review',R/'tools/verify_visual_scope.py');v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
result,_=v.audit(inventory=True);manifest['reviewed_patch_sha256']=result['complete_patch_sha256']
write('baseline/modernization-scope.json',json.dumps(manifest,indent=2)+'\n')
vs=re.sub(r'FROZEN_MANIFEST_SHA256 = "[a-f0-9]+"','FROZEN_MANIFEST_SHA256 = "'+hashlib.sha256((R/'baseline/modernization-scope.json').read_bytes()).hexdigest()+'"',vs)
write('tools/verify_visual_scope.py',vs)
s=read('tools/verify_scope_test.py');s=replace(s,'*verifier.STEP3_ARCHIVE]', '*verifier.STEP3_ARCHIVE, *verifier.STEP4_ARCHIVE]')
s=replace(s,'        verifier.audit(root)\n    report =', '''        reject("readability source changed after freeze", verifier.PREFIX + "WorldReadability.java", append)
        for name in verifier.STEP4_ARCHIVE: reject("pre-readability archive changed " + name, name, append)
        verifier.audit(root)
    report =''');write('tools/verify_scope_test.py',s)
# Assets must be regenerated and committed before normal verification.
subprocess.run([sys.executable,'tools/prepare_stardew_presentation.py'],cwd=R,check=True)
# Temporary patcher removes itself; the workflow definition is also absent from
# the deliverable tree. Normal builds use the tracked Java and Python sources.
(R/'.github/apply_readability_step4.py').unlink()
(R/'.github/workflows/readability-step4.yml').unlink(missing_ok=True)
print('Step4 application complete. Only explicit presentation sources and derived atlas changed.')
