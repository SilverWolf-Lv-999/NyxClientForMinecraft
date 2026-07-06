package io.github.seraphina.nyxclient.module.client;

import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.module.ModuleInfo;
import io.github.seraphina.nyxclient.ui.clickgui.ClickGuiUI;

@ModuleInfo(name = "nyxclient.module.clickgui.name", description = "nyxclient.module.clickgui.description", category = Category.OTHER)
public class ClickGui extends Module {
    public static final ClickGui INSTANCE = new ClickGui();
    private ClickGuiUI clickGui;
    public ClickGui() {
        this.setKey(256);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (clickGui == null) {
            clickGui = new ClickGuiUI();
        }
        mc.setScreen(clickGui);
        this.setEnabled(false);
    }
}
