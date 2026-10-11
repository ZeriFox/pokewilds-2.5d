#!/usr/bin/env python3
"""One-use checked edit for the connected build workspace; removed from final tree."""
from pathlib import Path
import difflib
import hashlib
import json
import re
import subprocess

ROOT=Path(__file__).resolve().parents[1]
BASE='3da27238880f61ff9a6229c853d8f9c81f083cdc'
PREFIX='src/com/pkmngen/game/'
RENDERER=PREFIX+'JohtoRenderer.java'
HELPER=PREFIX+'WorldMeshBatch.java'

def git(*args):
    return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def once(text,old,new):
    if text.count(old)!=1:raise RuntimeError('Unexpected edit anchor: '+old[:100])
    return text.replace(old,new,1)
def section(text,start,end,new=''):
    if text.count(start)!=1 or text.count(end)!=1:raise RuntimeError('Ambiguous section: '+start)
    a=text.index(start);b=text.index(end,a)
    return text[:a]+new+text[b:]

if git('hash-object',RENDERER)!='ab51bc9c15c2312901da9e53ff1173f36c06e221':
    raise RuntimeError('Renderer changed since the inspected baseline')
if git('hash-object','tools/verify_visual_scope.py')!='12445b1b1baf8982d1612dda07b47e94af12fd52':
    raise RuntimeError('Scope verifier changed since review')
if set(git('diff','--name-only',BASE,'--','src','resources').splitlines())!={HELPER}:
    raise RuntimeError('Unexpected production edits before extraction')
archive=ROOT/'baseline/java-mesh-step1';archive.mkdir(parents=True,exist_ok=False)
raw=(ROOT/RENDERER).read_bytes();(archive/'JohtoRenderer.java').write_bytes(raw)
s=raw.decode('utf-8').replace('\r\n','\n')
s=section(s,'   private Geometry batch(', '   private void quad(')
s=section(s,'   private void vertex(Geometry mesh,','   private void clearGeometry()', '''   private void vertex(Geometry mesh, float x, float y, float z, float color, float u, float v, float style) {
      // Scene-dependent transforms stay here; storage/upload belong to the batch.
      mesh.vertex(x, y+surfaceLift, z, color, u, v, style,
         style>3.5f?projectedAnchor.set(x,y+surfaceLift+.12f,actorPlaneZ).prj(camera.combined).z:0f);
   }

''')
s=section(s,'   private void clearGeometry()', '   private static void upload(')
s=section(s,'   private static void upload(', '   private void collectLights(')
s=section(s,'   private void renderGroups(', '   public void dispose()')
a=s.index('   private static final class Geometry {')
s=s[:a]+'}\n'
old=''.join('      for (Geometry batch : '+name+'.values()) batch.dispose();\n' for name in ('geometry','translucent','shadows','emotes'))+''.join('      '+name+'.clear();\n' for name in ('geometry','translucent','shadows','emotes'))
s=once(s,old,''.join('      '+name+'.dispose();\n' for name in ('geometry','translucent','shadows','emotes')))
s=once(s,'         clearGeometry();','         geometry.clear();\n         translucent.clear();\n         shadows.clear();\n         emotes.clear();')
for name in ('geometry','translucent','shadows','emotes'):
    s=once(s,'private final Map<Texture, Geometry> '+name+' = new IdentityHashMap<>();','private final WorldMeshBatch '+name+' = new WorldMeshBatch();')
    s=once(s,'upload('+name+');',name+'.upload();')
    s=once(s,'renderGroups('+name+');',name+'.render(shader);')
s=s.replace('Map<Texture, Geometry>','WorldMeshBatch')
s=re.sub(r'\bbatch\((geometry|translucent|shadows|emotes|groups),\s*',r'\1.buffer(',s)
s=re.sub(r'\bGeometry\b','Buffer',s)
for line in ('import com.badlogic.gdx.graphics.Mesh;\n','import com.badlogic.gdx.graphics.VertexAttribute;\n','import com.badlogic.gdx.graphics.VertexAttributes.Usage;\n','import com.badlogic.gdx.utils.FloatArray;\n', '   private static final int FLOATS_PER_VERTEX = 8;\n','   private static final int MAX_VERTICES = 65532;\n'):
    s=once(s,line,'')
s=once(s,'package com.pkmngen.game;\n','package com.pkmngen.game;\n\nimport com.pkmngen.game.WorldMeshBatch.Buffer;\n')
for forbidden in ('mesh.vertices','MAX_VERTICES','FLOATS_PER_VERTEX','Map<Texture,','Mesh[]'):
    if forbidden in s:raise RuntimeError('Incomplete mesh extraction: '+forbidden)
if re.search(r'\bbatch\(',s):raise RuntimeError('Unconverted batch call')
(ROOT/RENDERER).write_text(s,encoding='utf-8',newline='\n')
# Freeze only this reviewed renderer delta and the new ownership component.
# All other reviewed production hashes and all historical archives stay unchanged.
manifest_path=ROOT/'baseline/modernization-scope.json'
manifest=json.loads(manifest_path.read_text(encoding='utf-8'))
for group in ('modified_original_sources','added_sources'):
    for path,item in manifest[group].items():
        if path!=RENDERER and sha(ROOT/path)!=item['reviewed_sha256']:
            raise RuntimeError('Unrelated reviewed source changed: '+path)
