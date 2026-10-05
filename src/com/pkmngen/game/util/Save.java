package com.pkmngen.game.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.esotericsoftware.kryo.io.Input;
import com.pkmngen.game.Game;
import com.pkmngen.game.PkmnMap;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Save {
   private static final String JSON_EXTENSION = ".json.zip";
   private static final String ZIP_JSON_FILE = "data.json";

   public static <T> void saveData(@NotNull T data, String path) throws Save.SaveFailure {
      saveJson(data, path + ".json.zip");
   }

   private static <T> void saveJson(@NotNull T data, String path) throws Save.SaveFailure {
      try {
         ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(path))));

         try {
            Writer writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);

            try {
               zip.putNextEntry(new ZipEntry("data.json"));
               Json.gson.toJson(data, writer);
            } catch (Throwable var8) {
               try {
                  writer.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }

               throw var8;
            }

            writer.close();
         } catch (Throwable var9) {
            try {
               zip.close();
            } catch (Throwable var6) {
               var9.addSuppressed(var6);
            }

            throw var9;
         }

         zip.close();
      } catch (IOException e) {
         throw new Save.SaveFailure(e, path, Save.Format.GSON);
      }
   }

   public static void saveMinimap(PkmnMap map, String path) throws Save.SaveFailure {
      try {
         FileHandle file = new FileHandle(path);
         if (map.minimap == null) {
            map.minimap = new Pixmap(map.width(), map.height(), Pixmap.Format.RGBA8888);
            map.minimap.setColor(0.0F, 0.0F, 0.0F, 1.0F);
            map.minimap.fill();
         }

         PixmapIO.writePNG(file, map.minimap);
      } catch (GdxRuntimeException e) {
         throw new Save.SaveFailure(e, path, Save.Format.PIXMAP);
      }
   }

   @Nullable
   public static <T> T readDataIfPresent(String path, Class<T> type) throws Save.ReadFailure {
      T data = readJson(path + ".json.zip", type);
      return data != null ? data : readKryo(path, type);
   }

   @NotNull
   public static <T> T readData(String path, Class<T> type) throws Save.ReadFailure {
      return readDataIfPresent(path, type);
   }

   @Nullable
   private static <T> T readJson(String path, Class<T> type) throws Save.ReadFailure {
      if (!pathExists(path)) {
         return null;
      }

      try {
         ZipFile zip = new ZipFile(path);

         Object var4;
         try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(zip.getEntry("data.json")), StandardCharsets.UTF_8));

            try {
               var4 = Json.gson.fromJson(reader, type);
            } catch (Throwable var8) {
               try {
                  reader.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }

               throw var8;
            }

            reader.close();
         } catch (Throwable var9) {
            try {
               zip.close();
            } catch (Throwable var6) {
               var9.addSuppressed(var6);
            }

            throw var9;
         }

         zip.close();
         return (T)var4;
      } catch (IOException e) {
         throw Save.ReadFailure.parsing(e, path, Save.Format.GSON);
      }
   }

   @Nullable
   private static <T> T readKryo(String path, Class<T> type) throws Save.ReadFailure {
      if (!pathExists(path)) {
         return null;
      }

      try {
         FileHandle file = Gdx.files.local(path);
         InflaterInputStream inputStream = new InflaterInputStream(file.read());
         Input input = new Input(inputStream);

         Object var5;
         try {
            var5 = Game.staticGame.server.getKryo().readObject(input, type);
         } catch (Throwable var8) {
            try {
               input.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }

            throw var8;
         }

         input.close();
         return (T)var5;
      } catch (Exception e) {
         throw Save.ReadFailure.parsing(e, path, Save.Format.KRYO);
      }
   }

   public static boolean isDataPresent(String path) {
      return isJsonPresent(path) || isKryoPresent(path);
   }

   private static boolean isJsonPresent(String path) {
      return pathExists(path + ".json.zip");
   }

   private static boolean isKryoPresent(String path) {
      return pathExists(path);
   }

   private static boolean pathExists(String path) {
      return Gdx.files.local(path).exists();
   }

   private enum Format {
      KRYO,
      PIXMAP,
      GSON;
   }

   public static class ReadFailure extends Exception {
      private ReadFailure(String message, Throwable cause) {
         super(message, cause);
      }

      public ReadFailure(String message) {
         super(message);
      }

      public static Save.ReadFailure parsing(Throwable cause, String path, Save.Format format) {
         return new Save.ReadFailure("<" + format + "> Failed to read file: " + path, cause);
      }

      public static Save.ReadFailure fileMissing(String path) {
         return new Save.ReadFailure("No file found: " + path);
      }
   }

   public static class SaveFailure extends Exception {
      private final String path;
      private final Save.Format format;

      public SaveFailure(Throwable cause, String path, Save.Format format) {
         super(cause);
         this.path = path;
         this.format = format;
      }

      @Override
      public String getMessage() {
         return "<" + this.format + "> Failed to save file " + this.path + ":\n" + super.getMessage();
      }
   }
}
