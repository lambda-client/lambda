package com.lambda.mixin.accessor.network;

import net.minecraft.network.play.client.CPacketVehicleMove;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CPacketVehicleMove.class)
public interface AccessorCPacketVehicleMove {
    @Accessor(value = "x")
    void setX(double x);

    @Accessor(value = "y")
    void setY(double y);

    @Accessor(value = "z")
    void setZ(double z);

    @Accessor(value = "yaw")
    void setYaw(float yaw);

    @Accessor(value = "pitch")
    void setPitch(float pitch);
}
