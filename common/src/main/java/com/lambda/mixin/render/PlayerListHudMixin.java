/*
 * Copyright 2025 Lambda
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

import com.lambda.module.modules.render.ExtraTab;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.List;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
    @Shadow @Final private static Comparator<PlayerListEntry> ENTRY_ORDERING;

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "collectPlayerEntries", at = @At(value = "HEAD"), cancellable = true)
    private void onCollectPlayerEntriesHead(CallbackInfoReturnable<List<PlayerListEntry>> cir) {
        if (ExtraTab.INSTANCE.isDisabled()) return;
        if (client.player == null) return;
        cir.setReturnValue(
                client.player.networkHandler
                        .getListedPlayerListEntries()
                        .stream()
                        .sorted(ENTRY_ORDERING)
                        .limit(ExtraTab.getTabEntries())
                        .toList()
        );
    }
}
