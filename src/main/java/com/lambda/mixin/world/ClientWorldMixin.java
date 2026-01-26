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
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @WrapMethod(method = "addEntity")
    private void onAddEntity(Entity entity, Operation<Void> original) {
        if (!EventFlow.post(new EntityEvent.Spawn(entity)).isCanceled())
            original.call(entity);
    }

    @WrapMethod(method = "removeEntity")
    private void onRemoveEntity(int entityId, Entity.RemovalReason removalReason, Operation<Void> original) {
        Entity entity = ((ClientWorld) (Object) this).getEntityById(entityId);
        if (entity == null) {
            original.call(entityId, removalReason);
            return;
        }

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


    @WrapMethod(method = "handleBlockUpdate")
    private void handleBlockUpdateInject(BlockPos pos, BlockState state, int flags, Operation<Void> original) {
        if (!EventFlow.post(new WorldEvent.BlockUpdate.Server(pos, state)).isCanceled())
            original.call(pos, state, flags);
    }
}
