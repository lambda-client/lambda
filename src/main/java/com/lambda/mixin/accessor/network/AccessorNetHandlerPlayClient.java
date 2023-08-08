package com.lambda.mixin.accessor.network;

import net.minecraft.client.network.NetHandlerPlayClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = NetHandlerPlayClient.class)
public interface AccessorNetHandlerPlayClient {

    @Accessor(value = "doneLoadingTerrain")
    boolean isDoneLoadingTerrain();

    @Accessor(value = "doneLoadingTerrain")
    void setDoneLoadingTerrain(boolean loaded);
}
