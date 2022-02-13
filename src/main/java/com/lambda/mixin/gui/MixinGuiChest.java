package com.lambda.mixin.gui;

import com.lambda.client.module.modules.render.ContainerPreview;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.IInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiChest.class)
public class MixinGuiChest {

    @Inject(method = "<init>", at = @At("RETURN"))
    public void drawScreen(IInventory upperInv, IInventory lowerInv, CallbackInfo ci) {
        if (lowerInv.getName().equals("Ender Chest")) {
            ContainerPreview.INSTANCE.setEnderChest(lowerInv);
        }
    }
}
