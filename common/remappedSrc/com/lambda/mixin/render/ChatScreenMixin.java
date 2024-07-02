package com.lambda.mixin.render;

import com.lambda.Lambda;
import com.lambda.command.CommandManager;
import com.lambda.graphics.renderer.gui.font.FontRenderer;
import com.lambda.graphics.renderer.gui.font.LambdaEmoji;
import com.lambda.graphics.renderer.gui.font.glyph.GlyphInfo;
import com.lambda.module.modules.client.LambdaMoji;
import com.lambda.util.math.Vec2d;
import kotlin.Pair;
import kotlin.ranges.IntRange;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @ModifyArg(method = "sendMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendChatMessage(Ljava/lang/String;)V"), index = 0)
    private String modifyChatText(String chatText) {
        if (LambdaMoji.INSTANCE.isDisabled()) return chatText;

        List<Pair<GlyphInfo, IntRange>> emojis = FontRenderer.Companion.parseEmojis(chatText, LambdaEmoji.Twemoji);
        Collections.reverse(emojis);

        List<String> pushEmojis = new ArrayList<>();
        List<Vec2d> pushPositions = new ArrayList<>();

        for (Pair<GlyphInfo, IntRange> emoji : emojis) {
            String emojiString = chatText.substring(emoji.getSecond().getStart() + 1, emoji.getSecond().getEndInclusive());
            if (LambdaEmoji.Twemoji.get(emojiString) == null)
                continue;

            // Because the width of a char is bigger than an emoji
            // we can simply replace the matches string by a space
            // and render it after the text
            chatText = chatText.substring(0, emoji.getSecond().getStart()) + " " + chatText.substring(emoji.getSecond().getEndInclusive() + 1);

            // We cannot retain the position in the future, but we can
            // assume that every time you send a message the height of
            // the position will change by the height of the glyph
            // The positions are from the top left corner of the screen
            int x = Lambda.getMc().textRenderer.getWidth(chatText.substring(0, emoji.getSecond().getStart()));
            int y = Lambda.getMc().textRenderer.fontHeight;

            pushEmojis.add(String.format(":%s:", emojiString));
            pushPositions.add(new Vec2d(x, y));
        }

        // Not optimal because it has to parse the emoji again but who cares
        LambdaMoji.INSTANCE.add(pushEmojis, pushPositions);

        return chatText;
    }

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    void sendMessageInject(String chatText, boolean addToHistory, CallbackInfoReturnable<Boolean> cir) {
        if (!CommandManager.INSTANCE.isLambdaCommand(chatText)) return;
        CommandManager.INSTANCE.executeCommand(chatText);

        cir.setReturnValue(true);
    }
}
