
package com.minato.mixin.render;

import com.minato.command.CommandHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    void sendMessageInject(String chatText, boolean addToHistory, CallbackInfo ci) {
        if (!CommandHandler.INSTANCE.isMinatoCommand(chatText)) return;
        CommandHandler.INSTANCE.executeCommand(chatText);
        ci.cancel();
    }
}
