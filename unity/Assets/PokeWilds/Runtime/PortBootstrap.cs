using System;
using System.Collections.Generic;
using PokeWilds.Core;
using UnityEngine;
using UnityEngine.InputSystem;
using UnityEngine.Rendering;

namespace PokeWilds.UnityPort
{
    // M0 integration scene. This is deliberately not a substitute for the Java rules.
    public sealed class PortBootstrap : MonoBehaviour
    {
        [SerializeField] private int seed = 77129;
        private PortAssets assets;
        private SurfaceGrid grid;
        private Camera view;
        private GameObject content;
        private Mesh terrain;
        private Vector2 position = new Vector2(.5f, .5f);
        private SpriteView trainer;
        private readonly List<SpriteView> objects = new List<SpriteView>();
        private readonly List<Actor> actors = new List<Actor>();
        private readonly List<SurfaceUv> surfaceUvs = new List<SurfaceUv>();
        private Vector2[] uvBuffer;
        private double seconds, nextUvUpdate;
        private string error, inspection, trainerDirection = "down";
        private bool paused, releaseBarrier = true;
        private Color oldFogColor;
        private bool oldFog;
        private FogMode oldFogMode;
        private float oldFogDensity;

        private sealed class Actor { public string Species; public Vector2 Position; public Facing Facing; public SpriteView Sprite; public float Scale; }
        private sealed class SurfaceUv { public SurfaceCell Cell; public int First; }

