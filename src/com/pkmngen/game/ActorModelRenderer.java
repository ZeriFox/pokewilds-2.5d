package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.loaders.gltf.GLTFLoader;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import net.mgsx.gltf.scene3d.utils.MaterialConverter;

/** Owns optional 3D actor presentation; never writes to a Player, Pokemon or map. */
public final class ActorModelRenderer {
   private static final int MAX_ACTORS = 64;
   private static final int MAX_SPECIES = 24;
   private static final int MAX_BONES = 128;
   private final boolean enabled = "on".equalsIgnoreCase(System.getProperty("pokewilds.models", "off"));
   private final IdentityHashMap<Object, Actor> actors = new IdentityHashMap<>();
   private final Map<String, SpeciesAsset> species = new HashMap<>();
   private final Set<String> unavailable = new HashSet<>();
   private final ArrayList<Actor> queued = new ArrayList<>();
   private final Environment environment = new Environment();
   private final ColorAttribute ambient = new ColorAttribute(ColorAttribute.AmbientLight, 0.70f, 0.70f, 0.70f, 1f);
   private final DirectionalLight sun = new DirectionalLight();
   private ModelBatch batch;
   private Camera camera;
   private long frame;
   private long playerModelFrames;
   private long pokemonModelFrames;
   private long animatedModelFrames;
   private int loadsThisFrame;

   public ActorModelRenderer() {
      environment.set(ambient);
      environment.add(sun.set(0.65f, 0.63f, 0.58f, -0.45f, -0.85f, -0.35f));
   }

   public void beginFrame(Camera camera) {
      this.camera = camera;
      frame++;
      queued.clear();
      loadsThisFrame = 0;
      // Keep only a short history of actors. Models are shared per species and
      // released separately once no instance uses them.
      Iterator<Map.Entry<Object, Actor>> iterator = actors.entrySet().iterator();
      while (iterator.hasNext()) {
         Actor actor = iterator.next().getValue();
         if (frame - actor.lastSeen > 180) {
            actor.dispose();
            iterator.remove();
         }
      }
      Iterator<Map.Entry<String, SpeciesAsset>> assets = species.entrySet().iterator();
      while (assets.hasNext()) {
         SpeciesAsset asset = assets.next().getValue();
         if (frame - asset.lastSeen > 600 && !isUsed(asset)) {
            asset.dispose();
            assets.remove();
         }
      }
   }

   public boolean queuePlayer(Player player, float lift) {
      if (!enabled || player == null || player.currSprite == null || player.position == null
         || player.isSleeping || player.isSitting || player.isFishing || !player.currFieldMove.isEmpty()) return false;
      if (!visible(player.position.x, player.position.y, 28f)) return false;
      String key = player.character + ":" + Color.rgba8888(player.color) + ":" + Color.rgba8888(player.skinColor);
      Actor actor = actors.get(player);
      if (actor != null && !key.equals(actor.key)) {
         actor.dispose();
         actors.remove(player);
         actor = null;
      }
      if (actor == null) {
         if (!makeActorSpace() || unavailable.contains("player:" + key)) return false;
         try {
            Model model = TrainerModel3D.create(player);
            actor = new Actor(key, new ModelInstance(model), model);
            actor.player = true;
            actor.bounds = calculateVisualBounds(actor.instance);
            actor.anchor = anchor(actor.bounds);
            actors.put(player, actor);
         } catch (RuntimeException ex) {
            unavailable.add("player:" + key);
            Gdx.app.error("ActorModels", "Trainer model unavailable; original sprite retained.", ex);
            return false;
         }
      }
      update(actor, player.position.x, player.position.y, player.dirFacing, 20f, lift);
      TrainerModel3D.animate(actor.instance, actor.phase, actor.movement);
      actor.animated = actor.movement > 0f;
      queued.add(actor);
      return true;
   }

