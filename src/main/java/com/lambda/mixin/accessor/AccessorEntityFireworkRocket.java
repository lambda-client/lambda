package com.lambda.mixin.accessor;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityFireworkRocket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityFireworkRocket.class)
public interface AccessorEntityFireworkRocket {

    @Accessor("boostedEntity")
    EntityLivingBase getBoostedEntity();
}
