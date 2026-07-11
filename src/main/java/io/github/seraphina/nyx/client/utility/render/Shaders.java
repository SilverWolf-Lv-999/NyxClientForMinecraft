package io.github.seraphina.nyx.client.utility.render;

public final class Shaders {
   public static Shader FONT;
   public static Shader GAUSSIAN_BLUR;
   public static Shader BLOOM;
   public static Shader ESP_GLOW;

   private Shaders() {
   }

   public static void init() {
      if (FONT == null) {
         FONT = new Shader("font.vert", "font.frag");
      }
      if (GAUSSIAN_BLUR == null) {
         GAUSSIAN_BLUR = new Shader("gaussian_blur.vert", "gaussian_blur.frag");
      }
      if (BLOOM == null) {
         BLOOM = new Shader("bloom.vert", "bloom.frag");
      }
      if (ESP_GLOW == null) {
         ESP_GLOW = new Shader("esp_glow.vert", "esp_glow.frag");
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
      if (BLOOM != null) {
         BLOOM.close();
         BLOOM = null;
      }
      if (ESP_GLOW != null) {
         ESP_GLOW.close();
         ESP_GLOW = null;
      }

   }
}