   public boolean queuePokemon(Pokemon pokemon) {
      if (!enabled || pokemon == null || pokemon.specie == null || pokemon.currOwSprite == null
         || pokemon.isEgg || pokemon.isGhost || pokemon.isShiny) return false;
      if (!visible(pokemon.position.x, pokemon.position.y, 40f)) return false;
      // Exact species/form key only. Never silently replace a form or shiny with
      // the standard species model. Missing assets retain the original sprite.
      String key = pokemon.specie.name;
      if (key == null || !key.matches("[a-z0-9_!.-]+")) return false;
      if (unavailable.contains(key)) return false;
      Actor actor = actors.get(pokemon);
      if (actor != null && !key.equals(actor.key)) {
         actor.dispose();
         actors.remove(pokemon);
         actor = null;
      }
      if (actor == null) {
         if (!makeActorSpace()) return false;
         SpeciesAsset asset = loadSpecies(key);
         if (asset == null) return false;
         try {
            Scene scene = new Scene(asset.asset.scene);
            MaterialConverter.makeCompatible(scene);
            actor = new Actor(key, scene.modelInstance, null);
            actor.scene = scene;
            actor.asset = asset;
            actor.idle = animation(actor.instance, "idle", "standing", "wait");
            actor.walk = animation(actor.instance, "walk", "run", "move");
            if (actor.idle != null) {
               scene.animationController.setAnimation(actor.idle, -1);
               scene.animationController.update(0f);
               actor.clip = actor.idle;
            }
            actor.bounds = calculateVisualBounds(actor.instance);
            actor.anchor = anchor(actor.bounds);
            asset.anchors.putIfAbsent(actor.idle, actor.anchor);
            actors.put(pokemon, actor);
         } catch (RuntimeException ex) {
            Gdx.app.error("ActorModels", "Cannot instantiate " + key + "; original sprite retained.", ex);
            unavailable.add(key);
            return false;
         }
      }
      actor.asset.lastSeen = frame;
      float height = MathUtils.clamp(pokemon.currOwSprite.getRegionHeight() * 1.125f, 12f, 48f);
      float lift = 0f;
      if (pokemon.inWater) {
         // The opaque water surface depth-clips the submerged model naturally.
         lift = -height * 0.40f;
         if (pokemon.drawUpper != null) lift += pokemon.drawUpper.floatOffset + pokemon.drawUpper.floatOffset2;
      }
      update(actor, pokemon.position.x, pokemon.position.y, pokemon.dirFacing, height, lift);
      String clip = actor.movement > 0f && actor.walk != null ? actor.walk : actor.idle;
      if (clip != null) {
         Vector3 clipAnchor = actor.asset.anchors.get(clip);
         if (clipAnchor == null) {
            clipAnchor = calculateClipAnchor(actor.asset.asset, clip);
            actor.asset.anchors.put(clip, clipAnchor);
         }
         actor.anchor = clipAnchor;
         if (!clip.equals(actor.clip)) {
            actor.scene.animationController.setAnimation(clip, -1);
            actor.clip = clip;
         }
         actor.scene.animationController.update(MathUtils.clamp(Gdx.graphics.getDeltaTime(), 0f, 0.05f));
         actor.animated = true;
      } else {
         actor.animated = false;
      }
      // Animation clips can contain an authored absolute height (for example a
      // bird's flight cycle). Remove only that clip's fixed start-pose offset;
      // keep its pose and every subsequent oscillation inside the original tile.
      applyTransform(actor, pokemon.position.x, pokemon.position.y, pokemon.dirFacing, height, lift);
      queued.add(actor);
      return true;
   }

   private void update(Actor actor, float x, float y, String direction, float targetHeight, float lift) {
      if (actor.lastSeen == frame) return;
      float distance = actor.hasPosition ? Vector3.dst(actor.x, actor.y, 0f, x, y, 0f) : 0f;
      actor.movement = distance > 0.001f && distance < 8f && frame - actor.lastSeen <= 2 ? 1f : 0f;
      if (actor.movement > 0f) actor.phase = (actor.phase + distance * MathUtils.PI2 / 24f) % MathUtils.PI2;
      actor.x = x;
      actor.y = y;
      actor.hasPosition = true;
      actor.lastSeen = frame;
      applyTransform(actor, x, y, direction, targetHeight, lift);
   }

