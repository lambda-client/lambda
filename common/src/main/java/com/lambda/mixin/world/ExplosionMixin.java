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

package com.lambda.mixin.world;

import com.lambda.module.modules.render.NoRender;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public class ExplosionMixin {
    /**
     * Cancels the method if {@link NoRender#getNoExplosion()} is true
     * <pre>{@code
     * if (particles) {
     *     ParticleEffect particleEffect;
     *     if (!(this.power < 2.0F) && bl) {
     *         particleEffect = this.emitterParticle;
     *     } else {
     *         particleEffect = this.particle;
     *     }
     *
     *     this.world.addParticle(particleEffect, this.x, this.y, this.z, 1.0, 0.0, 0.0);
     * }
     * }</pre>
     */
    @Inject(method = "affectWorld(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"), cancellable = true)
    void injectParticles(boolean particles, CallbackInfo ci) {
        if (NoRender.getNoExplosion()) ci.cancel();
    }
}
