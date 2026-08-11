package io.github.seraphina.nyx.client.module.other;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.ui.music.MusicPlayerScreen;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.EnumValue;

@ModuleInfo(name = "nyxclient.module.musicplayer.name", description = "nyxclient.module.musicplayer.description", category = Category.OTHER)
public class MusicPlayer extends Module {
    public static final MusicPlayer INSTANCE = new MusicPlayer();

    public final EnumValue<From> from = ValueBuild.enumSetting("target", From.SMTC, this);

    @Override
    public void onEnable() {
        super.onEnable();
        mc.setScreen(new MusicPlayerScreen());
        this.setEnabled(false);
    }

    public enum From {
        CLIENT,
        SMTC
    }
}
