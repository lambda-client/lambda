/*
 * Copyright 2026 Lambda
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

import com.lambda.interaction.handler.handlers.FriendHandler;
import com.lambda.module.modules.render.ExtraTab;
import com.lambda.util.text.TextBuilder;
import com.lambda.util.text.TextDslKt;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import kotlin.Unit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Nullables;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.List;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
    @Shadow @Final private static Comparator<PlayerListEntry> ENTRY_ORDERING;
    @Unique private static final Comparator<PlayerListEntry> FRIENDS_FIRST_ENTRY_ORDERING = Comparator
            .comparingInt((PlayerListEntry entry) -> FriendHandler.INSTANCE.isFriend(entry.getProfile().name()) ? 0 : 1)
            .thenComparingInt(entry -> -entry.getListOrder())
            .thenComparingInt((entry) -> entry.getGameMode() == GameMode.SPECTATOR ? 1 : 0)
            .thenComparing((entry) -> Nullables.mapOrElse(entry.getScoreboardTeam(), team -> team != null ? team.getName() : "", ""))
            .thenComparing((entry) -> entry.getProfile().name(), String::compareToIgnoreCase);

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "collectPlayerEntries", at = @At(value = "HEAD"), cancellable = true)
    private void onCollectPlayerEntriesHead(CallbackInfoReturnable<List<PlayerListEntry>> cir) {
        if (ExtraTab.INSTANCE.isDisabled()) return;
        if (client.player == null) return;
        cir.setReturnValue(
                client.player.networkHandler
                        .getListedPlayerListEntries()
                        .stream()
                        .filter(entry -> !ExtraTab.getFriendsOnly() || FriendHandler.INSTANCE.isFriend(entry.getProfile()))
                        .sorted(ExtraTab.getSortFriendsFirst() ? FRIENDS_FIRST_ENTRY_ORDERING : ENTRY_ORDERING)
                        .limit(ExtraTab.getTabEntries())
                        .toList()
        );
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 20))
    private int modifyRowLimit(int original) {
        return ExtraTab.INSTANCE.isEnabled() ? ExtraTab.getRows() : original;
    }

    @ModifyExpressionValue(method = "getPlayerName", at = @At(value = "INVOKE", target = "Lnet/minecraft/text/Text;copy()Lnet/minecraft/text/MutableText;"))
    private MutableText modifyPlayerName(MutableText original) { return modifyName(original); }

    @ModifyExpressionValue(method = "getPlayerName", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/Team;decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;"))
    private MutableText modifyDecorateName(MutableText original) { return modifyName(original); }

    @Unique
    private @Nullable MutableText modifyName(Text original) {
        if (ExtraTab.INSTANCE.isDisabled() ||
                !ExtraTab.getHighlightFriends() ||
                !FriendHandler.INSTANCE.isFriend(original.getString())) return original.copy();
        var newText = original.copy();
        var textBuilder = new TextBuilder();
        TextDslKt.color(textBuilder, ExtraTab.getFriendColor(), builder -> {
            builder.styleAndAppend(newText);
            return Unit.INSTANCE;
        });
        return textBuilder.getText();
    }
}
