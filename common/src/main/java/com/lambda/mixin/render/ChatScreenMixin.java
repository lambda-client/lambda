package com.lambda.mixin.render;

import com.lambda.command.CommandManager;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    void sendMessageInject(String chatText, boolean addToHistory, CallbackInfoReturnable<Boolean> cir) {
        if (!CommandManager.INSTANCE.isLambdaCommand(chatText)) return;
        CommandManager.INSTANCE.executeCommand(chatText);

        cir.setReturnValue(true);
    }
}
