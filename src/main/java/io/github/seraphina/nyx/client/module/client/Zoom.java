package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.KeyPressEvent;
import io.github.seraphina.nyx.client.events.impl.MouseScrollEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import org.lwjgl.glfw.GLFW;

@ModuleInfo(name = "nyxclient.module.zoom.name", description = "nyxclient.module.zoom.description", category = Category.CLIENT)
public class Zoom extends Module {
    public static final Zoom INSTANCE = new Zoom();

    private static final double SCROLL_ZOOM_STEP = 0.25D;

    public Zoom() {
        this.setKey(GLFW.GLFW_KEY_C);
    }

    public final DoubleValue zoom = ValueBuild.doubleSetting("zoom", 4.0D, 1.0D, 50.0D, 0.25D, this);
    public final BoolValue scrollWheelZoom = ValueBuild.boolSetting("scroll wheel zoom", false, this);

    public float applyZoom(float fov) {
        if (!this.isEnabled()) {
            return fov;
        }

        return Math.max(1.0F, fov / this.zoom.getValue().floatValue());
    }

    @EventTarget
    public void onKeyPress(KeyPressEvent event) {
        if (event.getAction() == GLFW.GLFW_RELEASE && event.getKey() == this.getKey()) {
            this.setEnabled(false);
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onMouseScroll(MouseScrollEvent event) {
        if (!this.scrollWheelZoom.getValue() || mc.screen != null || mc.player == null) {
            return;
        }

        double scrollY = event.getScrollY();
        if (scrollY != 0.0D) {
            this.zoom.setValue(this.zoom.getValue() + Math.signum(scrollY) * SCROLL_ZOOM_STEP);
        }

        event.setCancelled(true);
    }
}
