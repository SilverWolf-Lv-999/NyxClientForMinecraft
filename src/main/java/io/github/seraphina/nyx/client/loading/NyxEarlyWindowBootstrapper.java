package io.github.seraphina.nyx.client.loading;

import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NyxEarlyWindowBootstrapper implements GraphicsBootstrapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(NyxEarlyWindowBootstrapper.class);

    @Override
    public String name() {
        return "nyxclient-early-window";
    }

    @Override
    public void bootstrap(String[] arguments) {
        if (Boolean.getBoolean("nyxclient.skipEarlyWindowProvider")) {
            return;
        }

        try {
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER, NyxEarlyWindowProvider.PROVIDER_NAME);
            NyxEarlyLocatedPathCleaner.removeNyxModPathsFromFmlLocated("graphics bootstrapper");
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to select Nyx early window provider", throwable);
        }
    }
}
