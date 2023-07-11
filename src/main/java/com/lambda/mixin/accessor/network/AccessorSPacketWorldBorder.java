package com.lambda.mixin.accessor.network;

import net.minecraft.network.play.server.SPacketWorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SPacketWorldBorder.class)
public interface AccessorSPacketWorldBorder {
    @Accessor(value = "action")
    SPacketWorldBorder.Action getAction();
    @Accessor(value = "size")
    int getSize();
    @Accessor(value = "centerX")
    double getCenterX();
    @Accessor(value = "centerZ")
    double getCenterZ();
    @Accessor(value = "targetSize")
    double getTargetSize();
    @Accessor(value = "diameter")
    double getDiameter();
    @Accessor(value = "timeUntilTarget")
    long getTimeUntilTarget();
    @Accessor(value = "warningTime")
    int getWarningTime();
    @Accessor(value = "warningDistance")
    int getWarningDistance();
}
