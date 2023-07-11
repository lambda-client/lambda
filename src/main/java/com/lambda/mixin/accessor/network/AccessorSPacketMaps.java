package com.lambda.mixin.accessor.network;

import net.minecraft.network.play.server.SPacketMaps;
import net.minecraft.world.storage.MapDecoration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SPacketMaps.class)
public interface AccessorSPacketMaps {
    @Accessor(value = "mapScale")
    byte getMapScale();
    @Accessor(value = "trackingPosition")
    boolean getTrackingPosition();
    @Accessor(value = "icons")
    MapDecoration[] getIcons();
    @Accessor(value = "minX")
    int getMinX();
    @Accessor(value = "minZ")
    int getMinZ();
    @Accessor(value = "columns")
    int getColumns();
    @Accessor(value = "rows")
    int getRows();
    @Accessor(value = "mapDataBytes")
    byte[] getMapDataBytes();
}
