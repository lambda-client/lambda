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

package com.lambda.mixin.render.particle;

import com.lambda.module.modules.render.NoRender;
import net.minecraft.client.particle.ItemPickupParticle;
import net.minecraft.client.particle.ItemPickupParticleRenderer;
import net.minecraft.client.particle.NoRenderParticleRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.Submittable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemPickupParticleRenderer.class)
public class ItemPickupParticleRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void injectRender(Frustum frustum, Camera camera, float tickProgress, CallbackInfoReturnable<Submittable> cir) {
        if (NoRender.shouldOmitParticle(ItemPickupParticle.class)) cir.setReturnValue(NoRenderParticleRenderer.EMPTY);
    }
}
