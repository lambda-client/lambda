package com.lambda.mixin.accessor.network;

import net.minecraft.network.play.client.CPacketPlayerAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CPacketPlayerAbilities.class)
public interface AccessorCPacketPlayerAbilities {
    @Accessor(value = "flySpeed")
    float getFlySpeed();
    @Accessor(value = "walkSpeed")
    float getWalkSpeed();
}
