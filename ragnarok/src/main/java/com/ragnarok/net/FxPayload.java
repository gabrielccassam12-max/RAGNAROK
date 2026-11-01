package com.ragnarok.net;

import com.ragnarok.Ragnarok;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> owner: camera effects. kind 0 = shake (amount = degrees), kind 1 = zoom (amount = fov factor). */
public record FxPayload(int kind, float amount, int ticks) implements CustomPayload {
    public static final int SHAKE = 0, ZOOM = 1;

    public static final CustomPayload.Id<FxPayload> ID = new CustomPayload.Id<>(Ragnarok.id("fx"));
    public static final PacketCodec<RegistryByteBuf, FxPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, FxPayload::kind,
            PacketCodecs.FLOAT, FxPayload::amount,
            PacketCodecs.VAR_INT, FxPayload::ticks,
            FxPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
