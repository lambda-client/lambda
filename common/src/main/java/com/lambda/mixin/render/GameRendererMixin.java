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

package com.lambda.mixin.render;

import com.lambda.event.EventFlow;
import com.lambda.event.events.RenderEvent;
import com.lambda.graphics.RenderMain;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "updateTargetedEntity", at = @At("HEAD"), cancellable = true)
    private void updateTargetedEntityInvoke(float tickDelta, CallbackInfo info) {
        if (EventFlow.post(new RenderEvent.UpdateTarget()).isCanceled()) {
            info.cancel();
        }
    }

    /**
     * Begins our 3d render after the game has rendered the world
     * <pre>{@code
     * Matrix4f matrix4f = matrixStack.peek().getPositionMatrix();
     *     this.loadProjectionMatrix(matrix4f);
     *     matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
     *     matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
     * Matrix3f matrix3f = new Matrix3f(matrices.peek().getNormalMatrix()).invert();
     *     RenderSystem.setInverseViewRotationMatrix(matrix3f);
     *     this.client
     *             .worldRenderer
     *             .setupFrustum(matrices, camera.getPos(), this.getBasicProjectionMatrix(Math.max(d, (double)this.client.options.getFov().getValue().intValue())));
     *     this.client.worldRenderer.render(matrices, tickDelta, limitTime, bl, camera, this, this.lightmapTextureManager, matrix4f);
     * }</pre>
     */
    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V", shift = At.Shift.AFTER))
    private void onRenderWorld(float tickDelta, long limitTime, MatrixStack matrix, CallbackInfo ci) {
        RenderMain.render3D(matrix.peek().getPositionMatrix());
    }
}
