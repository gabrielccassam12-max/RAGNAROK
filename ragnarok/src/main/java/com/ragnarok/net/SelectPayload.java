package com.ragnarok.net;

import com.ragnarok.Ragnarok;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client -> server: choose a style and ability from the menu. */
public record SelectPayload(int style, int ability) implements CustomPayload {
    public static final CustomPayload.Id<SelectPayload> ID = new CustomPayload.Id<>(Ragnarok.id("select"));
    public static final PacketCodec<RegistryByteBuf, SelectPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SelectPayload::style,
            PacketCodecs.VAR_INT, SelectPayload::ability,
            SelectPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
