package com.pkmngen.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ScreenUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/** Compare actual CPU/GPU data with the frozen old implementation, not stubs. */
public final class WorldMeshBatchTest {
   private static int checks;
   private static void require(boolean ok, String message) {
      checks++;
      if (!ok) throw new IllegalStateException(message);
   }
   private static Object field(Object owner, String name) throws Exception {
      Field field = owner.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return field.get(owner);
   }
   private static Method method(Class<?> owner, String name, Class<?>... arguments) throws Exception {
      Method method = owner.getDeclaredMethod(name, arguments);
      method.setAccessible(true);
      return method;
   }
   public static void main(String[] args) {
      Probe probe = new Probe();
      Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
      cfg.setWindowedMode(160,144); cfg.setInitialVisible(false); cfg.useVsync(false);
      cfg.setTitle("World mesh extraction regression");
      try {
         new Lwjgl3Application(probe,cfg);
         require(probe.complete,"Probe did not complete");
         System.out.println("WORLD MESH BATCH PASS: " + checks + " assertions; real GL, chunk boundaries, reuse, eviction, borrowed textures");
      } catch (Throwable ex) { ex.printStackTrace(); System.exit(1); }
   }
   private static final class Probe extends ApplicationAdapter {
      boolean complete;
      @Override public void create() {
         try { verify(); complete=true; Gdx.app.exit(); }
         catch(Exception ex) { throw new RuntimeException(ex); }
      }
      private void verify() throws Exception {
         String vertex="attribute vec3 a_position; attribute vec4 a_color; attribute vec2 a_texCoord0; attribute float a_style; attribute float a_anchorDepth; varying vec4 c; varying vec2 uv; void main(){c=a_color;c.a*=255.0/254.0;uv=a_texCoord0;gl_Position=vec4(a_position,1.0);gl_Position.z+=a_style*.0001+a_anchorDepth*.0001;}";
         String fragment="#ifdef GL_ES\nprecision mediump float;\n#endif\nuniform sampler2D u_texture;varying vec4 c;varying vec2 uv;void main(){vec4 p=texture2D(u_texture,uv)*c;if(p.a<.1)discard;gl_FragColor=p;}";
         ShaderProgram shader=new ShaderProgram(vertex,fragment);
         require(shader.isCompiled(),shader.getLog());
         Pixmap pixels=new Pixmap(2,2,Pixmap.Format.RGBA8888);
         pixels.setColor(1,.5f,.2f,1);pixels.fill();pixels.drawPixel(0,0,0x4faadaff);
         Texture a=new Texture(pixels);
         pixels.setColor(.2f,.8f,.4f,.5f);pixels.fill();
         Texture b=new Texture(pixels);pixels.dispose();
         WorldMeshBatch current=new WorldMeshBatch();
         ReferenceJohtoRenderer original=new ReferenceJohtoRenderer();
         @SuppressWarnings("unchecked") Map<Texture,Object> oldGroups=(Map<Texture,Object>)field(original,"geometry");
         Method oldBatch=method(ReferenceJohtoRenderer.class,"batch",Map.class,Texture.class);
         Method oldUpload=method(ReferenceJohtoRenderer.class,"upload",Map.class);
         Method oldDraw=method(ReferenceJohtoRenderer.class,"renderGroups",Map.class);
         Field oldShader=ReferenceJohtoRenderer.class.getDeclaredField("shader");oldShader.setAccessible(true);oldShader.set(original,shader);
         try {
            int[] counts={3,6,65532,65535,131067,3,0,6};
            Object prior=null; Mesh priorMesh=null;
            for(int iteration=0;iteration<counts.length;iteration++) {
               current.clear();
               for(Object group:oldGroups.values())((FloatArray)field(group,"vertices")).clear();
               if(counts[iteration]>0) {
                  WorldMeshBatch.Buffer next=current.buffer(a);
                  if(prior!=null)require(prior==next,"CPU buffer was not reused across frames");
                  Object legacy=oldBatch.invoke(original,oldGroups,a);
                  append(next,(FloatArray)field(legacy,"vertices"),counts[iteration],false);
                  if(iteration%2==0)append(current.buffer(b),(FloatArray)field(oldBatch.invoke(original,oldGroups,b),"vertices"),6,true);
               }
               current.upload();oldUpload.invoke(null,oldGroups);
               @SuppressWarnings("unchecked") Map<Texture,Object> newGroups=(Map<Texture,Object>)field(current,"groups");
               require(newGroups.size()==oldGroups.size(),"Group eviction differs");
               for(Texture texture:oldGroups.keySet()) {
                  Object newer=newGroups.get(texture),older=oldGroups.get(texture);
                  FloatArray nv=(FloatArray)field(newer,"vertices"),ov=(FloatArray)field(older,"vertices");
                  require(nv.size==ov.size,"Vertex stride/count differs");
                  for(int i=0;i<nv.size;i++)require(Float.floatToRawIntBits(nv.items[i])==Float.floatToRawIntBits(ov.items[i]),"CPU vertex bits differ at "+i);
                  Mesh[] nm=(Mesh[])field(newer,"meshes"),om=(Mesh[])field(older,"meshes");
                  require(nm.length==om.length,"Chunk allocation differs");
                  for(int i=0;i<nm.length;i++) {
                     require(nm[i].getMaxVertices()==om[i].getMaxVertices(),"GPU capacity differs");
                     require(nm[i].getNumVertices()==om[i].getNumVertices(),"Uploaded vertex count differs");
                     require(nm[i].getVertexAttributes().equals(om[i].getVertexAttributes()),"Vertex layout differs");
                  }
               }
               if(counts[iteration]>0) {
                  Object buffer=newGroups.get(a);Mesh mesh=((Mesh[])field(buffer,"meshes"))[0];
                  if(priorMesh!=null&&priorMesh.getMaxVertices()>=counts[iteration]&&counts[iteration]<=65532)
                     require(priorMesh==mesh,"Reusable mesh was replaced");
                  prior=buffer;priorMesh=mesh;
               } else {prior=null;priorMesh=null;require(newGroups.isEmpty(),"Empty frame retained texture keys");}
               byte[] before=capture(shader,()->oldDraw.invoke(original,oldGroups));
               byte[] after=capture(shader,()->current.render(shader));
               require(Arrays.equals(before,after),"GPU pixels differ in case "+iteration);
               System.out.println("MESH case="+iteration+" vertices="+counts[iteration]+" framebuffer identical");
            }
            current.dispose();current.dispose();
            require(((Map<?,?>)field(current,"groups")).isEmpty(),"Dispose retained groups");
            require(Gdx.gl.glIsTexture(a.getTextureObjectHandle())&&Gdx.gl.glIsTexture(b.getTextureObjectHandle()),"Batch disposed borrowed textures");
            WorldMeshBatch.Buffer reused=current.buffer(a);
            append(reused,new FloatArray(),3,false);current.upload();capture(shader,()->current.render(shader));
            current.dispose();
            require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL error after reuse/dispose");
         } finally {
            current.dispose();
            for(Object group:oldGroups.values())method(group.getClass(),"dispose").invoke(group);
            oldGroups.clear();oldShader.set(original,null);
            original.dispose();shader.dispose();a.dispose();b.dispose();
         }
      }
      private void append(WorldMeshBatch.Buffer target,FloatArray reference,int count,boolean foreground) {
         float[] xs={-.8f,.8f,0},ys={-.7f,-.7f,.8f};
         for(int i=0;i<count;i++) {
            int v=i%3;float x=xs[v],y=ys[v],z=foreground?-.2f:.2f;
            float color=Color.toFloatBits(.9f,1f,.8f,foreground?.7f:1f),u=v==0?0:1,t=v==2?0:1;
            float style=foreground?4f:0,depth=foreground?-.1f:0;
            target.vertex(x,y,z,color,u,t,style,depth);
            reference.addAll(x,y,z,color,u,t,style,depth);
         }
      }
      private byte[] capture(ShaderProgram shader,Draw draw) throws Exception {
         Gdx.gl.glViewport(0,0,160,144);Gdx.gl.glDepthMask(true);
         Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
         Gdx.gl.glDisable(GL20.GL_CULL_FACE);Gdx.gl.glEnable(GL20.GL_BLEND);
         Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA);
         Gdx.gl.glClearColor(.08f,.12f,.18f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
         shader.begin();shader.setUniformi("u_texture",0);
         try {draw.draw();} finally {shader.end();}
         byte[] result=ScreenUtils.getFrameBufferPixels(0,0,160,144,false);
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"Native GL error");
         return result;
      }
   }
   @FunctionalInterface private interface Draw {void draw() throws Exception;}
}
