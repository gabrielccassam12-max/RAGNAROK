package com.ragnarok.item;

import com.ragnarok.Ragnarok;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
    private ModItems() {}

    private static Item core(String name, int elem) {
        return Registry.register(Registries.ITEM, Ragnarok.id(name),
                new CoreItem(elem, new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));
    }

    public static final Item EMBER_CORE = core("ember_core", 0);
    public static final Item STORM_CORE = core("storm_core", 1);
    public static final Item FROST_CORE = core("frost_core", 2);
    public static final Item VOID_CORE = core("void_core", 3);

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> {
            e.add(EMBER_CORE);
            e.add(STORM_CORE);
            e.add(FROST_CORE);
            e.add(VOID_CORE);
        });
    }
}
