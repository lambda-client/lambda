
package com.minato.mixin.render;

import com.minato.module.modules.render.ContainerPreview;
import com.minato.module.modules.render.MapPreview;
import net.minecraft.client.gui.tooltip.BundleTooltipComponent;
import net.minecraft.client.gui.tooltip.ProfilesTooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.item.tooltip.BundleTooltipData;
import net.minecraft.item.tooltip.TooltipData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TooltipComponent.class)
public interface TooltipComponentMixin {
    @Inject(method = "of(Lnet/minecraft/item/tooltip/TooltipData;)Lnet/minecraft/client/gui/tooltip/TooltipComponent;", at = @At("HEAD"), cancellable = true)
    private static void of(TooltipData tooltipData, CallbackInfoReturnable<TooltipComponent> cir) {
        if (ContainerPreview.INSTANCE.isEnabled() && tooltipData instanceof ContainerPreview.ContainerComponent containerComponent) {
            cir.setReturnValue(containerComponent);
            return;
        }

        if (MapPreview.INSTANCE.isEnabled()) switch (tooltipData) {
            case MapPreview.MapComponent mapComponent -> cir.setReturnValue(mapComponent);
            case BundleTooltipData bundleTooltipData -> cir.setReturnValue(new BundleTooltipComponent(bundleTooltipData.contents()));
            case ProfilesTooltipComponent.ProfilesData profilesData -> cir.setReturnValue(new ProfilesTooltipComponent(profilesData));
            default -> {} // ignore
        }
    }
}
