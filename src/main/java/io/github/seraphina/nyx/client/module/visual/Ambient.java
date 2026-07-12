package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.mixins.FogRendererAccessor;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;

@ModuleInfo(name = "nyxclient.module.ambient.name", description = "nyxclient.module.ambient.description", category = Category.VISUAL)
public class Ambient extends Module {
    public static final Ambient INSTANCE = new Ambient();

    public final BoolValue time = ValueBuild.boolSetting("time", true, this);
    public final IntValue clientTime = ValueBuild.intSetting("client time", 6000, 0, 24000, 100, time::getValue, this);

    public final EnumValue<WeatherMode> weather = ValueBuild.enumSetting("weather", WeatherMode.CLEAR, this);
    public final IntValue rainStrength = ValueBuild.intSetting("rain strength", 100, 0, 100, 1, () -> weather.is(WeatherMode.RAIN) || weather.is(WeatherMode.THUNDER), this);
    public final IntValue thunderStrength = ValueBuild.intSetting("thunder strength", 100, 0, 100, 1, () -> weather.is(WeatherMode.THUNDER), this);

    public final BoolValue fog = ValueBuild.boolSetting("fog", true, enabled -> applyFogState(), this);

    @Override
    public void onEnable() {
        applyFogState();
    }

    @Override
    public void onDisable() {
        FogRendererAccessor.nyx$setFogEnabled(true);
    }

    @EventTarget
    public void onTick(TickEvent.Post event) {
        applyFogState();
    }

    public boolean shouldChangeTime() {
        return isEnabled() && time.getValue();
    }

    public long getClientTime() {
        return clientTime.getValue();
    }

    public boolean shouldChangeWeather() {
        return isEnabled() && !weather.is(WeatherMode.VANILLA);
    }

    public float getRainLevel() {
        return switch (weather.getValue()) {
            case VANILLA -> 0.0F;
            case CLEAR -> 0.0F;
            case RAIN, THUNDER -> rainStrength.getValue() / 100.0F;
        };
    }

    public float getThunderLevel() {
        return weather.is(WeatherMode.THUNDER) ? thunderStrength.getValue() / 100.0F : 0.0F;
    }

    public boolean shouldDisableFog() {
        return isEnabled() && !fog.getValue();
    }

    private void applyFogState() {
        FogRendererAccessor.nyx$setFogEnabled(!shouldDisableFog());
    }

    public enum WeatherMode {
        VANILLA("Vanilla"),
        CLEAR("Clear"),
        RAIN("Rain"),
        THUNDER("Thunder");

        private final String name;

        WeatherMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
