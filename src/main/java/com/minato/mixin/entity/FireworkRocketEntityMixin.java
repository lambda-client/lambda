
package com.minato.mixin.entity;

import com.minato.module.modules.movement.elytrafly.ElytraFly;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"))
    private void wrapSetVelocity(LivingEntity shooter, Vec3d vec3d, Operation<Void> original) {
        if (ElytraFly.INSTANCE.isEnabled()) ElytraFly.boostRocket();
        else original.call(shooter, vec3d);
    }
}
