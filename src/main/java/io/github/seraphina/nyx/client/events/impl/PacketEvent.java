package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.callables.EventCancellable;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

public class PacketEvent {

    @Setter
    @Getter
    public static class Send extends EventCancellable {

        private Packet<?> packet;

        public Send(Packet<?> packet) {
            this.packet = packet;
        }

    }

    @Getter
    public static class Receive extends EventCancellable {

        @Setter
        private Packet<?> packet;
        private final Connection connection;

        public Receive(Packet<?> packet) {
            this(packet, null);
        }

        public Receive(Packet<?> packet, Connection connection) {
            this.packet = packet;
            this.connection = connection;
        }

    }

}
