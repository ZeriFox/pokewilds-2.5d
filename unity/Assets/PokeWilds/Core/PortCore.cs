using System;
using System.Collections.Generic;

namespace PokeWilds.Core
{
    // Engine-independent contracts: one Unity tile = 16 legacy world units.
    public sealed class PixelSampling
    {
        public const float LegacyTileSize = 16f;
        public readonly int SampleWidth, SampleHeight, Columns, Rows;
        public int Count { get { return Columns * Rows; } }
        public PixelSampling(int width, int height, int sampleWidth, int sampleHeight)
        {
            if (width <= 0 || height <= 0 || sampleWidth <= 0 || sampleHeight <= 0
                || width % sampleWidth != 0 || height % sampleHeight != 0)
                throw new ArgumentException("Samples must divide the positive source rectangle exactly.");
            long count = (long)(width / sampleWidth) * (height / sampleHeight);
            if (count > 1048576) throw new ArgumentException("Excessive sample count.");
            SampleWidth = sampleWidth; SampleHeight = sampleHeight;
            Columns = width / sampleWidth; Rows = height / sampleHeight;
        }
        public int Index(double legacyX, double legacyY)
        {
            if (!Finite(legacyX) || !Finite(legacyY)) throw new ArgumentException("Non-finite coordinate.");
            // Remainder in double avoids overflowing a fixed-width coordinate cast.
            int col = Mod(Math.Floor(legacyX / LegacyTileSize), Columns);
            int row = Mod(Math.Floor(-legacyY / LegacyTileSize), Rows);
            return row * Columns + col;
        }
        private static int Mod(double value, int divisor) { return (int)((value % divisor + divisor) % divisor); }
        public int SourceX(int index) { Check(index); return index % Columns * SampleWidth; }
        public int SourceY(int index) { Check(index); return index / Columns * SampleHeight; }
        private void Check(int index) { if (index < 0 || index >= Count) throw new ArgumentOutOfRangeException(nameof(index)); }
        public static bool Finite(double n) { return !double.IsNaN(n) && !double.IsInfinity(n); }
    }

    // PMDCollab rows: S, SE, E, NE, N, NW, W, SW. Do not flip the shared sheet.
    public enum Facing { South, SouthEast, East, NorthEast, North, NorthWest, West, SouthWest }
    public sealed class FrameTimeline
    {
        private readonly int[] durations;
        private readonly long total;
        private readonly int rows;
        public int Columns { get { return durations.Length; } }
        public FrameTimeline(int[] ticks, int rowCount)
        {
            if (ticks == null || ticks.Length == 0 || (rowCount != 1 && rowCount != 8))
                throw new ArgumentException("Expected nonempty timings and one or eight PMD directions.");
            durations = (int[])ticks.Clone(); rows = rowCount;
            foreach (int value in durations) { if (value <= 0) throw new ArgumentException("Duration must be positive."); total += value; }
        }
        public int Index(Facing facing, double seconds)
        {
            if ((int)facing < 0 || (int)facing > 7) throw new ArgumentOutOfRangeException(nameof(facing));
            if (!PixelSampling.Finite(seconds) || seconds < 0) seconds = 0;
            // Modulo before multiplication also handles very large finite clocks.
            double tick = Math.Floor((seconds % (total / 60.0)) * 60.0);
            if (tick >= total) tick = 0;
            int column = 0;
            while (column < durations.Length - 1 && tick >= durations[column]) tick -= durations[column++];
            return (rows == 1 ? 0 : (int)facing) * durations.Length + column;
        }
    }

    public sealed class SurfaceCell
    {
        public int X, Z;
        public string Ground, Cliff, Prop, Biome;
        public bool Blocked;
        // Order: south-west, south-east, north-west, north-east.
        public float H00, H10, H01, H11;
        public float Height(float x, float z)
        {
            float u = Math.Max(0, Math.Min(1, x - X)), v = Math.Max(0, Math.Min(1, z - Z));
            // Exactly the same SW->NE triangle diagonal used to build the mesh.
            return u >= v ? H00 + u * (H10 - H00) + v * (H11 - H10)
                          : H00 + v * (H01 - H00) + u * (H11 - H01);
        }
    }

