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
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @ModifyExpressionValue(method = "getEntitiesToRender(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Ljava/util/List;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;isThirdPerson()Z"))
    private boolean renderIsThirdPerson(boolean original) {
        return Freecam.INSTANCE.isEnabled() || original;
    }

    @ModifyArg(method = "render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V"), index = 3)
    private boolean renderSetupTerrainModifyArg(boolean hasForcedFrustum) {
        return Freecam.INSTANCE.isEnabled() || CameraTweaks.INSTANCE.isEnabled() || hasForcedFrustum;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/BackgroundRenderer;applyFog(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/BackgroundRenderer$FogType;Lorg/joml/Vector4f;FZF)Lnet/minecraft/client/render/Fog;", ordinal = 0), index = 3)
    private float modifyApplyFogRenderDistance(float viewDistance) {
        return NoRender.INSTANCE.isEnabled() && NoRender.getNoTerrainFog()
                ? Float.MAX_VALUE
                : viewDistance;
    }

    @Inject(method = "hasBlindnessOrDarkness(Lnet/minecraft/client/render/Camera;)Z", at = @At(value = "HEAD"), cancellable = true)
    private void modifyEffectCheck(Camera camera, CallbackInfoReturnable<Boolean> cir) {
        Entity entity = camera.getFocusedEntity();
        if (entity instanceof LivingEntity livingEntity && NoRender.INSTANCE.isEnabled()) {
            boolean blind = livingEntity.hasStatusEffect(StatusEffects.BLINDNESS) && !NoRender.getNoBlindness();
            boolean dark = livingEntity.hasStatusEffect(StatusEffects.DARKNESS) && !NoRender.getNoDarkness();
            cir.setReturnValue(blind || dark);
        }
    }
}
