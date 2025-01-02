/*
 * Copyright 2024 Lambda
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
import com.lambda.module.modules.render.WorldColors;
import com.lambda.util.math.ColorKt;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void addEntity(Entity entity, CallbackInfo ci) {
        if (EventFlow.post(new EntityEvent.EntitySpawn(entity)).isCanceled()) ci.cancel();
    }

    @Inject(method = "getCloudsColor", at = @At("HEAD"), cancellable = true)
    private void getCloudsColorInject(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (WorldColors.INSTANCE.isEnabled() && WorldColors.getCustomClouds()) {
            cir.setReturnValue(ColorKt.getVec3d(WorldColors.getCloudColor()));
        }
    }

    @Inject(method = "getSkyColor", at = @At("HEAD"), cancellable = true)
    private void getSkyColorInject(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (WorldColors.INSTANCE.isEnabled() && WorldColors.getCustomSky()) {
            cir.setReturnValue(ColorKt.getVec3d(WorldColors.getSkyColor()));
        }
    }
}
