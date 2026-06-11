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

package com.lambda.mixin.network;

import com.lambda.module.modules.combat.AutoDisconnect;
import com.lambda.module.modules.combat.autodisconnect.AutoDisconnectScreen;
import com.lambda.module.modules.combat.DisconnectDetails;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.DisconnectionInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientCommonNetworkHandler.class)
public class ClientCommonNetworkHandlerMixin {
    @Inject(method = "createDisconnectedScreen", at = @At("HEAD"), cancellable = true)
    private void createDisconnectedScreen(DisconnectionInfo info, CallbackInfoReturnable<Screen> cir) {
        DisconnectDetails details = AutoDisconnect.INSTANCE.consumeDetails();
        if (details != null) {
            cir.setReturnValue(new AutoDisconnectScreen(details));
        }
    }
}
