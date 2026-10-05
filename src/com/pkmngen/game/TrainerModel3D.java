package com.pkmngen.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import java.util.Locale;

/** Original low-poly trainer mesh. All coordinates are volumes, not sprite planes.
 * Front is +Z, feet are at Y=0. The separate limb nodes are animated only visually.
 */
public final class TrainerModel3D {
   private static final long ATTRIBUTES = Usage.Position | Usage.Normal;

   private TrainerModel3D() { }

   public static Model create(Player player) {
      String character = player.character == null ? "gold" : player.character.toLowerCase(Locale.ROOT);
      boolean girl = character.contains("kris") || character.contains("lyra")
         || character.contains("leaf") || character.contains("may") || character.contains("dawn");
      Color shirtColor = new Color(player.color);
      shirtColor.a = 1f;
      Color skinColor = new Color(player.skinColor);
      skinColor.a = 1f;
      // Use the player's chosen colours as inputs, without changing its palettes.
      Material shirt = material("shirt", shirtColor);
      Material skin = material("skin", skinColor);
      Material dark = material("navy", new Color(0.11f, 0.16f, 0.25f, 1f));
      Material pants = material("shorts", new Color(0.22f, 0.28f, 0.38f, 1f));
      Material hair = material("hair", girl ? new Color(0.24f, 0.19f, 0.25f, 1f) : new Color(0.27f, 0.20f, 0.16f, 1f));
      Material white = material("cream", new Color(0.96f, 0.95f, 0.87f, 1f));
      Material eyes = material("eyes", new Color(0.10f, 0.13f, 0.18f, 1f));
      Material pack = material("backpack", new Color(0.59f, 0.37f, 0.18f, 1f));
      Material trim = material("gold-trim", new Color(0.98f, 0.76f, 0.25f, 1f));
      ModelBuilder builder = new ModelBuilder();
      builder.begin();
      Node body = builder.node();
      body.id = "body";
      box(builder, "jacket", shirt, 0, 0.91f, 0, 0.58f, 0.60f, 0.36f);
      box(builder, "waist", pants, 0, 0.60f, 0, 0.51f, 0.16f, 0.32f);
      box(builder, "jacket-zip", white, 0, 0.92f, 0.187f, 0.038f, 0.53f, 0.022f);
      box(builder, "collar", dark, 0, 1.18f, 0, 0.36f, 0.08f, 0.36f);
      ellipsoid(builder, "neck", skin, 0, 1.24f, 0, 0.22f, 0.16f, 0.23f);
      ellipsoid(builder, "backpack-volume", pack, 0, 0.91f, -0.28f, 0.46f, 0.53f, 0.30f);
      box(builder, "backpack-flap", trim, 0, 1.07f, -0.41f, 0.31f, 0.12f, 0.04f);
      box(builder, "left-strap", pack, -0.19f, 0.99f, 0.195f, 0.06f, 0.40f, 0.035f);
      box(builder, "right-strap", pack, 0.19f, 0.99f, 0.195f, 0.06f, 0.40f, 0.035f);

      // A large rounded head and cap keep the silhouette readable at game scale.
      ellipsoid(builder, "head", skin, 0, 1.51f, 0.02f, 0.69f, 0.65f, 0.62f);
      ellipsoid(builder, "left-ear", skin, -0.335f, 1.49f, 0.005f, 0.115f, 0.17f, 0.16f);
      ellipsoid(builder, "right-ear", skin, 0.335f, 1.49f, 0.005f, 0.115f, 0.17f, 0.16f);
      ellipsoid(builder, "hair-back", hair, 0, 1.56f, -0.135f, 0.69f, girl ? 0.62f : 0.45f, 0.39f);
      if (girl) {
         ellipsoid(builder, "left-ponytail", hair, -0.35f, 1.42f, -0.22f, 0.21f, 0.41f, 0.23f);
         ellipsoid(builder, "right-ponytail", hair, 0.35f, 1.42f, -0.22f, 0.21f, 0.41f, 0.23f);
      }
      ellipsoid(builder, "cap-crown", dark, 0, 1.76f, -0.015f, 0.73f, 0.30f, 0.67f);
      box(builder, "cap-front", trim, 0, 1.77f, 0.298f, 0.36f, 0.14f, 0.037f);
      // A short brim keeps the eyes visible from the overworld's raised camera.
      ellipsoid(builder, "cap-brim", shirt, 0, 1.70f, 0.25f, 0.71f, 0.065f, 0.26f);
      box(builder, "left-eye-white", white, -0.135f, 1.535f, 0.307f, 0.118f, 0.143f, 0.033f);
      box(builder, "right-eye-white", white, 0.135f, 1.535f, 0.307f, 0.118f, 0.143f, 0.033f);
      box(builder, "left-eye", eyes, -0.12f, 1.533f, 0.331f, 0.061f, 0.113f, 0.022f);
      box(builder, "right-eye", eyes, 0.12f, 1.533f, 0.331f, 0.061f, 0.113f, 0.022f);
      ellipsoid(builder, "nose", skin, 0, 1.43f, 0.324f, 0.08f, 0.085f, 0.10f);
      box(builder, "mouth", hair, 0, 1.35f, 0.294f, 0.095f, 0.018f, 0.023f);

      arm(builder, "arm-left", -0.35f, shirt, skin);
      arm(builder, "arm-right", 0.35f, shirt, skin);
      leg(builder, "leg-left", -0.15f, pants, skin, dark, white, trim);
      leg(builder, "leg-right", 0.15f, pants, skin, dark, white, trim);
      return builder.end();
   }

