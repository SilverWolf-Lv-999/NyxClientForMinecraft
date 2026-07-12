package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.NyxClient;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@ModuleInfo(name = "nyxclient.module.networkoptimization.name", description = "nyxclient.module.networkoptimization.description", category = Category.CLIENT)
public class NetworkOptimization extends Module {
    public static final NetworkOptimization INSTANCE = new NetworkOptimization();

    public final BoolValue tcpNoDelay = ValueBuild.boolSetting("tcp nodelay", true, ignored -> refreshActiveConnections(), this);
    public final BoolValue writeBufferWaterMark = ValueBuild.boolSetting("write buffer watermark", true, ignored -> refreshActiveConnections(), this);
    public final IntValue writeBufferLowWaterMark = ValueBuild.intSetting("write buffer low kb", 32, 8, 1024, 8, () -> writeBufferWaterMark.getValue(), this);
    public final IntValue writeBufferHighWaterMark = ValueBuild.intSetting("write buffer high kb", 256, 32, 8192, 8, () -> writeBufferWaterMark.getValue(), this);
    public final BoolValue ipTos = ValueBuild.boolSetting("ip tos", true, ignored -> refreshActiveConnections(), this);
    public final EnumValue<IpTosMode> ipTosMode = ValueBuild.enumSetting("ip tos mode", IpTosMode.LOW_LATENCY, () -> ipTos.getValue(), ignored -> refreshActiveConnections(), this);
    public final BoolValue batchFlush = ValueBuild.boolSetting("batch flush", false, this);

    private final Set<Connection> activeConnections = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Connection, Boolean> originalTcpNoDelay = new WeakHashMap<>();
    private final Map<Connection, WriteBufferWaterMark> originalWriteBufferWaterMark = new WeakHashMap<>();
    private final Map<Connection, Integer> originalIpTos = new WeakHashMap<>();
    private int lastRuntimeSettingsSignature = Integer.MIN_VALUE;

    @Override
    public void onEnable() {
        registerCurrentConnection();
        refreshActiveConnections();
    }

    @Override
    public void onDisable() {
        restoreActiveConnections();
        flushActiveConnections();
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        refreshActiveConnectionsIfSettingsChanged();

        if (batchFlush.getValue()) {
            flushActiveConnections();
        }
    }

    public void registerConnection(Connection connection) {
        if (connection == null) {
            return;
        }

        synchronized (activeConnections) {
            activeConnections.add(connection);
        }

        if (isEnabled()) {
            applyChannelOptions(connection);
        }
    }

    public void unregisterConnection(Connection connection) {
        if (connection == null) {
            return;
        }

        synchronized (activeConnections) {
            activeConnections.remove(connection);
        }
        synchronized (originalTcpNoDelay) {
            originalTcpNoDelay.remove(connection);
        }
        synchronized (originalWriteBufferWaterMark) {
            originalWriteBufferWaterMark.remove(connection);
        }
        synchronized (originalIpTos) {
            originalIpTos.remove(connection);
        }
    }

    public boolean shouldDeferFlush(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
        return flush && isEnabled() && batchFlush.getValue() && listener == null && packet != null;
    }

    private void registerCurrentConnection() {
        if (mc.getConnection() != null) {
            registerConnection(mc.getConnection().getConnection());
        }
    }

    private void refreshActiveConnections() {
        if (!isEnabled()) {
            return;
        }

        lastRuntimeSettingsSignature = runtimeSettingsSignature();
        for (Connection connection : snapshotConnections()) {
            applyChannelOptions(connection);
        }
    }

    private void restoreActiveConnections() {
        for (Connection connection : snapshotConnections()) {
            restoreChannelOptions(connection);
        }
    }

    private void flushActiveConnections() {
        for (Connection connection : snapshotConnections()) {
            if (connection.isConnected()) {
                connection.flushChannel();
            }
        }
    }

    private ArrayList<Connection> snapshotConnections() {
        synchronized (activeConnections) {
            return new ArrayList<>(activeConnections);
        }
    }

    private void applyChannelOptions(Connection connection) {
        Channel channel = connection.channel();
        if (channel == null) {
            return;
        }

        runOnChannelEventLoop(channel, () -> {
            applyTcpNoDelay(connection, channel);
            applyWriteBufferWaterMark(connection, channel);
            applyIpTos(connection, channel);
        });
    }

    private void restoreChannelOptions(Connection connection) {
        Channel channel = connection.channel();
        if (channel == null) {
            return;
        }

        runOnChannelEventLoop(channel, () -> {
            restoreTcpNoDelay(connection, channel);
            restoreWriteBufferWaterMark(connection, channel);
            restoreIpTos(connection, channel);
        });
    }

