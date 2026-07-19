
package com.minato.mixin.world;

import com.minato.interaction.managers.rotating.RotationManager;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static com.minato.Minato.getMc;

@Mixin(Direction.class)
public class DirectionMixin {
    @ModifyExpressionValue(method = "getEntityFacingOrder", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"))
    private static float modifyGetYaw(float original, Entity entity) {
        return entity == getMc().player ? RotationManager.getServerRotation().getYawF() : original;
    }

    @ModifyExpressionValue(method = "getEntityFacingOrder", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"))
    private static float modifyGetPitch(float original, Entity entity) {
        return entity == getMc().player ? RotationManager.getServerRotation().getPitchF() : original;
    }
}