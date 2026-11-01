package com.ragnarok.net;

import com.ragnarok.Ragnarok;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/** Server -> owner: everything the HUD and the menu show. */
public record SyncPayload(int unlocked, int essence, int max, int style, int ability, List<Integer> xp,
                          int cdLeft, int cdMax, int formElem, int formLeft, int formMax, int combo100,
                          boolean overloaded) implements CustomPayload {
    public static final CustomPayload.Id<SyncPayload> ID = new CustomPayload.Id<>(Ragnarok.id("sync"));
    public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.of(SyncPayload::write, SyncPayload::read);

    private static void write(SyncPayload p, RegistryByteBuf b) {
        b.writeVarInt(p.unlocked());
        b.writeVarInt(p.essence());
        b.writeVarInt(p.max());
        b.writeVarInt(p.style());
        b.writeVarInt(p.ability());
        b.writeVarInt(p.xp().size());
        for (int v : p.xp()) b.writeVarInt(v);
        b.writeVarInt(p.cdLeft());
        b.writeVarInt(p.cdMax());
        b.writeVarInt(p.formElem() + 1);
        b.writeVarInt(p.formLeft());
        b.writeVarInt(p.formMax());
        b.writeVarInt(p.combo100());
        b.writeBoolean(p.overloaded());
    }

    private static SyncPayload read(RegistryByteBuf b) {
        int unlocked = b.readVarInt();
        int essence = b.readVarInt();
        int max = b.readVarInt();
        int style = b.readVarInt();
        int ability = b.readVarInt();
        int n = b.readVarInt();
        List<Integer> xp = new ArrayList<>();
        for (int i = 0; i < n; i++) xp.add(b.readVarInt());
        int cdLeft = b.readVarInt();
        int cdMax = b.readVarInt();
        int formElem = b.readVarInt() - 1;
        int formLeft = b.readVarInt();
        int formMax = b.readVarInt();
        int combo100 = b.readVarInt();
        boolean overloaded = b.readBoolean();
        return new SyncPayload(unlocked, essence, max, style, ability, xp, cdLeft, cdMax, formElem, formLeft, formMax, combo100, overloaded);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
