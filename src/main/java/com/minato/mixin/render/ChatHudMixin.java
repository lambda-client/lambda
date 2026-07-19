
package com.minato.mixin.render;

import com.minato.event.EventFlow;
import com.minato.event.events.ChatEvent;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChatHud.class)
public class ChatHudMixin {
    @WrapMethod(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V")
    void wrapAddMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, Operation<Void> original) {
        var event = new ChatEvent.Receive(message, signatureData, indicator);

        if (!EventFlow.post(event).isCanceled())
            original.call(event.getMessage(), event.getSignature(), event.getIndicator());
    }
}
