package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TextEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.StringValue;

@ModuleInfo(name = "nyxclient.module.nameprotection.name", description = "nyxclient.module.nameprotection.description", category = Category.CLIENT)
public class NameProtection extends Module {
    public static final NameProtection INSTANCE = new NameProtection();

    public final StringValue name = ValueBuild.stringSetting("name","NyxUser", this);

    @EventTarget
    public void text(TextEvent event) {
        if (isNull()) return;
        String aname = mc.player.getDisplayName().getString();
        if (!event.getText().contains(aname)) return;
        String text = event.getText().replace(aname, name.getValue());
        event.setText(text);
    }
}
