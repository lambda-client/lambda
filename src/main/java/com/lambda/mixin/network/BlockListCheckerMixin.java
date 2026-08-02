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

import net.minecraft.client.network.Address;
import net.minecraft.client.network.BlockListChecker;
import net.minecraft.client.network.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockListChecker.class)
public interface BlockListCheckerMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void onCreate(CallbackInfoReturnable<BlockListChecker> cir) {
        cir.setReturnValue(new BlockListChecker() {
            @Override
            public boolean isAllowed(Address address) {
                return true;
            }

            @Override
            public boolean isAllowed(ServerAddress address) {
                return true;
            }
        });
    }
}
