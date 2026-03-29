/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.mixin.world;

import com.lambda.event.EventFlow;
import com.lambda.event.events.WorldEvent;
import com.lambda.module.modules.render.LightLevels;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Consumer;

@Mixin(ClientChunkManager.class)
public class ClientChunkManagerMixin {
    @Inject(method = "loadChunkFromPacket", at = @At("TAIL"))
    private void onChunkLoad(
            int x, int z, PacketByteBuf buf, Map<Heightmap.Type, long[]> heightmaps, Consumer<ChunkData.BlockEntityVisitor> consumer, CallbackInfoReturnable<WorldChunk> cir
    ) {
        EventFlow.post(new WorldEvent.ChunkEvent.Load(cir.getReturnValue()));
    }

    @Inject(method = "loadChunkFromPacket", at = @At(value = "NEW", target = "net/minecraft/world/chunk/WorldChunk", shift = At.Shift.BEFORE))
    private void onChunkUnload(int x, int z, PacketByteBuf buf, Map<Heightmap.Type, long[]> heightmaps, Consumer<ChunkData.BlockEntityVisitor> consumer, CallbackInfoReturnable<WorldChunk> cir, @Local WorldChunk chunk) {
        if (chunk != null) {
            EventFlow.post(new WorldEvent.ChunkEvent.Unload(chunk));
        }
    }

    @Inject(method = "unload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientChunkManager$ClientChunkMap;unloadChunk(ILnet/minecraft/world/chunk/WorldChunk;)V"))
    private void onChunkUnload(ChunkPos pos, CallbackInfo ci, @Local WorldChunk chunk) {
        EventFlow.post(new WorldEvent.ChunkEvent.Unload(chunk));
    }

    @Inject(method = "onLightUpdate", at = @At("RETURN"))
    private void injectOnLightUpdate(LightType type, ChunkSectionPos pos, CallbackInfo ci) {
        if (LightLevels.INSTANCE.isEnabled()) {
            LightLevels.updateChunk(pos.getX(), pos.getZ());
        }
    }
}
