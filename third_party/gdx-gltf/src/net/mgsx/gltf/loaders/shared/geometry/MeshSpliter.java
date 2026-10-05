package net.mgsx.gltf.loaders.shared.geometry;

import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.ShortArray;

/**
 * ABI-compatible replacement for gdx-gltf 2.1.0's unsigned-index splitter.
 * Each output batch contains complete primitives and at most 65,535 vertices.
 * Vertex records are copied exactly; no geometry is simplified or discarded.
 */
class MeshSpliter {
   private static final int MAX_VERTICES = 65535;

   public static void split(Array<float[]> verticesList, Array<short[]> indicesList,
      float[] vertices, VertexAttributes attributes, int[] indices, int primitiveSize) {
      if (primitiveSize != 2 && primitiveSize != 3) {
         throw new IllegalArgumentException("Unsigned index splitting requires lines or triangles.");
      }
      if (attributes.vertexSize <= 0 || attributes.vertexSize % 4 != 0) {
         throw new IllegalArgumentException("Invalid vertex record size.");
      }
      int stride = attributes.vertexSize / 4;
      if (vertices.length % stride != 0 || indices.length % primitiveSize != 0) {
         throw new IllegalArgumentException("Incomplete vertex record or primitive.");
      }
      int vertexCount = vertices.length / stride;
      int capacity = Math.min(MAX_VERTICES, vertexCount);
      FloatArray batchVertices = new FloatArray(Math.max(stride, capacity * stride));
      ShortArray batchIndices = new ShortArray(Math.min(65535, indices.length));
      IntIntMap remap = new IntIntMap(Math.max(1, capacity));

      for (int primitive = 0; primitive < indices.length; primitive += primitiveSize) {
         int newVertices = 0;
         for (int corner = 0; corner < primitiveSize; corner++) {
            int source = indices[primitive + corner];
            if (source < 0 || source >= vertexCount) {
               throw new IllegalArgumentException("Primitive references invalid vertex " + source + " of " + vertexCount);
            }
            if (remap.get(source, -1) >= 0) continue;
            // A degenerate primitive can reference a new vertex more than once.
            boolean alreadyCounted = false;
            for (int earlier = 0; earlier < corner; earlier++) {
               if (indices[primitive + earlier] == source) alreadyCounted = true;
            }
            if (!alreadyCounted) newVertices++;
         }

         if (remap.size + newVertices > MAX_VERTICES) {
            verticesList.add(batchVertices.toArray());
            indicesList.add(batchIndices.toArray());
            batchVertices.clear();
            batchIndices.clear();
            remap.clear();
         }

         // The capacity check above reserves the entire primitive. Never split
         // a triangle or line between batches, even at a 16-bit index boundary.
         for (int corner = 0; corner < primitiveSize; corner++) {
            int source = indices[primitive + corner];
            int target = remap.get(source, -1);
            if (target < 0) {
               target = remap.size;
               remap.put(source, target);
               batchVertices.addAll(vertices, source * stride, stride);
            }
            batchIndices.add((short)target);
         }
      }
      if (batchIndices.size > 0) {
         verticesList.add(batchVertices.toArray());
         indicesList.add(batchIndices.toArray());
      }
   }
}
