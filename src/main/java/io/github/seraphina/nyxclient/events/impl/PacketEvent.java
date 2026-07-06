package io.github.seraphina.nyxclient.events.impl;

import io.github.seraphina.nyxclient.events.api.events.callables.EventCancellable;
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

        public Receive(Packet<?> packet) {
            this.packet = packet;
        }

        public Packet<?> getPacket() {
            return this.packet;
        }

        public void setPacket(Packet<?> packet) {
            this.packet = packet;
        }

    }

}