        private void Awake()
        {
            oldFog = RenderSettings.fog; oldFogColor = RenderSettings.fogColor;
            oldFogDensity = RenderSettings.fogDensity; oldFogMode = RenderSettings.fogMode;
            content = new GameObject("Unity migration fixture"); content.transform.SetParent(transform, false);
            var cameraObject = new GameObject("Migration Camera"); cameraObject.transform.SetParent(content.transform, false);
            view = cameraObject.AddComponent<Camera>(); view.tag = "MainCamera";
            view.clearFlags = CameraClearFlags.SolidColor; view.orthographic = true;
            view.orthographicSize = 7; view.nearClipPlane = .05f; view.farClipPlane = 100;
            view.transform.rotation = Quaternion.Euler(50, 0, 0);
            try
            {
                assets = new PortAssets(); grid = MigrationFixture.Create(seed);
                BuildTerrain();
                trainer = new SpriteView("Player", content.transform, assets);
                foreach (SurfaceCell cell in grid.Cells)
                {
                    if (string.IsNullOrEmpty(cell.Prop)) continue;
                    var prop = new SpriteView(cell.Prop, content.transform, assets);
                    PixelFrame f = assets.Region(cell.Prop);
                    prop.Set(f, 1f / 16f, new Vector3(cell.X + .5f, grid.Height(cell.X+.5f,cell.Z+.5f)+.02f, cell.Z+.5f), view.transform.rotation, true);
                    objects.Add(prop);
                }
                AddActor("pikachu", 3.5f, .5f, Facing.West);
                AddActor("machop", -1.5f, 6.5f, Facing.SouthEast);
                AddActor("milotic", 12.5f, .5f, Facing.West); // Intentional water fixture, not a wild-spawn algorithm.
                TickScene(false);
            }
            catch (Exception ex) { error = ex.Message; Debug.LogException(ex, this); enabled = true; }
        }
        private void AddActor(string species, float x, float z, Facing facing)
        {
            actors.Add(new Actor { Species = species, Position = new Vector2(x,z), Facing = facing,
                Scale = assets.PokemonPixelScale(species), Sprite = new SpriteView(species,content.transform,assets) });
        }
        private void OnApplicationFocus(bool focused) { releaseBarrier = true; }
        private void Update()
        {
            if (error != null || assets == null || grid == null) return;
            Keyboard k = Keyboard.current; Mouse m = Mouse.current;
            if (!Application.isFocused) { releaseBarrier = true; return; }
            bool any = (k != null && (k.wKey.isPressed || k.aKey.isPressed || k.sKey.isPressed || k.dKey.isPressed || k.enterKey.isPressed))
                || (m != null && (m.leftButton.isPressed || m.rightButton.isPressed));
            if (releaseBarrier) { if (!any) releaseBarrier = false; TickScene(false); return; }
            if (k != null && k.enterKey.wasPressedThisFrame) { paused = !paused; inspection = null; releaseBarrier = true; TickScene(false); return; }
            if (inspection != null)
            {
                if (m != null && m.rightButton.wasPressedThisFrame) { inspection = null; releaseBarrier = true; }
                TickScene(false); return;
            }
            if (paused) { TickScene(false); return; }
            float delta = Mathf.Min(Time.unscaledDeltaTime, .05f);
            seconds += delta;
            Vector2 input = k == null ? Vector2.zero : new Vector2((k.dKey.isPressed ? 1 : 0) - (k.aKey.isPressed ? 1 : 0),
                (k.wKey.isPressed ? 1 : 0) - (k.sKey.isPressed ? 1 : 0));
            input = Vector2.ClampMagnitude(input, 1);
            if (input.sqrMagnitude > .001f)
                trainerDirection = Mathf.Abs(input.x) >= Mathf.Abs(input.y) ? (input.x > 0 ? "right" : "left") : (input.y > 0 ? "up" : "down");
            Vector2 before = position;
            float speed = m != null && m.rightButton.isPressed ? 4f : 2.3f;
            Vector2 target = position + input * (speed * delta);
            if (grid.CanMove(position.x, position.y, target.x, target.y)) position = target;
            else
            {
                if (grid.CanMove(position.x, position.y, target.x, position.y)) position.x = target.x;
                if (grid.CanMove(position.x, position.y, position.x, target.y)) position.y = target.y;
            }
            if (m != null && m.leftButton.wasPressedThisFrame)
            {
                Actor nearest = null; float distance = 3f;
                foreach (Actor actor in actors)
                {
                    float d = Vector2.Distance(position, actor.Position);
                    if (d < distance) { nearest = actor; distance = d; }
                }
                inspection = nearest == null ? "Nessun Pokemon vicino.\nQuesta scena verifica movimento, quote e asset."
                    : nearest.Species + "\nSprite e animazione PMD originali.\nStatistiche, inventario e battaglie non ancora migrati.";
                releaseBarrier = true;
            }
            TickScene((position-before).sqrMagnitude > .000001f);
        }
        private void TickScene(bool moving)
        {
            float ground = grid.Height(position.x, position.y);
            Vector3 target = new Vector3(position.x, ground, position.y);
            view.transform.position = target - view.transform.forward * 20;
            SurfaceCell cell = grid.At(position.x, position.y);
            bool graveyard = cell != null && cell.Biome == "cimitero";
            view.backgroundColor = graveyard ? new Color(.37f,.41f,.39f) : new Color(.63f,.77f,.81f);
            RenderSettings.fog = graveyard; RenderSettings.fogColor = view.backgroundColor;
            RenderSettings.fogMode = FogMode.ExponentialSquared; RenderSettings.fogDensity = .028f;
            int step = (int)(seconds * 8) & 3;
            int frame = moving ? (step == 3 ? 1 : step) : 1;
            PixelFrame player = assets.Region("trainer_male_walk_"+trainerDirection+"_"+frame);
            // Trainer frame origins are known B/W metadata, not guessed from transparent bounds.
            trainer.Set(player, .85f/16f, target + Vector3.up*.025f, view.transform.rotation, false, player.width*.5f, player.height*(3f/32f));
            foreach (Actor actor in actors)
                actor.Sprite.Set(assets.PokemonFrame(actor.Species,"Idle",actor.Facing,seconds), actor.Scale,
                    new Vector3(actor.Position.x,grid.Height(actor.Position.x,actor.Position.y)+.025f,actor.Position.y),view.transform.rotation,false);
            if (seconds >= nextUvUpdate)
            {
                nextUvUpdate = seconds + 1.0/12.0;
                foreach (SurfaceUv s in surfaceUvs) SetUv(uvBuffer,s.First,assets.CellUV(s.Cell.Ground,s.Cell.X,s.Cell.Z,seconds));
                terrain.uv = uvBuffer;
            }
        }
        private void BuildTerrain()
        {
            var data = new MeshData();
            foreach (SurfaceCell c in grid.Cells)
            {
                surfaceUvs.Add(new SurfaceUv { Cell=c, First=data.Vertices.Count });
                data.Quad(new Vector3(c.X,c.H00,c.Z),new Vector3(c.X+1,c.H10,c.Z),
                    new Vector3(c.X+1,c.H11,c.Z+1),new Vector3(c.X,c.H01,c.Z+1),assets.CellUV(c.Ground,c.X,c.Z,0),Color.white);
                PixelFrame face = assets.Region(c.Cliff); Rect uv=assets.UV(face);
                SurfaceCell south=grid.Cell(c.X,c.Z-1), north=grid.Cell(c.X,c.Z+1), west=grid.Cell(c.X-1,c.Z), east=grid.Cell(c.X+1,c.Z);
                Edge(data,new Vector2(c.X,c.Z),new Vector2(c.X+1,c.Z),c.H00,c.H10,south==null?-1:south.H01,south==null?-1:south.H11,uv,.82f);
                Edge(data,new Vector2(c.X+1,c.Z+1),new Vector2(c.X,c.Z+1),c.H11,c.H01,north==null?-1:north.H10,north==null?-1:north.H00,uv,.64f);
                Edge(data,new Vector2(c.X,c.Z+1),new Vector2(c.X,c.Z),c.H01,c.H00,west==null?-1:west.H11,west==null?-1:west.H10,uv,.73f);
                Edge(data,new Vector2(c.X+1,c.Z),new Vector2(c.X+1,c.Z+1),c.H10,c.H11,east==null?-1:east.H00,east==null?-1:east.H01,uv,.9f);
            }
            terrain = data.Build("Fixture terrain"); uvBuffer = data.Uvs.ToArray();
            var go = new GameObject("Raised terrain mesh"); go.transform.SetParent(content.transform,false);
            go.AddComponent<MeshFilter>().sharedMesh = terrain;
            go.AddComponent<MeshRenderer>().sharedMaterial = assets.Material(assets.Region("grass_light").texture);
            // Collision uses the same CPU triangles as the visual mesh, not 2D sprite bounds.
        }
        private static void Edge(MeshData d,Vector2 a,Vector2 b,float ha,float hb,float la,float lb,Rect uv,float shade)
        {
            if (ha <= la+.001f && hb <= lb+.001f) return;
            d.Quad(new Vector3(a.x,Math.Min(ha,la),a.y),new Vector3(b.x,Math.Min(hb,lb),b.y),
                new Vector3(b.x,hb,b.y),new Vector3(a.x,ha,a.y),uv,new Color(shade,shade,shade,1));
        }
        private void OnGUI()
        {
            float width = Mathf.Min(680,Screen.width-24);
            GUI.Box(new Rect(12,12,width,104),"UNITY / PORTING M0 — scena tecnica, non gioco completo");
            GUI.Label(new Rect(24,40,width-24,65),error ?? "WASD: movimento   |   RMB tenuto: corsa   |   LMB: ispeziona vicino\nInvio: pausa   |   RMB: chiudi ispezione\nRampe a nord; vulcano a ovest; acqua e deserto a est; cimitero a sud.");
            if (paused || inspection != null)
            {
                float w = Mathf.Min(490,Screen.width-32), h=150;
                Rect box=new Rect((Screen.width-w)/2,(Screen.height-h)/2,w,h);
                GUI.Box(box,paused?"PAUSA":"ISPEZIONE");
                GUI.Label(new Rect(box.x+16,box.y+30,w-32,h-42),paused?"Invio per riprendere.\nSalvataggi Java non caricati né modificati.":inspection+"\n\nRMB per chiudere.");
            }
        }
        private void OnDestroy()
        {
            trainer?.Dispose(); foreach (SpriteView obj in objects) obj.Dispose(); foreach (Actor a in actors) a.Sprite.Dispose();
            PortAssets.Release(terrain); assets?.Dispose(); PortAssets.Release(content);
            RenderSettings.fog=oldFog; RenderSettings.fogColor=oldFogColor; RenderSettings.fogMode=oldFogMode; RenderSettings.fogDensity=oldFogDensity;
        }
        private static void SetUv(Vector2[] buffer,int first,Rect uv)
        {
            buffer[first]=new Vector2(uv.xMin,uv.yMin); buffer[first+1]=new Vector2(uv.xMax,uv.yMin);
            buffer[first+2]=new Vector2(uv.xMax,uv.yMax); buffer[first+3]=new Vector2(uv.xMin,uv.yMax);
        }
        private sealed class MeshData
        {
            public readonly List<Vector3> Vertices=new List<Vector3>();
            public readonly List<Vector2> Uvs=new List<Vector2>();
            private readonly List<int> triangles=new List<int>();
            private readonly List<Color> colors=new List<Color>();
            public void Quad(Vector3 a,Vector3 b,Vector3 c,Vector3 d,Rect uv,Color color)
            {
                int i=Vertices.Count; Vertices.Add(a);Vertices.Add(b);Vertices.Add(c);Vertices.Add(d);
                Uvs.Add(new Vector2(uv.xMin,uv.yMin));Uvs.Add(new Vector2(uv.xMax,uv.yMin));Uvs.Add(new Vector2(uv.xMax,uv.yMax));Uvs.Add(new Vector2(uv.xMin,uv.yMax));
                for(int j=0;j<4;j++)colors.Add(color);
                triangles.Add(i);triangles.Add(i+2);triangles.Add(i+1);triangles.Add(i);triangles.Add(i+3);triangles.Add(i+2);
            }
            public Mesh Build(string name)
            {
                var mesh=new Mesh { name=name,indexFormat=IndexFormat.UInt32 };
                mesh.SetVertices(Vertices);mesh.SetUVs(0,Uvs);mesh.SetColors(colors);mesh.SetTriangles(triangles,0);mesh.RecalculateBounds();return mesh;
            }
        }
        private sealed class SpriteView : IDisposable
        {
            private readonly GameObject go;
            private readonly Mesh mesh;
            private readonly MeshRenderer renderer;
            private readonly PortAssets assets;
            private readonly Vector3[] vertices=new Vector3[4];
            private readonly Vector2[] uvs=new Vector2[4];
            private PixelFrame last;
            private float lastScale=-1;
            public SpriteView(string name,Transform parent,PortAssets assets)
            {
                this.assets=assets;go=new GameObject(name);go.transform.SetParent(parent,false);
                mesh=new Mesh { name=name+" sprite" };mesh.vertices=vertices;mesh.triangles=new[]{0,1,2,0,2,3};
                mesh.colors=new[]{Color.white,Color.white,Color.white,Color.white};
                go.AddComponent<MeshFilter>().sharedMesh=mesh;renderer=go.AddComponent<MeshRenderer>();
            }
            public void Set(PixelFrame f,float scale,Vector3 ground,Quaternion rotation,bool useLayout,float? ax=null,float? ay=null)
            {
                go.transform.rotation=rotation;
                go.transform.position=ground+(useLayout?new Vector3(f.offsetX,f.elevation,f.offsetY):Vector3.zero);
                if(ReferenceEquals(last,f)&&lastScale==scale)return;
                last=f;lastScale=scale;
                float width=f.width*scale,height=f.height*scale;
                if(useLayout)
                {
                    if(f.worldWidth>0){width=f.worldWidth;height=width*f.height/f.width;}
                    if(f.worldHeight>0){height=f.worldHeight;if(f.worldWidth<=0)width=height*f.width/f.height;}
                }
                float x0=-(ax??f.anchorX)/f.width*width,y0=-(ay??f.anchorY)/f.height*height;
                vertices[0]=new Vector3(x0,y0,0);vertices[1]=new Vector3(x0+width,y0,0);
                vertices[2]=new Vector3(x0+width,y0+height,0);vertices[3]=new Vector3(x0,y0+height,0);
                SetUv(uvs,0,assets.UV(f));mesh.vertices=vertices;mesh.uv=uvs;mesh.RecalculateBounds();
                renderer.sharedMaterial=assets.Material(f.texture);
            }
            public void Dispose(){PortAssets.Release(mesh);PortAssets.Release(go);}
        }
    }
}
