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
    private final GuiButton stealButton = new LambdaGuiStealButton(this.guiLeft + this.xSize + 2, this.guiTop + 2);
    private final GuiButton storeButton = new LambdaGuiStoreButton(this.guiLeft + this.xSize + 2, this.guiTop + 4 + stealButton.height);

    @Inject(method = "mouseClicked", at = @At("TAIL"))
    public void mouseClicked(int x, int y, int button, CallbackInfo ci) {
        if (button == 0) {
            if (storeButton.mousePressed(mc, x, y)) {
                ChestStealer.INSTANCE.setStoring(!ChestStealer.INSTANCE.getStoring());
            } else if (stealButton.mousePressed(mc, x, y)) {
                ChestStealer.INSTANCE.setStealing(!ChestStealer.INSTANCE.getStealing());
            }
        }
    }

    @Inject(method = "updateScreen", at = @At("TAIL"))
    public void updateScreen(CallbackInfo ci) {
        if (ChestStealer.INSTANCE.isValidGui()) {
            if (ChestStealer.INSTANCE.isEnabled()) {
                if (!this.buttonList.contains(stealButton)) {
                    this.buttonList.add(stealButton);
                }
                if (!this.buttonList.contains(storeButton)) {
                    this.buttonList.add(storeButton);
                }
                ChestStealer.updateButton(stealButton, this.guiLeft, this.xSize, this.guiTop);
                ChestStealer.updateButton(storeButton, this.guiLeft, this.xSize, this.guiTop);
            } else {
                this.buttonList.remove(storeButton);
                this.buttonList.remove(storeButton);
            }
        }
    }

}
