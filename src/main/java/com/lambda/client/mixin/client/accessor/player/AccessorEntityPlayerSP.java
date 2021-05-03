package com.lambda.client.mixin.client.accessor.player;

import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityPlayerSP.class)
public interface AccessorEntityPlayerSP {

    @Accessor("handActive")
    void kbSetHandActive(boolean value);

    @Accessor("horseJumpPower")
    void horseJumpPower(float value);
}
