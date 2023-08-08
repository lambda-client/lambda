package com.lambda.mixin.accessor.network;

import net.minecraft.network.play.client.CPacketResourcePackStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CPacketResourcePackStatus.class)
public interface AccessorCPacketResourcePackStatus {
    @Accessor(value = "action")
    CPacketResourcePackStatus.Action getAction();
}
