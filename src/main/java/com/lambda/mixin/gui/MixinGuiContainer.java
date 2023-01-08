package com.lambda.mixin.gui;

import com.lambda.client.gui.mc.LambdaGuiStealButton;
import com.lambda.client.gui.mc.LambdaGuiStoreButton;
import com.lambda.client.module.modules.player.ChestStealer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiContainer.class)
public class MixinGuiContainer extends GuiScreen {

    @Shadow protected int guiLeft;
    @Shadow protected int guiTop;
    @Shadow protected int xSize;
    private final GuiButton stealButton = new LambdaGuiStealButton(guiLeft + xSize + 2, guiTop + 2);
    private final GuiButton storeButton = new LambdaGuiStoreButton(guiLeft + xSize + 2, guiTop + 4 + stealButton.height);

    @Inject(method = "mouseClicked", at = @At("TAIL"))
    public void mouseClicked(int x, int y, int button, CallbackInfo ci) {
        if (button != 0) return;

        if (storeButton.mousePressed(mc, x, y)) {
            ChestStealer.INSTANCE.setStoring(!ChestStealer.INSTANCE.getStoring());
        } else if (stealButton.mousePressed(mc, x, y)) {
            ChestStealer.INSTANCE.setStealing(!ChestStealer.INSTANCE.getStealing());
        }
    }

    @Inject(method = "updateScreen", at = @At("TAIL"))
    public void updateScreen(CallbackInfo ci) {
        if (!ChestStealer.INSTANCE.isValidGui()) return;

        if (ChestStealer.INSTANCE.isDisabled()) {
            buttonList.remove(storeButton);
            buttonList.remove(storeButton);
            return;
        }

        if (!buttonList.contains(stealButton)) {
            buttonList.add(stealButton);
        }
        if (!buttonList.contains(storeButton)) {
            buttonList.add(storeButton);
        }

        ChestStealer.updateButton(stealButton, guiLeft, xSize, guiTop);
        ChestStealer.updateButton(storeButton, guiLeft, xSize, guiTop);
    }

}
