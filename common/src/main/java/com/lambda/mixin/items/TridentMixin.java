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

package com.lambda.mixin.items;

import com.lambda.module.modules.movement.TridentBoost;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(TridentItem.class)
public class TridentMixin {
    // Forge doesn't support the @ModityArgs annotation, so we have to chain multiple @ModifyArg
    @ModifyArg(method = "onStoppedUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addVelocity(DDD)V"), index = 0)
    private double modifyVelocity0(double velocity) {
        return TridentBoost.INSTANCE.isEnabled() ? velocity * TridentBoost.getTridentSpeed() : velocity;
    }

    @ModifyArg(method = "onStoppedUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addVelocity(DDD)V"), index = 1)
    private double modifyVelocity1(double velocity) {
        return TridentBoost.INSTANCE.isEnabled() ? velocity * TridentBoost.getTridentSpeed() : velocity;
    }

    @ModifyArg(method = "onStoppedUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addVelocity(DDD)V"), index = 2)
    private double modifyVelocity2(double velocity) {
        return TridentBoost.INSTANCE.isEnabled() ? velocity * TridentBoost.getTridentSpeed() : velocity;
    }

    @ModifyExpressionValue(method = {"onStoppedUsing", "use"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isTouchingWaterOrRain()Z"))
    private boolean modifyIsTouchingWaterOrRain(boolean original) {
        return TridentBoost.INSTANCE.isEnabled() && TridentBoost.getForceUse() || original;
    }
}
