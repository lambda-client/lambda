package com.lambda.client.mixin.client.gui;

import com.lambda.client.module.modules.render.ContainerPreview;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryEnderChest;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiChest.class)
public class MixinGuiChest {

    @Final @Shadow private IInventory lowerChestInventory;

    @Inject(method = "drawScreen", at = @At("RETURN"))
    public void drawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (lowerChestInventory.getName().equals("Ender Chest")) {
            ContainerPreview.INSTANCE.setEnderChestItems(lowerChestInventory);
        }
    }
}