   private void applyTransform(Actor actor, float x, float y, String direction, float targetHeight, float lift) {
      float scale = targetHeight / actor.bounds.getHeight();
      // Bound silhouettes around the same logical one-tile footprint without
      // scaling or changing any collision coordinate.
      scale = Math.min(scale, 25f / Math.max(0.001f, actor.bounds.getWidth()));
      scale = Math.min(scale, 30f / Math.max(0.001f, actor.bounds.getDepth()));
      float yaw = "right".equals(direction) ? 90f : "left".equals(direction) ? -90f : "up".equals(direction) ? 180f : 0f;
      actor.instance.transform.idt().translate(x + 8f, 0.15f + lift, -y - 7f)
         .rotate(Vector3.Y, yaw).scale(scale, scale, scale)
         .translate(-actor.anchor.x, -actor.anchor.y, -actor.anchor.z);
   }

   private boolean visible(float x, float y, float radius) {
      return camera != null && camera.frustum.sphereInFrustum(x + 8f, radius * 0.5f, -y - 7f, radius);
   }

   private boolean makeActorSpace() {
      if (actors.size() < MAX_ACTORS) return true;
      Object oldestKey = null;
      long oldestFrame = frame;
      for (Map.Entry<Object, Actor> entry : actors.entrySet()) {
         if (entry.getValue().lastSeen < oldestFrame) {
            oldestFrame = entry.getValue().lastSeen;
            oldestKey = entry.getKey();
         }
      }
      if (oldestKey == null) return false;
      actors.remove(oldestKey).dispose();
      return true;
   }

   private SpeciesAsset loadSpecies(String name) {
      SpeciesAsset found = species.get(name);
      if (found != null) return found;
      if (unavailable.contains(name) || loadsThisFrame >= 1) return null;
      FileHandle file = Gdx.files.internal("visual/johto/models/pokemon/" + name + ".glb");
      if (!file.exists()) file = Gdx.files.internal("visual/johto/models/pokemon/" + name + ".gltf");
      if (!file.exists()) {
         unavailable.add(name);
         return null;
      }
      if (species.size() >= MAX_SPECIES) {
         String oldestName = null;
         long oldestFrame = Long.MAX_VALUE;
         for (Map.Entry<String, SpeciesAsset> entry : species.entrySet()) {
            if (entry.getValue().lastSeen < oldestFrame && !isUsed(entry.getValue())) {
               oldestName = entry.getKey();
               oldestFrame = entry.getValue().lastSeen;
            }
         }
         if (oldestName == null) return null;
         species.remove(oldestName).dispose();
      }
      loadsThisFrame++;
      SceneAsset loaded = null;
      try {
         loaded = file.extension().equals("glb") ? new GLBLoader().load(file) : new GLTFLoader().load(file);
         if (loaded.scene == null) throw new IllegalArgumentException("Model has no default scene.");
         if (loaded.maxBones > MAX_BONES) throw new IllegalArgumentException("Model needs " + loaded.maxBones + " bones; supported maximum is " + MAX_BONES);
         prepareSceneAsset(loaded);
         SpeciesAsset asset = new SpeciesAsset(loaded);
         asset.lastSeen = frame;
         species.put(name, asset);
         Gdx.app.log("ActorModels", "Loaded exact 3D species " + name + " (" + loaded.maxBones + " bones, "
            + (loaded.animations == null ? 0 : loaded.animations.size) + " animations).");
         return asset;
      } catch (RuntimeException ex) {
         if (loaded != null) loaded.dispose();
         unavailable.add(name);
         Gdx.app.error("ActorModels", "Cannot load " + name + "; original sprite retained.", ex);
         return null;
      }
   }

   private boolean isUsed(SpeciesAsset asset) {
      for (Actor actor : actors.values()) if (actor.asset == asset) return true;
      return false;
   }

