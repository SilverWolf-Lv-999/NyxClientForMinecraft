package io.github.seraphina.nyxclient.module.client;

import io.github.seraphina.nyxclient.manager.FontManager;
import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.module.ModuleInfo;
import io.github.seraphina.nyxclient.ui.clickgui.ClickGuiUI;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;

@ModuleInfo(name = "nyxclient.module.clickgui.name", description = "nyxclient.module.clickgui.description", category = Category.CLIENT)
public class ClickGui extends Module {
    public static final ClickGui INSTANCE = new ClickGui();
    private ClickGuiUI clickGui;
    public ClickGui() {
        this.setKey(GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        FontManager.initClickGuiFonts();
        if (clickGui == null) {
            clickGui = new ClickGuiUI();
        }
        mc.setScreen(clickGui);
        this.setEnabled(false);
    }
}