    private void applyTcpNoDelay(Connection connection, Channel channel) {
        if (!tcpNoDelay.getValue()) {
            restoreTcpNoDelay(connection, channel);
            return;
        }

        try {
            Boolean current = channel.config().getOption(ChannelOption.TCP_NODELAY);
            synchronized (originalTcpNoDelay) {
                originalTcpNoDelay.putIfAbsent(connection, current);
            }
            channel.config().setOption(ChannelOption.TCP_NODELAY, true);
        } catch (RuntimeException ignored) {
            NyxClient.LOGGER.debug("TCP_NODELAY is not supported by {}", channel.getClass().getName());
        }
    }

    private void restoreTcpNoDelay(Connection connection, Channel channel) {
        Boolean original;
        synchronized (originalTcpNoDelay) {
            original = originalTcpNoDelay.remove(connection);
        }
        if (original == null) {
            return;
        }

        try {
            channel.config().setOption(ChannelOption.TCP_NODELAY, original);
        } catch (RuntimeException ignored) {
            NyxClient.LOGGER.debug("Could not restore TCP_NODELAY for {}", channel.getClass().getName());
        }
    }

    private void applyWriteBufferWaterMark(Connection connection, Channel channel) {
        if (!writeBufferWaterMark.getValue()) {
            restoreWriteBufferWaterMark(connection, channel);
            return;
        }

        try {
            WriteBufferWaterMark current = channel.config().getOption(ChannelOption.WRITE_BUFFER_WATER_MARK);
            synchronized (originalWriteBufferWaterMark) {
                originalWriteBufferWaterMark.putIfAbsent(connection, current);
            }
            channel.config().setOption(ChannelOption.WRITE_BUFFER_WATER_MARK, configuredWriteBufferWaterMark());
        } catch (RuntimeException ignored) {
            NyxClient.LOGGER.debug("WRITE_BUFFER_WATER_MARK is not supported by {}", channel.getClass().getName());
        }
    }

    private void restoreWriteBufferWaterMark(Connection connection, Channel channel) {
        WriteBufferWaterMark original;
        synchronized (originalWriteBufferWaterMark) {
            original = originalWriteBufferWaterMark.remove(connection);
        }
        if (original == null) {
            return;
        }

        try {
            channel.config().setOption(ChannelOption.WRITE_BUFFER_WATER_MARK, original);
        } catch (RuntimeException ignored) {
            NyxClient.LOGGER.debug("Could not restore WRITE_BUFFER_WATER_MARK for {}", channel.getClass().getName());
        }
    }

    private void applyIpTos(Connection connection, Channel channel) {
        if (!ipTos.getValue()) {
            restoreIpTos(connection, channel);
            return;
        }

        try {
            Integer current = channel.config().getOption(ChannelOption.IP_TOS);
            synchronized (originalIpTos) {
                originalIpTos.putIfAbsent(connection, current);
            }
            channel.config().setOption(ChannelOption.IP_TOS, ipTosMode.getValue().value);
        } catch (RuntimeException ignored) {
            NyxClient.LOGGER.debug("IP_TOS is not supported by {}", channel.getClass().getName());
        }
    }

    private void restoreIpTos(Connection connection, Channel channel) {
        Integer original;
        synchronized (originalIpTos) {
            original = originalIpTos.remove(connection);
        }
        if (original == null) {
            return;
        }

        try {
            channel.config().setOption(ChannelOption.IP_TOS, original);
        } catch (RuntimeException ignored) {
            NyxClient.LOGGER.debug("Could not restore IP_TOS for {}", channel.getClass().getName());
        }
    }

    private WriteBufferWaterMark configuredWriteBufferWaterMark() {
        int low = writeBufferLowWaterMark.getValue() * 1024;
        int high = writeBufferHighWaterMark.getValue() * 1024;
        return new WriteBufferWaterMark(low, Math.max(high, low + 8 * 1024));
    }

    private void refreshActiveConnectionsIfSettingsChanged() {
        int signature = runtimeSettingsSignature();
        if (signature != lastRuntimeSettingsSignature) {
            refreshActiveConnections();
        }
    }

    private int runtimeSettingsSignature() {
        int result = Boolean.hashCode(tcpNoDelay.getValue());
        result = 31 * result + Boolean.hashCode(writeBufferWaterMark.getValue());
        result = 31 * result + writeBufferLowWaterMark.getValue();
        result = 31 * result + writeBufferHighWaterMark.getValue();
        result = 31 * result + Boolean.hashCode(ipTos.getValue());
        result = 31 * result + ipTosMode.getValue().ordinal();
        return result;
    }

    private void runOnChannelEventLoop(Channel channel, Runnable action) {
        if (channel.eventLoop().inEventLoop()) {
            action.run();
        } else {
            channel.eventLoop().execute(action);
        }
    }

    public enum IpTosMode {
        LOW_LATENCY(0x10),
        HIGH_THROUGHPUT(0x08);

        private final int value;

        IpTosMode(int value) {
            this.value = value;
        }
    }
}
