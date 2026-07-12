package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.NyxClient;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
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
    public final BoolValue batchFlush = ValueBuild.boolSetting("batch flush", false, this);

    private final Set<Connection> activeConnections = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Connection, Boolean> originalTcpNoDelay = new WeakHashMap<>();

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
            applyTcpNoDelay(connection);
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

        for (Connection connection : snapshotConnections()) {
            applyTcpNoDelay(connection);
        }
    }

    private void restoreActiveConnections() {
        for (Connection connection : snapshotConnections()) {
            restoreTcpNoDelay(connection);
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

    private void applyTcpNoDelay(Connection connection) {
        if (!tcpNoDelay.getValue()) {
            restoreTcpNoDelay(connection);
            return;
        }

        Channel channel = connection.channel();
        if (channel == null) {
            return;
        }

        runOnChannelEventLoop(channel, () -> {
            try {
                Boolean current = channel.config().getOption(ChannelOption.TCP_NODELAY);
                synchronized (originalTcpNoDelay) {
                    originalTcpNoDelay.putIfAbsent(connection, current);
                }
                channel.config().setOption(ChannelOption.TCP_NODELAY, true);
            } catch (RuntimeException ignored) {
                NyxClient.LOGGER.debug("TCP_NODELAY is not supported by {}", channel.getClass().getName());
            }
        });
    }

    private void restoreTcpNoDelay(Connection connection) {
        Channel channel = connection.channel();
        if (channel == null) {
            return;
        }

        Boolean original;
        synchronized (originalTcpNoDelay) {
            original = originalTcpNoDelay.remove(connection);
        }
        if (original == null) {
            return;
        }

        runOnChannelEventLoop(channel, () -> {
            try {
                channel.config().setOption(ChannelOption.TCP_NODELAY, original);
            } catch (RuntimeException ignored) {
                NyxClient.LOGGER.debug("Could not restore TCP_NODELAY for {}", channel.getClass().getName());
            }
        });
    }

    private void runOnChannelEventLoop(Channel channel, Runnable action) {
        if (channel.eventLoop().inEventLoop()) {
            action.run();
        } else {
            channel.eventLoop().execute(action);
        }
    }
}
