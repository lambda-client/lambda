package com.lambda.mixin.accessor.network;

import net.minecraft.network.play.server.SPacketCloseWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SPacketCloseWindow.class)
public interface AccessorSPacketCloseWindow {
    @Accessor(value = "windowId")
    int getWindowId();
}
