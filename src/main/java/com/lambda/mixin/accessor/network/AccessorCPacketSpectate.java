package com.lambda.mixin.accessor.network;

import net.minecraft.network.play.client.CPacketSpectate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(value = CPacketSpectate.class)
public interface AccessorCPacketSpectate {
    @Accessor(value = "id")
    UUID getId();
}
