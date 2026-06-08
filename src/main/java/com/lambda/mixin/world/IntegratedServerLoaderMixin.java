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

package com.lambda.mixin.world;

import com.lambda.module.modules.combat.AutoDisconnect;
import com.lambda.module.modules.combat.SingleplayerReconnectTarget;
import net.minecraft.server.integrated.IntegratedServerLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServerLoader.class)
public class IntegratedServerLoaderMixin {
    @Inject(method = "start(Ljava/lang/String;Ljava/lang/Runnable;)V", at = @At("HEAD"))
    private void onStart(String name, Runnable onCancel, CallbackInfo ci) {
        AutoDisconnect.INSTANCE.setLastReconnectTarget(new SingleplayerReconnectTarget(name));
    }

}
