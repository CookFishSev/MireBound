package com.fish.mirebound.mud.container;

import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudVolumeState;
import com.fish.mirebound.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Ordinary full block item with optional Silk-Touch-preserved finite volume. */
public final class MudBlockItem extends BlockItem {
    public MudBlockItem(MudBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        MudVolumeData data = context.getItemInHand().get(ModDataComponents.MUD_VOLUME.get());
        if (state == null || data == null || data.medium() != ((MudBlock) getBlock()).medium()) {
            return state;
        }
        return MudVolumeState.withPixels(state, data.pixels(), MudBlock.storedFacing(state));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        MudVolumeData data = stack.get(ModDataComponents.MUD_VOLUME.get());
        if (data != null) {
            tooltip.add(Component.translatable("item.mirebound.mud_volume", data.pixels())
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
