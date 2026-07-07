package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.RenderItemInHandEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;

@ModuleInfo(name = "nyxclient.module.animations.name", description = "nyxclient.module.animations.description", category = Category.VISUAL)
public class Animations extends Module {
    public static final Animations INSTANCE = new Animations();

    public final BoolValue noEatBobbing = ValueBuild.boolSetting("noeatbobbing", false, this);
    public final DoubleValue value = ValueBuild.doubleValue("scale", 1.0, 0.1, 10.0, 0.1, this);
    public final DoubleValue xPos = ValueBuild.doubleValue("xpos", 0.0, -10.0, 10.0, 0.01, this);
    public final DoubleValue yPos = ValueBuild.doubleValue("ypos", 0.0, -10.0, 10.0, 0.01, this);
    public final DoubleValue zPos = ValueBuild.doubleValue("zpos", 0.0, -10.0, 10.0, 0.01, this);
    public final DoubleValue xRot = ValueBuild.doubleValue("xrot", 0.0, -180.0, 180.0, 1.0, this);
    public final DoubleValue yRot = ValueBuild.doubleValue("yrot", 0.0, -180.0, 180.0, 1.0, this);
    public final DoubleValue zRot = ValueBuild.doubleValue("zrot", 0.0, -180.0, 180.0, 1.0, this);

    public boolean shouldDisableEatBobbing() {
        return isEnabled() && noEatBobbing.getValue();
    }

    @EventTarget
    public void render(RenderItemInHandEvent event) {
        event.setScale(value.getValue().floatValue());
        event.setXPos(xPos.getValue());
        event.setYPos(yPos.getValue());
        event.setZPos(zPos.getValue());
        event.setXRot(xRot.getValue());
        event.setYRot(yRot.getValue());
        event.setZRot(zRot.getValue());
    }
}
