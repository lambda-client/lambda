package com.lambda.mixin.network;

import com.lambda.client.event.LambdaEventBus;
import com.lambda.client.event.events.ChunkDataEvent;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {

    @Shadow private WorldClient world;

    @Inject(method = "handleChunkData", at = @At("TAIL"))
    public void handleChunkData(SPacketChunkData packetIn, CallbackInfo ci) {
        LambdaEventBus.INSTANCE.post(new ChunkDataEvent(packetIn.isFullChunk(), this.world.getChunk(packetIn.getChunkX(), packetIn.getChunkZ())));
    }
}
