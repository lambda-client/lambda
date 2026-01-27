/*
 * Copyright 2025 Lambda
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
import com.lambda.event.events.EntityEvent;
import com.lambda.event.events.WorldEvent;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void onAddEntity(Entity entity, CallbackInfo ci) {
        if (EventFlow.post(new EntityEvent.Spawn(entity)).isCanceled()) ci.cancel();
    }

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void onRemoveEntity(int entityId, Entity.RemovalReason removalReason, CallbackInfo ci) {
        Entity entity = ((ClientWorld) (Object) this).getEntityById(entityId);
        if (entity == null) return;
        EventFlow.post(new EntityEvent.Removal(entity, removalReason));
    }

//    @ModifyReturnValue(method = "getCloudsColor", at = @At("RETURN"))
//    private int modifyGetCloudsColor(int original) {
//        if (WorldColors.INSTANCE.isEnabled() && WorldColors.getCustomClouds()) {
//            return WorldColors.getCloudColor().getRGB() & 0xFFFFFF;
//        }
//        return original;
//    }
//
//    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
//    private int modifyGetSkyColor(int original) {
//        if (WorldColors.INSTANCE.isEnabled() && WorldColors.getCustomSky()) {
//            return WorldColors.getSkyColor().getRGB() & 0xFFFFFF;
//        }
//        return original;
//    }


    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), cancellable = true)
    private void handleBlockUpdateInject(BlockPos pos, BlockState newState, int flags, CallbackInfo ci) {
        if (EventFlow.post(new WorldEvent.BlockUpdate.Server(pos, newState)).isCanceled()) ci.cancel();
    }
}
