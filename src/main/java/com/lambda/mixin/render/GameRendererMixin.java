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

package com.lambda.mixin.render;

import com.lambda.event.EventFlow;
import com.lambda.event.events.RenderEvent;
import com.lambda.graphics.RenderMain;
import com.lambda.gui.DearImGui;
import com.lambda.module.modules.render.BlockOutline;
import com.lambda.module.modules.render.NoRender;
import com.lambda.module.modules.render.Zoom;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "updateCrosshairTarget(F)V", at = @At("HEAD"), cancellable = true)
    private void updateTargetedEntityInvoke(float tickDelta, CallbackInfo info) {
        if (EventFlow.post(new RenderEvent.UpdateTarget()).isCanceled()) {
            info.cancel();
        }
    }

    @WrapOperation(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
    void onRenderWorld(WorldRenderer instance, ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, Operation<Void> original) {
        original.call(instance, allocator, tickCounter, renderBlockOutline, camera, positionMatrix, basicProjectionMatrix, projectionMatrix, fogBuffer, fogColor, renderSky);
        RenderMain.render3D(positionMatrix, basicProjectionMatrix);
    }

    @ModifyExpressionValue(method = "renderWorld", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F", ordinal = 0))
    private float modifyMax(float original) {
        return (NoRender.INSTANCE.isEnabled() && NoRender.getNoNausea()) ? 0 : original;
    }

    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    private void injectShowFloatingItem(ItemStack floatingItem, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoFloatingItemAnimation()) ci.cancel();
    }

    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private float modifyGetFov(float original) {
        Zoom.updateCurrentZoom();
        return original / Zoom.getLerpedZoom();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", shift = At.Shift.AFTER))
    private void onGuiRenderComplete(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        DearImGui.INSTANCE.render();
    }

    @Inject(method = "shouldRenderBlockOutline()Z", at = @At("HEAD"), cancellable = true)
    private void injectShouldRenderBlockOutline(CallbackInfoReturnable<Boolean> cir) {
        if (BlockOutline.INSTANCE.isEnabled()) cir.setReturnValue(false);
    }
}