   /**
    * glTF channels identify nodes by index, but ModelInstance copies them by
    * node id. Some exports use the same name for the conversion wrapper and
    * skeleton root. Give duplicate ids distinct names before copying, keeping
    * the loader's original direct animation/skin references intact. This only
    * changes our private loaded presentation asset, never the GLB or game state.
    */
   public static void prepareSceneAsset(SceneAsset asset) {
      Array<Node> nodes = new Array<>();
      for (Node node : asset.scene.model.nodes) collectNodes(node, nodes);
      Set<String> reserved = new HashSet<>();
      for (Node node : nodes) if (node.id != null) reserved.add(node.id);
      Set<String> seen = new HashSet<>();
      int sequence = 0;
      for (Node node : nodes) {
         if (node.id != null && seen.add(node.id)) continue;
         String replacement;
         do { replacement = "__pokewilds_node_" + sequence++; } while (reserved.contains(replacement));
         node.id = replacement;
         reserved.add(replacement);
         seen.add(replacement);
      }
   }

   private static void collectNodes(Node node, Array<Node> nodes) {
      nodes.add(node);
      for (Node child : node.getChildren()) collectNodes(child, nodes);
   }

   /**
    * Returns a constant presentation anchor for a clip's initial pose. A fresh
    * instance keeps this measurement from advancing or resetting a live actor's
    * animation. Call prepareSceneAsset before creating any asset instances.
    */
   public static Vector3 calculateClipAnchor(SceneAsset asset, String clip) {
      Scene sample = new Scene(asset.scene);
      if (clip != null) {
         sample.animationController.setAnimation(clip, -1);
         sample.animationController.update(0f);
      }
      return anchor(calculateVisualBounds(sample.modelInstance));
   }

   private static Vector3 anchor(BoundingBox bounds) {
      return new Vector3(bounds.getCenterX(), bounds.min.y, bounds.getCenterZ());
   }

   /**
    * Computes the actual shader-space geometry bounds, including skinning.
    * ModelInstance.calculateBoundingBox uses node global transforms for all
    * meshes; DefaultShader instead uses bone matrices for skinned renderables.
    * Assets whose export root has a 0.01 scale therefore need these visual bounds.
    * The model and all of its meshes, materials and bones are read only here.
    */
   public static BoundingBox calculateVisualBounds(ModelInstance model) {
      model.calculateTransforms();
      Array<Renderable> renderables = new Array<>();
      Pool<Renderable> pool = new Pool<Renderable>() {
         @Override protected Renderable newObject() { return new Renderable(); }
      };
      model.getRenderables(renderables, pool);
      IdentityHashMap<Mesh, float[]> verticesByMesh = new IdentityHashMap<>();
      BoundingBox bounds = new BoundingBox().inf();
      Vector3 point = new Vector3();
      for (Renderable renderable : renderables) {
         Mesh mesh = renderable.meshPart.mesh;
         float[] vertices = verticesByMesh.get(mesh);
         if (vertices == null) {
            vertices = new float[mesh.getNumVertices() * mesh.getVertexSize() / 4];
            mesh.getVertices(vertices);
            verticesByMesh.put(mesh, vertices);
         }
         VertexAttribute position = mesh.getVertexAttribute(Usage.Position);
         if (position == null || position.numComponents < 3) continue;
         Array<VertexAttribute> weights = new Array<>();
         for (VertexAttribute attribute : mesh.getVertexAttributes()) {
            if (attribute.usage == Usage.BoneWeight) weights.add(attribute);
         }
         int stride = mesh.getVertexSize() / 4;
         int offset = renderable.meshPart.offset;
         int size = renderable.meshPart.size;
         short[] indices = null;
         if (mesh.getNumIndices() > 0) {
            indices = new short[size];
            mesh.getIndices(offset, size, indices, 0);
         }
         for (int i = 0; i < size; i++) {
            int vertex = (indices == null ? offset + i : indices[i] & 0xffff) * stride;
            int p = vertex + position.offset / 4;
            float x = vertices[p];
            float y = vertices[p + 1];
            float z = vertices[p + 2];
            if (renderable.bones != null && weights.size > 0) {
               float sx = 0f, sy = 0f, sz = 0f;
               for (VertexAttribute weightAttribute : weights) {
                  int w = vertex + weightAttribute.offset / 4;
                  int boneIndex = (int)vertices[w];
                  float weight = vertices[w + 1];
                  if (weight == 0f) continue;
                  if (boneIndex < 0 || boneIndex >= renderable.bones.length) {
                     throw new IllegalArgumentException("Vertex references an invalid skin bone " + boneIndex);
                  }
                  Matrix4 bone = renderable.bones[boneIndex];
                  if (bone == null) {
                     sx += x * weight;
                     sy += y * weight;
                     sz += z * weight;
                  } else {
                     float[] m = bone.val;
                     sx += (m[Matrix4.M00] * x + m[Matrix4.M01] * y + m[Matrix4.M02] * z + m[Matrix4.M03]) * weight;
                     sy += (m[Matrix4.M10] * x + m[Matrix4.M11] * y + m[Matrix4.M12] * z + m[Matrix4.M13]) * weight;
                     sz += (m[Matrix4.M20] * x + m[Matrix4.M21] * y + m[Matrix4.M22] * z + m[Matrix4.M23]) * weight;
                  }
               }
               point.set(sx, sy, sz);
            } else {
               point.set(x, y, z);
            }
            point.mul(renderable.worldTransform);
            bounds.ext(point);
         }
      }
      if (!Float.isFinite(bounds.getHeight()) || bounds.getHeight() < 0.0001f) {
         throw new IllegalArgumentException("Model has invalid or empty geometry bounds.");
      }
      return bounds;
   }

