package com.lambda.mixin.accessor.network;

import net.minecraft.network.play.client.CPacketClientSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CPacketClientSettings.class)
public interface AccessorCPacketClientSettings {
    @Accessor(value = "view")
    int getView();

}
