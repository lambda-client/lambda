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

package com.lambda.mixin.input;

import com.lambda.module.modules.movement.Speed;
import com.lambda.module.modules.movement.Sprint;
import com.lambda.module.modules.movement.TargetStrafe;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Objects;

@Mixin(KeyBinding.class)
public class KeyBindingMixin {
    @ModifyReturnValue(method = "isPressed", at = @At("RETURN"))
    boolean modifyIsPressed(boolean original) {
        KeyBinding instance = (KeyBinding) (Object) this;
        if (!Objects.equals(instance.getId(), "key.sprint")) return original;

        if (Sprint.INSTANCE.isEnabled()) return true;
        if (Speed.INSTANCE.isEnabled() && Speed.getMode() == Speed.Mode.GrimStrafe) return true;
        if (TargetStrafe.INSTANCE.isEnabled() && TargetStrafe.isActive()) return true;
        return original;
    }
}
