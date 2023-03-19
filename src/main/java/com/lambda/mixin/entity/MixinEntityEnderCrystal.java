package com.lambda.mixin.entity;

import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityEnderCrystal.class)
public abstract class MixinEntityEnderCrystal {
    @Shadow
    protected abstract void onCrystalDestroyed(DamageSource source);

    @Inject(method = "attackEntityFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;createExplosion(Lnet/minecraft/entity/Entity;DDDFZ)Lnet/minecraft/world/Explosion;"), cancellable = true)
    private void onAttackEntityFrom(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callbackInfo) {
        EntityEnderCrystal entity = (EntityEnderCrystal) (Object) this;
        if (!source.isExplosion()) {
            entity.world.createExplosion(entity, entity.posX, entity.posY, entity.posZ, 6.0F, true);
            this.onCrystalDestroyed(source);
            callbackInfo.setReturnValue(true);
        }
    }
}
