package io.github.seraphina.nyx.client.module.other;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.ui.music.MusicPlayerScreen;

@ModuleInfo(name = "nyxclient.module.musicplayer.name", description = "nyxclient.module.musicplayer.description", category = Category.OTHER)
public class MusicPlayer extends Module {
    public static final MusicPlayer INSTANCE = new MusicPlayer();

    @Override
    public void onEnable() {
        super.onEnable();
        mc.setScreen(new MusicPlayerScreen());
        this.setEnabled(false);
    }
}
