
package com.minato.mixin.render;

import com.minato.command.CommandHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {
    @Shadow @Final TextFieldWidget textField;

    @Shadow
    public abstract void show(boolean narrateFirstSuggestion);

    @ModifyVariable(method = "refresh", at = @At(value = "STORE"), index = 3)
    private boolean refreshModify(boolean showCompletions) {
        return CommandHandler.INSTANCE.isCommand(textField.getText());
    }

    @SuppressWarnings("unchecked")
    @WrapOperation(method = "refresh", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;getCommandDispatcher()Lcom/mojang/brigadier/CommandDispatcher;"))
    private CommandDispatcher<CommandSource> wrapRefresh(ClientPlayNetworkHandler instance, Operation<CommandDispatcher<CommandSource>> original) {
        return (CommandDispatcher<CommandSource>) CommandHandler.INSTANCE.currentDispatcher(textField.getText());
    }
}
