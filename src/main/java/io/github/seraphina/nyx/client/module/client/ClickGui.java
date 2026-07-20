package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.manager.FontManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.ui.LuaScreen;
import io.github.seraphina.nyx.client.ui.clickgui.ArrayListClickGui;
import io.github.seraphina.nyx.client.ui.clickgui.ClickGuiUI;
import io.github.seraphina.nyx.client.ui.clickgui.JelloClickGui;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;

@ModuleInfo(name = "nyxclient.module.clickgui.name", description = "nyxclient.module.clickgui.description", category = Category.CLIENT)
public class ClickGui extends Module {
    public static final ClickGui INSTANCE = new ClickGui();

    public ClickGui() {
        this.setKey(GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        FontManager.initClickGuiFonts();
        LuaScreen screen = switch (Client.INSTANCE.clickGuiCategory.getValue()) {
            case SERAPHINA -> {
                ClickGuiUI.INSTANCE.beginOpenAnimation();
                yield ClickGuiUI.INSTANCE;
            }
            case JELLO_FOR_SIGMA -> {
                JelloClickGui.INSTANCE.beginOpenAnimation();
                yield JelloClickGui.INSTANCE;
            }
            case ARRAY_LIST -> {
                ArrayListClickGui.INSTANCE.beginOpenAnimation();
                yield ArrayListClickGui.INSTANCE;
            }
        };
        mc.setScreen(screen);
        this.setEnabled(false);
    }
}
