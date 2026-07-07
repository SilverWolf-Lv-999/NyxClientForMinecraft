package io.github.seraphina.nyx.client.utility.render;

public final class Shaders {
   public static Shader FONT;
   public static Shader GAUSSIAN_BLUR;

   private Shaders() {
   }

   public static void init() {
      if (FONT == null) {
         FONT = new Shader("font.vert", "font.frag");
      }
      if (GAUSSIAN_BLUR == null) {
         GAUSSIAN_BLUR = new Shader("gaussian_blur.vert", "gaussian_blur.frag");
      }
   }

   public static void close() {
      if (FONT != null) {
         FONT.close();
         FONT = null;
      }
      if (GAUSSIAN_BLUR != null) {
         GAUSSIAN_BLUR.close();
         GAUSSIAN_BLUR = null;
      }

   }
}
