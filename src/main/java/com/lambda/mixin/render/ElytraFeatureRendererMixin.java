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

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ElytraFeatureRenderer.class)
public class ElytraFeatureRendererMixin<T extends LivingEntity> {
    @ModifyReturnValue(method = "getTexture(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)Lnet/minecraft/util/Identifier;", at = @At("TAIL"))
    private static Identifier getTexture(Identifier original, BipedEntityRenderState state) {
        // FixMe: fuck you mojang
        /*var entity = StreamSupport.stream(Lambda.getMc().world.getEntities().spliterator(), false)
                        .filter(e -> e.getDisplayName() == state.displayName)
                        .findFirst().get();

        if (!Capes.INSTANCE.isEnabled() || !CapeManager.INSTANCE.containsKey(entity.getUuid())) return original;

        return Identifier.of("lambda", CapeManager.INSTANCE.get(entity.getUuid()));*/

        return original;
    }
}
