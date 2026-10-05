package net.mgsx.gltf.loaders.shared.geometry;

import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** CPU-only reconstruction check: output primitive vertex records must match input exactly. */
public final class MeshSplitterInvariantTest {
   private static final VertexAttributes ATTRIBUTES = new VertexAttributes(
      new VertexAttribute(Usage.Position, 3, "a_position"),
      new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"));
   private static final int STRIDE = 5;
   private static int checks;

   public static void main(String[] arguments) throws Exception {
      verify("triangles-boundary", records(65538), sequence(65538), 3);
      int[] boundary = Arrays.copyOf(sequence(65532), 65544);
      int[] end = {65532, 65532, 65532, 65533, 65534, 65534, 0, 65535, 65535, 65535, 65534, 1};
      System.arraycopy(end, 0, boundary, 65532, end.length);
      verify("repeated-degenerate-cross-boundary", records(65536), boundary, 3);
      verify("lines-boundary", records(70000), sequence(70000), 2);
      verify("sparse-indices", records(200001), new int[] {0, 200000, 1, 1, 200000, 0}, 3);
      verify("empty", new float[0], new int[0], 3);
      expectRejected(new float[5], new int[] {0, 1, 0}, 3, "out-of-range");
      expectRejected(new float[5], new int[] {-1, 0, 0}, 3, "negative-index");
      expectRejected(new float[5], new int[] {0, 0}, 3, "incomplete-triangle");
      if (arguments.length != 1) throw new IllegalArgumentException("Expected original Chimecho GLB path");
      verifyGlb(Path.of(arguments[0]));
      System.out.println("INVARIANTS PASS: " + checks + " cases; primitive order, vertex bits, index ranges, capacity and counts preserved.");
   }

   private static int[] sequence(int length) {
      int[] result = new int[length];
      for (int i = 0; i < length; i++) result[i] = i;
      return result;
   }

   private static float[] records(int count) {
      float[] result = new float[count * STRIDE];
      for (int i = 0; i < count; i++) {
         result[i * STRIDE] = i;
         result[i * STRIDE + 1] = -i * 0.25f;
         result[i * STRIDE + 2] = -0.0f;
         result[i * STRIDE + 3] = Float.intBitsToFloat(0x7fc00000 | (i & 0x003fffff));
         result[i * STRIDE + 4] = Float.intBitsToFloat(i);
      }
      return result;
   }

   private static void verify(String name, float[] vertices, int[] indices, int primitiveSize) {
      Array<float[]> splitVertices = new Array<>();
      Array<short[]> splitIndices = new Array<>();
      MeshSpliter.split(splitVertices, splitIndices, vertices, ATTRIBUTES, indices, primitiveSize);
      require(splitVertices.size == splitIndices.size, name + ": output array count");
      int cursor = 0;
      for (int batch = 0; batch < splitVertices.size; batch++) {
         float[] actualVertices = splitVertices.get(batch);
         short[] actualIndices = splitIndices.get(batch);
         require(actualVertices.length % STRIDE == 0, name + ": vertex stride");
         int vertexCount = actualVertices.length / STRIDE;
         require(vertexCount <= 65535, name + ": vertex capacity");
         require(actualIndices.length % primitiveSize == 0, name + ": primitive divided between batches");
         for (short signedIndex : actualIndices) {
            int index = signedIndex & 0xffff;
            require(index < vertexCount && index < 65535, name + ": invalid output index " + index);
            require(cursor < indices.length, name + ": extra output indices");
            int expected = indices[cursor++] * STRIDE;
            for (int component = 0; component < STRIDE; component++) {
               int actualBits = Float.floatToRawIntBits(actualVertices[index * STRIDE + component]);
               int expectedBits = Float.floatToRawIntBits(vertices[expected + component]);
               require(actualBits == expectedBits, name + ": changed or reordered vertex at index " + (cursor - 1));
            }
         }
      }
      require(cursor == indices.length, name + ": missing output indices");
      checks++;
      System.out.println("PASS " + name + ": " + indices.length / primitiveSize + " primitives, " + splitVertices.size + " batches");
   }

   private static void expectRejected(float[] vertices, int[] indices, int primitiveSize, String name) {
      try {
         MeshSpliter.split(new Array<>(), new Array<>(), vertices, ATTRIBUTES, indices, primitiveSize);
      } catch (IllegalArgumentException expected) {
         checks++;
         return;
      }
      throw new AssertionError("Invalid input was accepted: " + name);
   }

   private static void verifyGlb(Path file) throws Exception {
      byte[] bytes = Files.readAllBytes(file);
      ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
      require(buffer.getInt(0) == 0x46546c67 && buffer.getInt(4) == 2, "Invalid GLB header");
      int jsonLength = buffer.getInt(12);
      JsonValue document = new JsonReader().parse(new String(bytes, 20, jsonLength, StandardCharsets.UTF_8));
      int binaryStart = 20 + jsonLength + 8;
      JsonValue accessors = document.get("accessors");
      JsonValue views = document.get("bufferViews");
      int meshIndex = 0;
      for (JsonValue mesh : document.get("meshes")) {
         int primitiveIndex = 0;
         for (JsonValue primitive : mesh.get("primitives")) {
            require(primitive.getInt("mode", 4) == 4, "Expected original Chimecho triangles");
            JsonValue position = accessors.get(primitive.get("attributes").getInt("POSITION"));
            require(position.getInt("componentType") == 5126 && position.getString("type").equals("VEC3"), "Position format");
            JsonValue positionView = views.get(position.getInt("bufferView"));
            int positionOffset = binaryStart + positionView.getInt("byteOffset", 0) + position.getInt("byteOffset", 0);
            int positionStride = positionView.getInt("byteStride", 12);
            float[] vertices = records(position.getInt("count"));
            for (int vertex = 0; vertex < position.getInt("count"); vertex++) {
               for (int component = 0; component < 3; component++) {
                  vertices[vertex * STRIDE + component] = buffer.getFloat(positionOffset + vertex * positionStride + component * 4);
               }
            }
            JsonValue index = accessors.get(primitive.getInt("indices"));
            require(index.getInt("componentType") == 5125, "Expected original Chimecho UINT indices");
            JsonValue indexView = views.get(index.getInt("bufferView"));
            int indexOffset = binaryStart + indexView.getInt("byteOffset", 0) + index.getInt("byteOffset", 0);
            int[] indices = new int[index.getInt("count")];
            for (int i = 0; i < indices.length; i++) indices[i] = buffer.getInt(indexOffset + i * 4);
            verify("chimecho-mesh" + meshIndex + "-primitive" + primitiveIndex, vertices, indices, 3);
            primitiveIndex++;
         }
         meshIndex++;
      }
   }

   private static void require(boolean condition, String message) {
      if (!condition) throw new AssertionError(message);
   }
}
