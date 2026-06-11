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

import com.lambda.Lambda;
import com.lambda.event.EventFlow;
import com.lambda.event.events.RenderEvent;
import com.lambda.gui.DearImGui;
import com.lambda.graphics.RenderMain;
import com.lambda.graphics.outline.OutlineCapturingQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import com.lambda.module.modules.render.BlockOutline;
import com.lambda.module.modules.render.Bobbing;
import com.lambda.module.modules.render.NoRender;
import com.lambda.module.modules.render.Zoom;
import com.lambda.util.render.CursorOverrideProvider;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.Cursor;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
        RenderMain.render();
    }

    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;getEntityRenderCommandQueue()Lnet/minecraft/client/render/command/OrderedRenderCommandQueueImpl;"))
    private OrderedRenderCommandQueueImpl wrapHandQueue(GameRenderer instance, Operation<OrderedRenderCommandQueueImpl> original) {
        OrderedRenderCommandQueueImpl queue = original.call(instance);
        return new OutlineCapturingQueue(queue, -1);
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

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;applyCursorTo(Lnet/minecraft/client/util/Window;)V"))
    private void applyCursorOverride(DrawContext context, Window window, Operation<Void> original) {
        original.call(context, window);

        if (Lambda.getMc().currentScreen instanceof CursorOverrideProvider provider) {
            int mouseX = (int) Lambda.getMc().mouse.getScaledX(window);
            int mouseY = (int) Lambda.getMc().mouse.getScaledY(window);
            Cursor cursor = provider.getCursorOverride(mouseX, mouseY);

            if (cursor != null) {
                cursor.applyTo(window);
                return;
            }
        }
        Cursor.DEFAULT.applyTo(window);
    }

    @Inject(method = "shouldRenderBlockOutline()Z", at = @At("HEAD"), cancellable = true)
    private void injectShouldRenderBlockOutline(CallbackInfoReturnable<Boolean> cir) {
        if (BlockOutline.INSTANCE.isEnabled()) cir.setReturnValue(false);
    }

    @ModifyVariable(method = "bobView", at = @At("STORE"), ordinal = 1)
    private float modifyBobbingSpeed(float f) {
        return Bobbing.INSTANCE.isEnabled()
                ? f * (float) Bobbing.INSTANCE.getSpeed()
                : f;
    }

    @ModifyVariable(method = "bobView", at = @At("STORE"), ordinal = 2)
    private float modifyBobbingMagnitude(float g) {
        return Bobbing.INSTANCE.isEnabled()
                ? g * (float) Bobbing.INSTANCE.getMagnitude()
                : g;
    }
}
