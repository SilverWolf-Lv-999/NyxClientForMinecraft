package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.StringValue;

@ModuleInfo(name = "nyxclient.module.client.name", description = "nyxclient.module.client.description", category = Category.CLIENT)
public class Client extends Module {
    public static final Client INSTANCE = new Client();

    public final EnumValue<Language> LANGUAGE = ValueBuild.enumSetting("language", Language.EN_US, this);

    public final StringValue commandPrefix = ValueBuild.stringSetting("command prefix", ".", this);

    public enum Language {
        EN_US,ZH_CN,MINECRAFT_LANGUAGE;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.setEnabled(false);
    }
}
