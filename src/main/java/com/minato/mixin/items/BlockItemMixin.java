
package com.minato.mixin.items;

import com.minato.module.modules.render.ContainerPreview;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Optional;

@Mixin(BlockItem.class)
public class BlockItemMixin extends Item {
    public BlockItemMixin(Settings settings) {
        super(settings);
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        if (ContainerPreview.INSTANCE.isEnabled() && ContainerPreview.isPreviewableContainer(stack)) {
            return Optional.of(new ContainerPreview.ContainerComponent(stack));
        }
        return super.getTooltipData(stack);
    }
}
