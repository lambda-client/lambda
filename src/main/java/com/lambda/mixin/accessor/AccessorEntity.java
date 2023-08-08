package com.lambda.mixin.accessor;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface AccessorEntity {

    @Accessor("isInWeb")
    boolean getIsInWeb();

    @Invoker("setFlag")
    void invokeSetFlag(int flag, boolean set);
}
