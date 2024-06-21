package com.lambda.mixin.render;

import com.lambda.command.CommandManager;
import com.lambda.graphics.renderer.gui.font.FontRenderer;
import com.lambda.graphics.renderer.gui.font.LambdaEmoji;
import com.lambda.graphics.renderer.gui.font.glyph.GlyphInfo;
import kotlin.Pair;
import kotlin.ranges.IntRange;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @ModifyArg(method = "sendMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendChatMessage(Ljava/lang/String;)V"), index = 0)
    private String modifyChatText(String chatText) {
        List<Pair<GlyphInfo, IntRange>> emojis = FontRenderer.Companion.parseEmojis(chatText, LambdaEmoji.Twemoji);
        Collections.reverse(emojis);

        for (Pair<GlyphInfo, IntRange> emoji : emojis) {
            String emojiString = chatText.substring(emoji.getSecond().getStart() + 1, emoji.getSecond().getEndInclusive());
            if (LambdaEmoji.Twemoji.get(emojiString) == null)
                continue;

            // Because the width of a char is bigger than an emoji
            // we can simply replace the matches string by a space
            // and render it after the text
            chatText = chatText.substring(0, emoji.getSecond().getStart()) + " " + chatText.substring(emoji.getSecond().getEndInclusive() + 1);

            // TODO: Build a renderer for the emojis
            // TODO: Render the emojis at their correct position
        }

        return chatText;
    }

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    void sendMessageInject(String chatText, boolean addToHistory, CallbackInfoReturnable<Boolean> cir) {
        if (!CommandManager.INSTANCE.isLambdaCommand(chatText)) return;
        CommandManager.INSTANCE.executeCommand(chatText);

        cir.setReturnValue(true);
    }
}
