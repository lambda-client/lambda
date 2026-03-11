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
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.BillboardParticleSubmittable;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BillboardParticle.class)
public class BillboardParticleMixin {
    @Inject(method = "render(Lnet/minecraft/client/particle/BillboardParticleSubmittable;Lnet/minecraft/client/render/Camera;F)V", at = @At("HEAD"), cancellable = true)
    private void injectRender(BillboardParticleSubmittable submittable, Camera camera, float tickDelta, CallbackInfo ci) {
        if (NoRender.shouldOmitParticle((Particle) ((Object) this))) ci.cancel();
    }
}
