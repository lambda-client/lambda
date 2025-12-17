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

import com.lambda.module.modules.render.NoRender;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.LinkedList;
import java.util.Queue;
import java.util.stream.Collectors;

@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    // prevents the particles from being stored and potential overhead. Downside being they need to spawn back in rather than just enabling rendering again
//    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
//    private void injectAddParticle(Particle particle, CallbackInfo ci) {
//        if (NoRender.shouldOmitParticle(particle)) ci.cancel();
//    }

//    @WrapOperation(method = "renderParticles(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/render/VertexConsumerProvider$Immediate;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;renderParticles(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/particle/ParticleTextureSheet;Ljava/util/Queue;)V"))
//    private void wrapRenderParticles(Camera camera, float tickProgress, VertexConsumerProvider.Immediate vertexConsumers, ParticleTextureSheet sheet, Queue<Particle> particles, Operation<Void> original) {
//        original.call(camera, tickProgress, vertexConsumers, sheet, filterParticles(particles));
//    }
//
//    @WrapOperation(method = "renderParticles(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/render/VertexConsumerProvider$Immediate;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;renderCustomParticles(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/render/VertexConsumerProvider$Immediate;Ljava/util/Queue;)V"))
//    private void wrapRenderParticles(Camera camera, float tickProgress, VertexConsumerProvider.Immediate vertexConsumers, Queue<Particle> particles, Operation<Void> original) {
//        original.call(camera, tickProgress, vertexConsumers, filterParticles(particles));
//    }

    @Unique
    private Queue<Particle> filterParticles(Queue<Particle> particles) {
        return particles.stream().filter(particle ->
                !NoRender.shouldOmitParticle(particle)).collect(Collectors.toCollection(LinkedList::new)
        );
    }
}