scope='CPU vertex storage, identity-based texture grouping, triangle-aligned mesh upload/reuse and GPU ownership extracted from JohtoRenderer; no camera, scene state, shader uniforms, texture ownership, gameplay or saved data.'
manifest['added_sources'][RENDERER]['reviewed_sha256']=sha(ROOT/RENDERER)
manifest['added_sources'][HELPER]={'scope':scope,'reviewed_sha256':sha(ROOT/HELPER)}
manifest['review_notes'].append('Java structure step 1: reviewed extraction of mesh storage/upload/draw/dispose only, from 3da27238. Preserve the old renderer as a checksum-pinned test reference; original rules, shaders, scene transforms and all PNGs unchanged.')
patch=[]
classic=ROOT/'baseline/johto-v1/classic-sources'
for path in sorted(manifest['modified_original_sources'])+sorted(manifest['added_sources']):
    original=(classic/path).read_text(encoding='utf-8') if path in manifest['modified_original_sources'] else ''
    patch.extend(difflib.unified_diff(original.splitlines(True),(ROOT/path).read_text(encoding='utf-8').splitlines(True),
        fromfile=('baseline/johto-v1/classic-sources/'+path) if path in manifest['modified_original_sources'] else '/dev/null',tofile=path))
manifest['reviewed_patch_sha256']=hashlib.sha256(''.join(patch).encode()).hexdigest()
manifest_path.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8',newline='\n')
verifier_path=ROOT/'tools/verify_visual_scope.py'
v=verifier_path.read_text(encoding='utf-8')
v=once(v,'ADDED_SCOPES = {\n','ADDED_SCOPES = {\n    "WorldMeshBatch.java": '+json.dumps(scope)+',\n')
v=once(v,'FROZEN_MANIFEST_SHA256 = "d4a7cb4610478f983a99920ba88b3e0f71ef28dd571528441c0ddce6b8d5a747"','FROZEN_MANIFEST_SHA256 = "'+sha(manifest_path)+'"')
verifier_path.write_text(v,encoding='utf-8',newline='\n')
runner=ROOT/'tools/verify_rework.py'
v=runner.read_text(encoding='utf-8')
v=once(v,'    ["tools/active_move_resize_test.py"],\n','    ["tools/active_move_resize_test.py"],\n    ["tools/world_mesh_batch_test.py"],\n')
runner.write_text(v,encoding='utf-8',newline='\n')
# Inspect reuse without consulting an already-disposed old Mesh after growth.
test=ROOT/'tools/WorldMeshBatchTest.java'
v=test.read_text(encoding='utf-8')
v=once(v,'Object prior=null; Mesh priorMesh=null;','Object prior=null; Mesh priorMesh=null; int priorCapacity=0;')
v=once(v,'priorMesh.getMaxVertices()>=counts[iteration]','priorCapacity>=counts[iteration]')
v=once(v,'prior=buffer;priorMesh=mesh;','prior=buffer;priorMesh=mesh;priorCapacity=mesh.getMaxVertices();')
test.write_text(v,encoding='utf-8',newline='\n')
if set(git('diff','--name-only',BASE,'--','src','resources').splitlines())!={RENDERER,HELPER}:
    raise RuntimeError('Extraction escaped its two production files')
(ROOT/'docs/JAVA-REFACTOR-STEP1.md').write_text('''# Java structure — step 1: world mesh ownership

Base: `3da27238880f61ff9a6229c853d8f9c81f083cdc`. Unity migration is cancelled; Java/LibGDX remains the game and Python remains build/asset/test tooling.

`WorldMeshBatch` owns the CPU vertex arrays, identity-based texture groups, chunked GPU meshes, upload, rendering and disposal for one pass. `JohtoRenderer` retains scene collection, terrain/actor coordinates, shader and GL state, pass ordering, atmosphere and camera. The four passes remain terrain/actors, translucent overlays, shadows and emotes. Borrowed textures/shaders are not disposed by the batch.

The 8-float vertex layout, 65,532-vertex non-indexed chunks, triangle order, UVs, packed color and anchor-depth math are unchanged. This is a structural change, not new artwork or a performance claim. No game rules, PNGs, input, generation or save format is changed. No Windows binary is replaced by this commit.

## Verification

Run `python build.py`, then `python tools/world_mesh_batch_test.py` on an OpenGL desktop (or Xvfb/Mesa). The test compiles a renamed, checksum-pinned copy of the old renderer from `baseline/java-mesh-step1/JohtoRenderer.java`. No production override or loose production assets precede the built JAR.

The two probes check exact float/vertex-layout parity, actual GPU images at chunk boundaries, reuse and eviction, idempotent disposal, borrowed texture lifetime, and exact pre/post RGBA comparison in six real world scenes (day, night, volcano day/night, interior, return). Assertions and logs, not this document, determine pass/fail. Full acceptance: `python tools/verify_rework.py --stage all` on a clean commit; the native stage now includes the new test.

The one-time remote extraction script/workflow is removed from the final tree: normal builds read the tracked Java files directly. Scope hashes were refreshed only for the explicitly inspected renderer extraction and its new helper; historical rule/save protections remain.

## Next bounded step

After this checkpoint has passed, separate semantic object/material selection from world scene construction. Do not mix that change with camera, UI or gameplay changes. Preserve the test evidence and keep the original Java reference; the full-game comparison is a refactoring test, not proof of every possible gameplay path.
''',encoding='utf-8')
print('Renderer lines:',len(raw.decode().splitlines()),'->',len(s.splitlines()))
print('Production changes limited to:',RENDERER,HELPER)
# The build/verification path never keeps a source-rewriting migration.
(ROOT/'.github/workflows/java-mesh-extraction.yml').unlink()
Path(__file__).unlink()
