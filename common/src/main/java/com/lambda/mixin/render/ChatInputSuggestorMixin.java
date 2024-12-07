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

import com.google.common.base.Strings;
import com.lambda.command.CommandManager;
import com.lambda.graphics.renderer.gui.font.LambdaAtlas;
import com.lambda.module.modules.client.LambdaMoji;
import com.lambda.module.modules.client.RenderSettings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.command.CommandSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {

    @Shadow
    @Final
    TextFieldWidget textField;

    @Shadow
    private @Nullable CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    public abstract void show(boolean narrateFirstSuggestion);

    @ModifyVariable(method = "refresh", at = @At(value = "STORE"), index = 3)
    private boolean refreshModify(boolean showCompletions) {
        return CommandManager.INSTANCE.isCommand(textField.getText());
    }

    @Redirect(method = "refresh", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;getCommandDispatcher()Lcom/mojang/brigadier/CommandDispatcher;"))
    private CommandDispatcher<CommandSource> refreshRedirect(ClientPlayNetworkHandler instance) {
        return CommandManager.INSTANCE.currentDispatcher(textField.getText());
    }

    @Inject(method = "refresh", at = @At("TAIL"))
    private void refreshEmojiSuggestion(CallbackInfo ci) {
        if (!LambdaMoji.INSTANCE.isEnabled() ||
                !LambdaMoji.INSTANCE.getSuggestions()) return;

        String typing = textField.getText();

        // Don't suggest emojis in commands
        if (CommandManager.INSTANCE.isCommand(typing) ||
                CommandManager.INSTANCE.isLambdaCommand(typing)) return;

        int cursor = textField.getCursor();
        String textToCursor = typing.substring(0, cursor);
        if (textToCursor.isEmpty()) return;

        // Most right index at the left of the regex expression
        int start = neoLambda$getLastColon(textToCursor);
        if (start == -1) return;

        String emojiString = typing.substring(start + 1);

        Stream<String> results = LambdaAtlas.INSTANCE.getKeys(RenderSettings.INSTANCE.getEmojiFont())
                .keySet().stream()
                .filter(s -> s.startsWith(emojiString))
                .map(s -> s + ":");

        pendingSuggestions = CommandSource.suggestMatching(results, new SuggestionsBuilder(textToCursor, start + 1));
        pendingSuggestions.thenRun(() -> {
            if (!pendingSuggestions.isDone()) return;

            show(false);
        });
    }

    @Unique
    private static final Pattern COLON_PATTERN = Pattern.compile("(:[a-zA-Z0-9_]+)");

    @Unique
    private int neoLambda$getLastColon(String input) {
        if (Strings.isNullOrEmpty(input)) return -1;

        int i = -1;
        Matcher matcher = COLON_PATTERN.matcher(input);

        while (matcher.find()) {
            i = matcher.start();
        }

        return i;
    }
}
