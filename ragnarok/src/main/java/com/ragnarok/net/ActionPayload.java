package com.ragnarok.net;

import com.ragnarok.Ragnarok;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client -> server key presses. CHARGE is sent every tick while its key is held. */
public record ActionPayload(int action, int arg) implements CustomPayload {
    public static final int CAST = 1, NEXT = 2, PREV = 3, STYLE = 4, UPPERCUT = 5, KICK = 6, DODGE = 7, TRANSFORM = 8, CHARGE = 9;
    /** DODGE arg: 0 left, 1 right, 2 back. */

    public static final CustomPayload.Id<ActionPayload> ID = new CustomPayload.Id<>(Ragnarok.id("action"));
    public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ActionPayload::action,
            PacketCodecs.VAR_INT, ActionPayload::arg,
            ActionPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
