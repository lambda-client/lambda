package com.lambda.mixin.items;

import com.lambda.module.modules.movement.TridentBoost;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(TridentItem.class)
public class TridentMixin {
    // Forge doesn't support the @ModityArgs annotation, so we have to chain multiple @ModifyArg
    @ModifyArg(method = "onStoppedUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addVelocity(DDD)V"), index = 0)
    private double modifyVelocity0(double velocity) { return TridentBoost.INSTANCE.isEnabled() ? velocity * TridentBoost.INSTANCE.getTridentSpeed() : velocity; }

    @ModifyArg(method = "onStoppedUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addVelocity(DDD)V"), index = 1)
    private double modifyVelocity1(double velocity) { return TridentBoost.INSTANCE.isEnabled() ? velocity * TridentBoost.INSTANCE.getTridentSpeed() : velocity; }

    @ModifyArg(method = "onStoppedUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addVelocity(DDD)V"), index = 2)
    private double modifyVelocity2(double velocity) { return TridentBoost.INSTANCE.isEnabled() ? velocity * TridentBoost.INSTANCE.getTridentSpeed() : velocity; }

    @ModifyExpressionValue(method = {"onStoppedUsing", "use"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isTouchingWaterOrRain()Z"))
    private boolean modifyIsTouchingWaterOrRain(boolean original) { return TridentBoost.INSTANCE.isEnabled() ? TridentBoost.INSTANCE.getForceUse() : original; }
}
