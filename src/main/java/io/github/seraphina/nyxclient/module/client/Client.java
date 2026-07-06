package io.github.seraphina.nyxclient.module.client;

import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.module.ModuleInfo;
import io.github.seraphina.nyxclient.value.ValueBuild;
import io.github.seraphina.nyxclient.value.impl.EnumValue;

@ModuleInfo(name = "nyxclient.module.client.name", description = "nyxclient.module.client.description", category = Category.CLIENT)
public class Client extends Module {
    public static final Client INSTANCE = new Client();

    public final EnumValue<Language> LANGUAGE = ValueBuild.enumSetting("language", Language.EN_US, this);

    public Client() {
        this.registerValue(LANGUAGE);
    }

    public enum Language {
        EN_US,ZH_CN,MINECRAFT_LANGUAGE;
    }
}
