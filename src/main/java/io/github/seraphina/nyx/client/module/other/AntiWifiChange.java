package io.github.seraphina.nyx.client.module.other;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.NetworkChangedEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.ui.player.MutiPlayerUI;
import io.github.seraphina.nyx.client.utility.SeraNative;

@ModuleInfo(name = "nyxclient.module.antiwifichange.name", description = "nyxclient.module.antiwifichange.description", category = Category.OTHER)
public final class AntiWifiChange extends Module {
    public static final AntiWifiChange INSTANCE = new AntiWifiChange();

    private static final String[] EMPTY_DNS_RESULTS = new String[0];

    @Override
    public void onEnable() {
        SeraNative.startNetworkChangeMonitor();
        SeraNative.flushDnsResolverCache();
    }

    @Override
    public void onDisable() {
        SeraNative.setNetworkChangeEventDispatchEnabled(false);
    }

    @EventTarget
    public void onNetworkChanged(NetworkChangedEvent event) {
        SeraNative.flushDnsResolverCache();
        refreshServerList();
    }

    public static String[] resolveHostnameWithoutCache(String hostname) {
        if (!INSTANCE.isEnabled() || hostname == null || hostname.isBlank()) {
            return EMPTY_DNS_RESULTS;
        }
        return SeraNative.resolveHostnameWithoutCache(hostname);
    }

    private void refreshServerList() {
        if (mc.screen instanceof MutiPlayerUI mutiPlayerUI) {
            mutiPlayerUI.refreshAfterNetworkChange();
        }
    }
}