   private static void arm(ModelBuilder builder, String name, float x, Material shirt, Material skin) {
      Node node = builder.node();
      node.id = name;
      node.translation.set(x, 1.12f, 0);
      ellipsoid(builder, name + "-sleeve", shirt, 0, -0.07f, 0, 0.23f, 0.29f, 0.28f);
      ellipsoid(builder, name + "-forearm", skin, 0, -0.28f, 0, 0.16f, 0.25f, 0.18f);
      ellipsoid(builder, name + "-hand", skin, 0, -0.395f, 0.012f, 0.19f, 0.18f, 0.20f);
   }

   private static void leg(ModelBuilder builder, String name, float x, Material pants, Material skin,
      Material dark, Material white, Material trim) {
      Node node = builder.node();
      node.id = name;
      node.translation.set(x, 0.60f, 0);
      box(builder, name + "-shorts", pants, 0, -0.095f, 0, 0.245f, 0.22f, 0.31f);
      ellipsoid(builder, name + "-shin", skin, 0, -0.285f, 0, 0.17f, 0.23f, 0.18f);
      box(builder, name + "-sock", white, 0, -0.415f, 0, 0.18f, 0.10f, 0.19f);
      ellipsoid(builder, name + "-shoe", dark, 0, -0.51f, 0.06f, 0.25f, 0.18f, 0.38f);
      box(builder, name + "-sole", white, 0, -0.575f, 0.06f, 0.24f, 0.045f, 0.33f);
      box(builder, name + "-shoe-mark", trim, 0, -0.48f, 0.219f, 0.15f, 0.05f, 0.027f);
   }

   public static void animate(ModelInstance instance, float phase, float movement) {
      float amount = MathUtils.clamp(movement, 0f, 1f);
      float swing = MathUtils.sin(phase) * 29f * amount;
      float bob = Math.abs(MathUtils.sin(phase)) * 0.025f * amount;
      instance.getNode("body").translation.y = bob;
      limb(instance.getNode("arm-left"), -swing, -7f, 1.12f + bob);
      limb(instance.getNode("arm-right"), swing, 7f, 1.12f + bob);
      limb(instance.getNode("leg-left"), swing, 0, 0.60f + bob);
      limb(instance.getNode("leg-right"), -swing, 0, 0.60f + bob);
      instance.calculateTransforms();
   }

   private static void limb(Node node, float pitch, float roll, float height) {
      node.translation.y = height;
      node.rotation.setEulerAngles(0, pitch, roll);
   }

   private static Material material(String name, Color color) {
      return new Material(name, ColorAttribute.createDiffuse(color));
   }

   private static MeshPartBuilder part(ModelBuilder builder, String id, Material material) {
      return builder.part(id, GL20.GL_TRIANGLES, ATTRIBUTES, material);
   }

   private static void box(ModelBuilder builder, String id, Material material,
      float x, float y, float z, float width, float height, float depth) {
      part(builder, id, material).box(x, y, z, width, height, depth);
   }

   private static void ellipsoid(ModelBuilder builder, String id, Material material,
      float x, float y, float z, float width, float height, float depth) {
      MeshPartBuilder mesh = part(builder, id, material);
      mesh.setVertexTransform(new Matrix4().setToTranslation(x, y, z));
      mesh.sphere(width, height, depth, 10, 8);
      mesh.setVertexTransform(null);
   }
}
