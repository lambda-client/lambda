package com.lambda.mixin.accessor.network;

import net.minecraft.network.play.client.CPacketConfirmTransaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CPacketConfirmTransaction.class)
public interface AccessorCPacketConfirmTransaction {
    @Accessor(value = "accepted")
    boolean getAccepted();
}
