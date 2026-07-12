package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;

@ModuleInfo(name = "nyxclient.module.keystrokes.name", description = "nyxclient.module.keystrokes.description", category = Category.VISUAL)
public class KeyStrokes extends Module {
    public static final KeyStrokes INSTANCE = new KeyStrokes();
}
