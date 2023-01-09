package com.lambda.mixin.gui;

import com.lambda.client.gui.mc.LambdaGuiAltManager;
import com.lambda.client.manager.managers.AltManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMultiplayer.class)
public class MixinGuiMultiplayer extends GuiScreen {
    @Inject(method = "actionPerformed", at = @At("HEAD"))
    private void actionPerformed(GuiButton button, CallbackInfo ci) {
        if (button.id == AltManager.BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(new LambdaGuiAltManager((GuiMultiplayer) (Object) this));
        }
    }

    @Inject(method = "connectToServer", at = @At("HEAD"))
    public void connectToServer(ServerData serverData, CallbackInfo ci) {
        if (mc.getCurrentServerData() != null && mc.world != null) {
            mc.world.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
        }
    }
}
