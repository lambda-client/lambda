package com.lambda.mixin.gui;

import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMultiplayer.class)
public class MixinGuiMultiplayer extends GuiScreen {

    @Inject(method = "connectToServer", at = @At("HEAD"))
    public void connectToServer(ServerData serverData, CallbackInfo ci) {
        if (mc.getCurrentServerData() != null && mc.world != null) {
            mc.world.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
        }
    }

}
