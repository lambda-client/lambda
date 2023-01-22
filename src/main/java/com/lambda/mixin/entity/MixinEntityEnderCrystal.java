package com.lambda.mixin.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityEnderCrystal.class, priority = Integer.MAX_VALUE)
public class MixinEntityEnderCrystal extends MixinEntity {
    @Inject(method = "onUpdate", at = @At("HEAD"))
    public void onUpdate(CallbackInfo ci) {
        // Update the entity so we can use the WorldEvent.EntityUpdate event
        super.onUpdate(ci);
    }
}