   private static String animation(ModelInstance model, String... terms) {
      for (String term : terms) {
         for (Animation animation : model.animations) {
            if (animation.id.toLowerCase(Locale.ROOT).contains(term)) return animation.id;
         }
      }
      return null;
   }

   /** Call between the opaque terrain pass and transparent overlays. */
   public void render(Camera camera, Color tint) {
      if (!enabled || queued.isEmpty()) return;
      if (batch == null) {
         DefaultShader.Config config = new DefaultShader.Config();
         config.numBones = MAX_BONES;
         config.numDirectionalLights = 1;
         config.numPointLights = 0;
         config.numSpotLights = 0;
         batch = new ModelBatch(new DefaultShaderProvider(config));
      }
      ambient.color.set(0.68f * tint.r, 0.68f * tint.g, 0.68f * tint.b, 1f);
      sun.color.set(0.62f * tint.r, 0.60f * tint.g, 0.55f * tint.b, 1f);
      boolean players = false;
      boolean pokemon = false;
      boolean animated = false;
      batch.begin(camera);
      try {
         for (Actor actor : queued) {
            batch.render(actor.instance, environment);
            players |= actor.player;
            pokemon |= !actor.player;
            animated |= actor.animated;
         }
      } finally {
         batch.end();
      }
      if (players) playerModelFrames++;
      if (pokemon) pokemonModelFrames++;
      if (animated) animatedModelFrames++;
   }

   public long getPlayerModelFrames() { return playerModelFrames; }
   public long getPokemonModelFrames() { return pokemonModelFrames; }
   public long getAnimatedModelFrames() { return animatedModelFrames; }
   public int getLoadedSpeciesCount() { return species.size(); }
   public boolean hasLoadedSpecies(String name) { return species.containsKey(name); }

   public void dispose() {
      for (Actor actor : actors.values()) actor.dispose();
      actors.clear();
      for (SpeciesAsset asset : species.values()) asset.dispose();
      species.clear();
      queued.clear();
      unavailable.clear();
      if (batch != null) batch.dispose();
      batch = null;
   }

   private static final class SpeciesAsset {
      final SceneAsset asset;
      final Map<String, Vector3> anchors = new HashMap<>();
      long lastSeen;
      SpeciesAsset(SceneAsset asset) { this.asset = asset; }
      void dispose() { asset.dispose(); }
   }

   private static final class Actor {
      final String key;
      final ModelInstance instance;
      final Model ownedModel;
      SpeciesAsset asset;
      Scene scene;
      BoundingBox bounds;
      Vector3 anchor;
      String idle;
      String walk;
      String clip;
      boolean player;
      boolean animated;
      boolean hasPosition;
      float x;
      float y;
      float movement;
      float phase;
      long lastSeen;

      Actor(String key, ModelInstance instance, Model ownedModel) {
         this.key = key;
         this.instance = instance;
         this.ownedModel = ownedModel;
      }

      void dispose() { if (ownedModel != null) ownedModel.dispose(); }
   }
}
