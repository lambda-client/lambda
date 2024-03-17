package com.lambda.mixin;

import com.lambda.command.CommandManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.command.CommandSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChatInputSuggestor.class)
public class ChatInputSuggestorMixin {

    @Shadow
    @Final
    TextFieldWidget textField;

    @ModifyVariable(method = "refresh", at = @At(value = "STORE"), index = 3)
    private boolean refreshModify(boolean showCompletions) {
        return CommandManager.INSTANCE.isCommand(textField.getText());
    }

    @Redirect(method = "refresh", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;getCommandDispatcher()Lcom/mojang/brigadier/CommandDispatcher;"))
    private CommandDispatcher<CommandSource> refreshRedirect(ClientPlayNetworkHandler instance) {
        return CommandManager.INSTANCE.currentDispatcher(textField.getText());
    }
}
