package com.lambda.mixin.world;

import com.lambda.event.EventFlow;
import com.lambda.event.events.WorldEvent;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.function.Consumer;

@Mixin(ClientChunkManager.class)
public class ClientChunkManagerMixin {
    @Final
    @Shadow
    ClientWorld world;

    @Inject(method = "loadChunkFromPacket", at = @At("TAIL"))
    private void onChunkLoad(
            int x,
            int z,
            PacketByteBuf packetByteBuf,
            NbtCompound nbtCompound,
            Consumer<ChunkData.BlockEntityVisitor> consumer,
            CallbackInfoReturnable<WorldChunk> info
    ) {
        EventFlow.post(new WorldEvent.ChunkEvent.Load(this.world, info.getReturnValue()));
    }

    @Inject(method = "loadChunkFromPacket", at = @At(value = "NEW", target = "net/minecraft/world/chunk/WorldChunk", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD)
    private void onChunkUnload(
            int x,
            int z,
            PacketByteBuf buf,
            NbtCompound tag,
            Consumer<ChunkData.BlockEntityVisitor> consumer,
            CallbackInfoReturnable<WorldChunk> info,
            int index,
            WorldChunk chunk,
            ChunkPos chunkPos
    ) {
        if (chunk != null) {
            EventFlow.post(new WorldEvent.ChunkEvent.Unload(this.world, chunk));
        }
    }

    @Inject(method = "unload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientChunkManager$ClientChunkMap;compareAndSet(ILnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/chunk/WorldChunk;)Lnet/minecraft/world/chunk/WorldChunk;"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void onChunkUnload(ChunkPos pos, CallbackInfo ci, int i, WorldChunk chunk) {
        EventFlow.post(new WorldEvent.ChunkEvent.Unload(this.world, chunk));
    }

//    @Inject(
//            method = "updateLoadDistance",
//            at = @At(
//                    value = "INVOKE",
//                    target = "net/minecraft/client/world/ClientChunkManager$ClientChunkMap.isInRadius(II)Z"
//            ),
//            locals = LocalCapture.CAPTURE_FAILHARD
//    )
//    private void onUpdateLoadDistance(
//            int loadDistance,
//            CallbackInfo ci,
//            int oldRadius,
//            int newRadius,
//            ClientChunkManager.ClientChunkMap clientChunkMap,
//            int k,
//            WorldChunk oldChunk,
//            ChunkPos chunkPos
//    ) {
//        if (!clientChunkMap.isInRadius(chunkPos.x, chunkPos.z)) {
//            EventFlow.post(new WorldEvent.ChunkEvent.Unload(this.world, oldChunk));
//        }
//    }
}
