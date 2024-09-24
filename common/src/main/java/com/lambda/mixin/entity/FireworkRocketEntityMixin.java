package com.lambda.mixin.entity;

import com.lambda.module.modules.movement.ElytraFly;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityMixin {
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"
            )
    )
    private void redirectSetVelocity(LivingEntity shooter, Vec3d vec3d) {
        if (!ElytraFly.getDoBoost()) {
            shooter.setVelocity(vec3d.add(
                    vec3d.x * 0.1 + (vec3d.x * 1.5 - vec3d.x) * 0.5,
                    vec3d.y * 0.1 + (vec3d.y * 1.5 - vec3d.y) * 0.5,
                    vec3d.z * 0.1 + (vec3d.z * 1.5 - vec3d.z) * 0.5
            ));
            return;
        }
        ElytraFly.boostRocket(shooter);
    }
}