    public sealed class SurfaceGrid
    {
        private readonly Dictionary<long, SurfaceCell> cells = new Dictionary<long, SurfaceCell>();
        public IEnumerable<SurfaceCell> Cells { get { return cells.Values; } }
        public int Count { get { return cells.Count; } }
        private static long Key(int x, int z) { return ((long)x << 32) ^ (uint)z; }
        public SurfaceCell Cell(int x, int z) { SurfaceCell c; cells.TryGetValue(Key(x, z), out c); return c; }
        public SurfaceCell At(float x, float z) { return Cell((int)Math.Floor(x), (int)Math.Floor(z)); }
        public void Add(SurfaceCell cell)
        {
            if (cell == null || !PixelSampling.Finite(cell.H00) || !PixelSampling.Finite(cell.H10)
                || !PixelSampling.Finite(cell.H01) || !PixelSampling.Finite(cell.H11))
                throw new ArgumentException("Invalid surface cell.");
            cells.Add(Key(cell.X, cell.Z), cell);
        }
        public float Height(float x, float z) { SurfaceCell c = At(x, z); return c == null ? 0 : c.Height(x, z); }
        private bool Footprint(float x, float z, float radius, float previousHeight)
        {
            SurfaceCell middle = At(x, z);
            if (middle == null || middle.Blocked || Math.Abs(middle.Height(x, z) - previousHeight) > .20f) return false;
            for (int i = 0; i < 8; i++)
            {
                double a = i * Math.PI / 4;
                float px = x + radius * (float)Math.Cos(a), pz = z + radius * (float)Math.Sin(a);
                SurfaceCell c = At(px, pz);
                if (c == null || c.Blocked || Math.Abs(c.Height(px, pz) - middle.Height(x, z)) > .25f) return false;
            }
            return true;
        }
        public bool CanMove(float x, float z, float toX, float toZ, float radius = .18f)
        {
            if (!PixelSampling.Finite(x) || !PixelSampling.Finite(z) || !PixelSampling.Finite(toX)
                || !PixelSampling.Finite(toZ) || radius < 0 || !PixelSampling.Finite(radius)) return false;
            double distance = Math.Sqrt((toX - x) * (double)(toX - x) + (toZ - z) * (double)(toZ - z));
            if (distance > 32) return false; // Runtime movement is bounded; teleports need a separate checked API.
            int steps = Math.Max(1, (int)Math.Ceiling(distance / .08));
            float height = Height(x, z);
            for (int i = 0; i <= steps; i++)
            {
                float t = (float)i / steps, sx = x + (toX - x) * t, sz = z + (toZ - z) * t;
                if (!Footprint(sx, sz, radius, height)) return false;
                height = Height(sx, sz);
            }
            return true;
        }
    }

    public static class MigrationFixture
    {
        // A bounded migration test map, NOT a port of PkmnMap's procedural generator.
        public static SurfaceGrid Create(int seed = 77129)
        {
            var grid = new SurfaceGrid();
            for (int z = -16; z < 16; z++) for (int x = -16; x < 16; x++)
            {
                var c = new SurfaceCell { X = x, Z = z, Ground = "grass_light", Cliff = "cliff", Biome = "prato" };
                if (x >= 8) { c.Ground = "desert_ground"; c.Cliff = "desert_cliff"; c.Biome = "deserto"; }
                if (x <= -8) { c.Ground = "volcanic_basalt"; c.Cliff = "volcanic_cliff"; c.Biome = "vulcano"; }
                if (z <= -8 && Math.Abs(x) < 8) { c.Ground = "graveyard_ground"; c.Cliff = "graveyard_cliff"; c.Biome = "cimitero"; }
                if (z >= 4 && Math.Abs(x) < 8)
                {
                    c.H00 = c.H10 = c.H01 = c.H11 = 1;
                    c.Ground = z >= 10 ? "snow" : "mountain"; c.Biome = z >= 10 ? "neve" : "montagna";
                }
                if ((x == 0 || x == 1) && z >= 2 && z < 4)
                {
                    c.H00 = c.H10 = (z - 2) * .5f; c.H01 = c.H11 = (z - 1) * .5f; c.Ground = "path";
                }
                if (x >= 11 && x <= 13 && z >= -10 && z <= 9) { c.Ground = "water"; c.Blocked = true; c.Biome = "acqua"; }
                if (x <= -12 && z >= -8 && z <= -2) { c.Ground = "lava_bright"; c.Blocked = true; }
                uint h; unchecked { h = (uint)(x * 73856093 ^ z * 19349663 ^ seed); h ^= h >> 13; h *= 1274126177; h ^= h >> 16; }
                if (!c.Blocked && Math.Abs(x) > 3 && h % 29 == 0)
                {
                    c.Prop = c.Biome == "deserto" ? "desert_rock" : c.Biome == "vulcano" ? "volcanic_rock"
                        : c.Biome == "neve" ? "tree_snow" : c.Biome == "cimitero" ? "graveyard_rock" : "tree";
                    c.Blocked = true;
                }
                grid.Add(c);
            }
            // Three read-only actor fixtures. Clear nearby static prop cells; do not invent spawn rules.
            foreach (int[] p in new[] { new[] { 3, 0 }, new[] { -2, 6 }, new[] { 12, 0 } })
                grid.Cell(p[0], p[1]).Prop = null;
            return grid;
        }
    }
}
