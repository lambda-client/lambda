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

import com.lambda.module.modules.player.Freecam;
import com.lambda.module.modules.render.CameraTweaks;
import com.lambda.module.modules.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @WrapMethod(method = "hasBlindnessOrDarkness(Lnet/minecraft/client/render/Camera;)Z")
    private boolean modifyEffectCheck(Camera camera, Operation<Boolean> original) {
        Entity entity = camera.getFocusedEntity();
        if (entity instanceof LivingEntity livingEntity && NoRender.INSTANCE.isEnabled()) {
            boolean blind = livingEntity.hasStatusEffect(StatusEffects.BLINDNESS) && !NoRender.getNoBlindness();
            boolean dark = livingEntity.hasStatusEffect(StatusEffects.DARKNESS) && !NoRender.getNoDarkness();
            return blind || dark;
        }

        return original.call(camera);
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;updateCamera(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Z)V"), index = 2)
    private boolean renderSetupTerrainModifyArg(boolean spectator) {
        return Freecam.INSTANCE.isEnabled() || CameraTweaks.INSTANCE.isEnabled() || spectator;
    }

    @ModifyExpressionValue(method = "fillEntityRenderStates", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;isThirdPerson()Z"))
    private boolean modifyIsThirdPerson(boolean original) {
        return Freecam.INSTANCE.isEnabled() || original;
    }
}
