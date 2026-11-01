package com.ragnarok.item;

import com.ragnarok.core.Manager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Right-click once to unlock an element for good (it is kept after death). */
public class CoreItem extends Item {
    private final int element;

    public CoreItem(int element, Settings settings) {
        super(settings);
        this.element = element;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity sp)) return TypedActionResult.success(stack);
        if (Manager.get(sp).has(element)) {
            sp.sendMessage(Text.translatable("message.ragnarok.already").formatted(Formatting.YELLOW), true);
            return TypedActionResult.fail(stack);
        }
        Manager.unlock(sp, element);
        if (!sp.isCreative()) stack.decrement(1);
        return TypedActionResult.success(stack);
    }
}
