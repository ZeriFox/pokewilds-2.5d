package com.pkmngen.game;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.FloatArray;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * CPU vertex storage and GPU mesh ownership for one world rendering pass.
 *
 * The caller supplies final positions, UVs, packed colors and anchor depths.
 * No camera, terrain, game state, shader uniforms or GL pass state is owned here.
 * Textures and the shader are borrowed and are never disposed by this class.
 * Use only on the render thread: clear, append complete triangles, upload, render.
 */
final class WorldMeshBatch {
   private static final int FLOATS_PER_VERTEX = 8;
   private static final int MAX_VERTICES = 65532;
   private final Map<Texture, Buffer> groups = new IdentityHashMap<>();

   Buffer buffer(Texture texture) {
      Buffer buffer = groups.get(texture);
      if (buffer == null) {
         buffer = new Buffer();
         groups.put(texture, buffer);
      }
      return buffer;
   }

   /** Reset this frame's vertices without discarding reusable GPU allocations. */
   void clear() {
      for (Buffer buffer : groups.values()) buffer.vertices.clear();
   }

   /** Upload each texture group and release groups no longer used this frame. */
   void upload() {
      Iterator<Buffer> iterator = groups.values().iterator();
      while (iterator.hasNext()) {
         Buffer buffer = iterator.next();
         int count = buffer.vertices.size / FLOATS_PER_VERTEX;
         // In particular, do not retain textures evicted by the PMD sprite cache.
         if (count == 0) { buffer.dispose(); iterator.remove(); continue; }
         // Preserve the original non-indexed, triangle-aligned chunk boundaries.
         int chunks = (count + MAX_VERTICES - 1) / MAX_VERTICES;
         if (buffer.meshes == null || buffer.meshes.length < chunks) {
            Mesh[] replacement = new Mesh[chunks];
            if (buffer.meshes != null) System.arraycopy(buffer.meshes, 0, replacement, 0, buffer.meshes.length);
            buffer.meshes = replacement;
         }
         for (int i = 0; i < chunks; i++) {
            int vertices = Math.min(MAX_VERTICES, count - i * MAX_VERTICES);
            Mesh mesh = buffer.meshes[i];
            if (mesh == null || mesh.getMaxVertices() < vertices) {
               if (mesh != null) mesh.dispose();
               mesh = new Mesh(false, Math.min(MAX_VERTICES, Math.max(256, vertices * 2)), 0,
                  new VertexAttribute(Usage.Position, 3, "a_position"),
                  new VertexAttribute(Usage.ColorPacked, 4, "a_color"),
                  new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                  new VertexAttribute(Usage.Generic, 1, "a_style"),
                  new VertexAttribute(Usage.Generic, 1, "a_anchorDepth"));
               buffer.meshes[i] = mesh;
            }
            mesh.setVertices(buffer.vertices.items, i * MAX_VERTICES * FLOATS_PER_VERTEX, vertices * FLOATS_PER_VERTEX);
         }
      }
   }

   /** Draw with the caller's active shader, uniforms and depth/blend state. */
   void render(ShaderProgram shader) {
      for (Map.Entry<Texture, Buffer> entry : groups.entrySet()) {
         Buffer buffer = entry.getValue();
         int count = buffer.vertices.size / FLOATS_PER_VERTEX;
         if (count == 0) continue;
         entry.getKey().bind(0);
         for (int i = 0; i * MAX_VERTICES < count; i++) {
            buffer.meshes[i].render(shader, GL20.GL_TRIANGLES, 0, Math.min(MAX_VERTICES, count - i * MAX_VERTICES));
         }
      }
   }

   /** Idempotent; a later frame may populate this batch again. */
   void dispose() {
      for (Buffer buffer : groups.values()) buffer.dispose();
      groups.clear();
   }

   /** Per-texture vertex writer. Its backing arrays and GPU meshes stay private. */
   static final class Buffer {
      private final FloatArray vertices = new FloatArray(false, 1536);
      private Mesh[] meshes;

      void vertex(float x, float y, float z, float color, float u, float v, float style, float anchorDepth) {
         vertices.add(x);
         vertices.add(y);
         vertices.add(z);
         vertices.add(color);
         vertices.add(u);
         vertices.add(v);
         vertices.add(style);
         vertices.add(anchorDepth);
      }

      private void dispose() {
         if (meshes != null) for (Mesh mesh : meshes) if (mesh != null) mesh.dispose();
         meshes = null;
         vertices.clear();
      }
   }
}
