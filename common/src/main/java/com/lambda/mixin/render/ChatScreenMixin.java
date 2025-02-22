/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.mixin.render;

import com.lambda.command.CommandManager;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    /**
     * Intercepts the chat message sending process to execute Lambda commands.
     *
     * <p>If the chat text is recognized as a Lambda command, the command is executed via the CommandManager,
     * and the default message sending is canceled by setting the callback's return value to true.</p>
     *
     * @param chatText the text of the chat message being processed
     * @param addToHistory flag indicating whether the message should be added to the chat history (not used for Lambda commands)
     * @param cir callback used to override the normal sending behavior of the chat message
     */
    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    void sendMessageInject(String chatText, boolean addToHistory, CallbackInfoReturnable<Boolean> cir) {
        if (!CommandManager.INSTANCE.isLambdaCommand(chatText)) return;
        CommandManager.INSTANCE.executeCommand(chatText);

        cir.setReturnValue(true);
    }
}
