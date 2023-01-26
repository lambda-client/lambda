package com.lambda.mixin.accessor.network;

import net.minecraft.network.play.server.SPacketEntityHeadLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SPacketEntityHeadLook.class)
public interface AccessorSPacketEntityHeadLook {

    @Accessor("entityId")
    int getEntityId();

    @Accessor("entityId")
    void setEntityId(int value);

}
