using System;
using System.Collections.Generic;
using PokeWilds.Core;
using UnityEngine;

namespace PokeWilds.UnityPort
{
    [Serializable] public sealed class PixelFrame
    {
        public string texture;
        public int x, y, width, height;
        public float anchorX, anchorY, worldWidth, worldHeight;
        public float offsetX, offsetY, elevation;
    }
    [Serializable] public sealed class RegionData
    {
        public string name;
        public PixelFrame frame;
        public int sampleWidth, sampleHeight, gridWidth, gridHeight;
    }
    [Serializable] public sealed class AnimationData { public string name; public string[] frames; public float fps; }
    [Serializable] public sealed class ClipData { public string name; public int rows; public int[] durations; public PixelFrame[] frames; }
    [Serializable] public sealed class PokemonData { public string name; public ClipData[] clips; }
    [Serializable] public sealed class PortCatalog
    {
        public int schemaVersion;
        public string sourceCommit;
        public RegionData[] regions;
        public AnimationData[] animations;
        public PokemonData[] pokemon;
    }

    public sealed class PortAssets : IDisposable
    {
        private readonly Dictionary<string, Texture2D> textures = new Dictionary<string, Texture2D>();
        private readonly Dictionary<string, Material> materials = new Dictionary<string, Material>();
        private readonly Dictionary<string, RegionData> regions = new Dictionary<string, RegionData>();
        private readonly Dictionary<string, PixelSampling> samplers = new Dictionary<string, PixelSampling>();
        private readonly Dictionary<string, AnimationData> animations = new Dictionary<string, AnimationData>();
        private readonly Dictionary<string, PokemonData> pokemon = new Dictionary<string, PokemonData>();
        private readonly Dictionary<ClipData, FrameTimeline> timelines = new Dictionary<ClipData, FrameTimeline>();
        private readonly Shader shader;
        public PortCatalog Catalog { get; private set; }
        public PortAssets()
        {
            shader = Resources.Load<Shader>("PixelCutout");
            if (shader == null) throw new InvalidOperationException("Missing PixelCutout shader.");
            TextAsset text = Resources.Load<TextAsset>("Imported/catalog");
            if (text == null) throw new InvalidOperationException("Importa prima gli asset: Tools > PokeWilds > Import Java assets. Il progetto Java resta intatto.");
            Catalog = JsonUtility.FromJson<PortCatalog>(text.text);
            if (Catalog == null || Catalog.schemaVersion != 1 || Catalog.regions == null || Catalog.pokemon == null)
                throw new InvalidOperationException("Unsupported Unity bridge catalog.");
            foreach (RegionData r in Catalog.regions)
            {
                if (r.frame == null) throw new InvalidOperationException("Missing region: " + r.name);
                regions.Add(r.name, r);
                samplers.Add(r.name, new PixelSampling(r.gridWidth, r.gridHeight, r.sampleWidth, r.sampleHeight));
            }
            foreach (AnimationData a in Catalog.animations ?? new AnimationData[0])
            {
                if (a.frames == null || a.frames.Length == 0 || a.fps <= 0) throw new InvalidOperationException("Invalid animation.");
                foreach (string key in a.frames) if (!regions.ContainsKey(key)) throw new InvalidOperationException("Missing frame: " + key);
                animations.Add(a.name, a);
            }
            foreach (PokemonData p in Catalog.pokemon)
            {
                pokemon.Add(p.name, p);
                foreach (ClipData c in p.clips)
                {
                    var clock = new FrameTimeline(c.durations, c.rows);
                    if (c.frames == null || c.frames.Length != c.rows * clock.Columns) throw new InvalidOperationException("Invalid PMD grid: " + p.name);
                    timelines.Add(c, clock);
                }
            }
        }
        public Texture2D Texture(string path)
        {
            Texture2D result;
            if (!textures.TryGetValue(path, out result))
            {
                result = Resources.Load<Texture2D>("Imported/" + path);
                if (result == null) throw new InvalidOperationException("Missing imported PNG: " + path);
                textures.Add(path, result);
            }
            return result;
        }
        public Material Material(string texture)
        {
            Material result;
            if (!materials.TryGetValue(texture, out result))
            {
                result = new Material(shader) { name = "PokeWilds " + texture, mainTexture = Texture(texture) };
                materials.Add(texture, result);
            }
            return result;
        }
        public PixelFrame Region(string name)
        {
            RegionData result;
            if (!regions.TryGetValue(name, out result)) throw new InvalidOperationException("Missing region: " + name);
            return result.frame;
        }
        public Rect UV(PixelFrame frame)
        {
            Texture2D t = Texture(frame.texture);
            if (frame.x < 0 || frame.y < 0 || frame.width <= 0 || frame.height <= 0
                || (long)frame.x + frame.width > t.width || (long)frame.y + frame.height > t.height)
                throw new InvalidOperationException("Out-of-bounds source crop: " + frame.texture);
            // Source rectangles use top-left; Unity UV uses bottom-left.
            return new Rect((float)frame.x / t.width, (float)(t.height - frame.y - frame.height) / t.height,
                (float)frame.width / t.width, (float)frame.height / t.height);
        }
        public Rect CellUV(string name, int x, int z, double seconds)
        {
            AnimationData a;
            if (animations.TryGetValue(name, out a)) name = a.frames[(int)(Math.Floor(Math.Max(0, seconds) * a.fps) % a.frames.Length)];
            RegionData r = regions[name]; PixelSampling s = samplers[name];
            int index = s.Index(x * 16.0, z * 16.0);
            Texture2D t = Texture(r.frame.texture);
            return new Rect((float)(r.frame.x + s.SourceX(index)) / t.width,
                (float)(t.height - r.frame.y - s.SourceY(index) - s.SampleHeight) / t.height,
                (float)s.SampleWidth / t.width, (float)s.SampleHeight / t.height);
        }
        public PixelFrame PokemonFrame(string name, string animation, Facing facing, double seconds)
        {
            PokemonData p;
            if (!pokemon.TryGetValue(name, out p)) throw new InvalidOperationException("Pokemon not imported: " + name);
            ClipData clip = Array.Find(p.clips, c => c.name == animation) ?? Array.Find(p.clips, c => c.name == "Idle");
            if (clip == null) throw new InvalidOperationException("No usable PMD clip: " + name);
            return clip.frames[timelines[clip].Index(facing, seconds)];
        }
        public float PokemonPixelScale(string name)
        {
            PixelFrame idle = PokemonFrame(name, "Idle", Facing.South, 0);
            return Math.Min(.85f, 30f / Math.Max(idle.width, idle.height)) / 16f;
        }
        public void Dispose()
        {
            foreach (Material m in materials.Values) Release(m);
            materials.Clear(); textures.Clear(); // Loaded Resources textures remain owned by Unity.
        }
        public static void Release(UnityEngine.Object value)
        {
            if (value == null) return;
            if (Application.isPlaying) UnityEngine.Object.Destroy(value); else UnityEngine.Object.DestroyImmediate(value);
        }
    }
}
