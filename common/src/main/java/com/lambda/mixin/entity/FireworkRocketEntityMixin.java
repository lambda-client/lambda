package com.lambda.mixin.entity;

import com.lambda.module.Module;
import com.lambda.module.modules.movement.ElytraFly;
import com.lambda.util.Nameable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;

@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityMixin {


    @Shadow @Nullable public LivingEntity shooter;

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"
            )
    )
    private void redirectSetVelocity(LivingEntity instance, Vec3d vec3d) {

        if (ElytraFly.INSTANCE.isEnabled() && ElytraFly.INSTANCE.getMode().equals(ElytraFly.Mode.ROCKET_BOOST)) {

            double speedMultiplier = ElytraFly.INSTANCE.getRocketSpeed();

            assert shooter != null;
            Vec3d rotationVector = shooter.getRotationVector();
            Vec3d currentVelocity = shooter.getVelocity();

            double d = 1.5 * speedMultiplier;
            double e = 0.1 * speedMultiplier;

            Vec3d newVelocity = currentVelocity.add(
                    rotationVector.x * e + (rotationVector.x * d - currentVelocity.x) * 0.5,
                    rotationVector.y * e + (rotationVector.y * d - currentVelocity.y) * 0.5,
                    rotationVector.z * e + (rotationVector.z * d - currentVelocity.z) * 0.5
            );

            this.shooter.setVelocity(newVelocity);
        }
    }
}
