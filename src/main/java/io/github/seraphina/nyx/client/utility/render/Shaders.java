package io.github.seraphina.nyx.client.utility.render;

public final class Shaders {
   public static Shader FONT;
   public static Shader GAUSSIAN_BLUR;
   public static Shader BLOOM;
   public static Shader KAWASE_BLUR_DOWN;
   public static Shader KAWASE_BLUR_UP;
   public static Shader SHADOW;
   public static Shader SHADOW_MASK;

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
      if (KAWASE_BLUR_DOWN == null) {
         KAWASE_BLUR_DOWN = new Shader("kawase_blur.vert", "kawase_blur_down.frag");
      }
      if (KAWASE_BLUR_UP == null) {
         KAWASE_BLUR_UP = new Shader("kawase_blur.vert", "kawase_blur_up.frag");
      }
      if (SHADOW == null) {
         SHADOW = new Shader("shadow.vert", "shadow.frag");
      }
      if (SHADOW_MASK == null) {
         SHADOW_MASK = new Shader("shadow_mask.vert", "shadow_mask.frag");
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
      if (KAWASE_BLUR_DOWN != null) {
         KAWASE_BLUR_DOWN.close();
         KAWASE_BLUR_DOWN = null;
      }
      if (KAWASE_BLUR_UP != null) {
         KAWASE_BLUR_UP.close();
         KAWASE_BLUR_UP = null;
      }
      if (SHADOW != null) {
         SHADOW.close();
         SHADOW = null;
      }
      if (SHADOW_MASK != null) {
         SHADOW_MASK.close();
         SHADOW_MASK = null;
      }
   }
}
