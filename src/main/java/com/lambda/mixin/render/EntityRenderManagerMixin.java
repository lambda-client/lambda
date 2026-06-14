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

package com.lambda.mixin.render;

import com.lambda.graphics.outline.IEntityRenderState;
import com.lambda.graphics.outline.OutlineCapturingQueue;
import com.lambda.graphics.outline.OutlineHandler;
import com.lambda.graphics.outline.VertexCapture;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public class EntityRenderManagerMixin {
    @Inject(method = "getAndUpdateRenderState", at = @At("RETURN"))
    private <E extends Entity> void captureEntityId(E entity, float tickProgress, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState state = cir.getReturnValue();
        if (state instanceof IEntityRenderState lambdaState) lambdaState.lambda$setEntityId(entity.getId());
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"))
    private <S extends EntityRenderState> void wrapRenderQueue(EntityRenderer<?, S> renderer, S renderState, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, Operation<Void> original) {
        int entityId = -1;
        if (renderState instanceof IEntityRenderState lambdaState) {
            entityId = lambdaState.lambda$getEntityId();
        }

        if (entityId != -1 && OutlineHandler.shouldCapture(entityId)) {
            VertexCapture.INSTANCE.beginCapture(entityId);

            OrderedRenderCommandQueueImpl wrappedQueue = new OutlineCapturingQueue((OrderedRenderCommandQueueImpl) queue, entityId);
            original.call(renderer, renderState, matrices, wrappedQueue, cameraState);

            VertexCapture.INSTANCE.endCapture();
        } else {
            original.call(renderer, renderState, matrices, queue, cameraState);
        }
    }
}
