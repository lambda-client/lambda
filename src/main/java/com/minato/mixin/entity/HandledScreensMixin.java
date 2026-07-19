
package com.minato.mixin.entity;

import com.minato.interaction.managers.inventory.InventoryManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreens.Provider.class)
public interface HandledScreensMixin {
    @Inject(method = "open", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    private void injectOpen(Text name, ScreenHandlerType<ScreenHandler> type, MinecraftClient client, int id, CallbackInfo ci) {
        if (client.player == null) return;
        InventoryManager.onSetScreenHandler(client.player.currentScreenHandler);
    }
}
