
package com.minato.mixin.items;

import com.minato.module.modules.render.MapPreview;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Optional;

@Mixin(FilledMapItem.class)
public class FilledMapItemMixin extends Item {
    public FilledMapItemMixin(Item.Settings settings) {
        super(settings);
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        return MapPreview.INSTANCE.isEnabled()
                ? Optional.of(new MapPreview.MapComponent(stack))
                : super.getTooltipData(stack);
    }
}
