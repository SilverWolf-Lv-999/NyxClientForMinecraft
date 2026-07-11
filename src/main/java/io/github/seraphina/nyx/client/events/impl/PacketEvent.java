package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.callables.EventCancellable;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

public class PacketEvent {

    public static class Send extends EventCancellable {

        private Packet<?> packet;

        public Send(Packet<?> packet) {
            this.packet = packet;
        }

        public Packet<?> getPacket() {
            return this.packet;
        }

        public void setPacket(Packet<?> packet) {
            this.packet = packet;
        }

    }

    public static class Receive extends EventCancellable {

        private Packet<?> packet;
        private final Connection connection;

        public Receive(Packet<?> packet) {
            this(packet, null);
        }

        public Receive(Packet<?> packet, Connection connection) {
            this.packet = packet;
            this.connection = connection;
        }

        public Packet<?> getPacket() {
            return this.packet;
        }

        public void setPacket(Packet<?> packet) {
            this.packet = packet;
        }

        public Connection getConnection() {
            return this.connection;
        }

    }

}
