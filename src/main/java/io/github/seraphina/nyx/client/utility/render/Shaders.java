package io.github.seraphina.nyx.client.utility.render;

public final class Shaders {
   public static Shader FONT;

   private Shaders() {
   }

   public static void init() {
      if (FONT != null) {
         return;
      }

      FONT = new Shader("font.vert", "font.frag");
   }

   public static void close() {
      if (FONT != null) {
         FONT.close();
         FONT = null;
      }

   }
}
