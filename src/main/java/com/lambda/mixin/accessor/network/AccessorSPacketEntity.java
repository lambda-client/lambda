package com.lambda.mixin.accessor.network;

import net.minecraft.network.play.server.SPacketEntity;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SPacketEntity.class)
public interface AccessorSPacketEntity {

    @Accessor("entityId")
    int getEntityId();

    @Accessor("entityId")
    void setEntityId(int value);

}
