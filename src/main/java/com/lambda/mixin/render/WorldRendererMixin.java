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
import com.lambda.graphics.outline.OutlineRenderManager;
import com.lambda.graphics.outline.IWorldRenderer;
import com.lambda.module.modules.player.Freecam;
import com.lambda.module.modules.render.CameraTweaks;
import com.lambda.module.modules.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.Handle;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin implements IWorldRenderer {

    @Shadow
    private Framebuffer entityOutlineFramebuffer;

    @Shadow
    @Final
    private DefaultFramebufferSet framebufferSet;

    @Unique
    private Stack<Framebuffer> framebufferStack;

    @Unique
    private Stack<Handle<Framebuffer>> framebufferHandleStack;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        framebufferStack = new ObjectArrayList<>();
        framebufferHandleStack = new ObjectArrayList<>();
    }

    // === IWorldRenderer implementation ===

    @Override
    public void lambda$pushEntityOutlineFramebuffer(Framebuffer framebuffer) {
        framebufferStack.push(this.entityOutlineFramebuffer);
        this.entityOutlineFramebuffer = framebuffer;

        framebufferHandleStack.push(this.framebufferSet.entityOutlineFramebuffer);
        this.framebufferSet.entityOutlineFramebuffer = () -> framebuffer;
    }

    @Override
    public void lambda$popEntityOutlineFramebuffer() {
        this.entityOutlineFramebuffer = framebufferStack.pop();
        this.framebufferSet.entityOutlineFramebuffer = framebufferHandleStack.pop();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline,
            Camera camera, Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        RenderMain.updateState(positionMatrix, basicProjectionMatrix, projectionMatrix);
        EventFlow.post(RenderEvent.PreRenderWorld.INSTANCE);
    }

    // === Outline entity render hook ===

    @Inject(method = "pushEntityRenders", at = @At("TAIL"))
    private void onPushEntityRenders(MatrixStack matrices, WorldRenderState worldState, OrderedRenderCommandQueue queue,
            CallbackInfo info) {
        // Obsolete: We now use VertexCapture + ID Pass instead of re-rendering to a
        // custom FB
        // OutlineRenderManager.onPushEntityRenders(matrices, worldState,
        // (WorldRenderer) (Object) this);
    }

    // === Existing hooks ===

    @Inject(method = "hasBlindnessOrDarkness(Lnet/minecraft/client/render/Camera;)Z", at = @At(value = "HEAD"), cancellable = true)
    private void modifyEffectCheck(Camera camera, CallbackInfoReturnable<Boolean> cir) {
        Entity entity = camera.getFocusedEntity();
        if (entity instanceof LivingEntity livingEntity && NoRender.INSTANCE.isEnabled()) {
            boolean blind = livingEntity.hasStatusEffect(StatusEffects.BLINDNESS) && !NoRender.getNoBlindness();
            boolean dark = livingEntity.hasStatusEffect(StatusEffects.DARKNESS) && !NoRender.getNoDarkness();
            cir.setReturnValue(blind || dark);
        }
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;updateCamera(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Z)V"), index = 2)
    private boolean renderSetupTerrainModifyArg(boolean spectator) {
        return Freecam.INSTANCE.isEnabled() || CameraTweaks.INSTANCE.isEnabled() || spectator;
    }

    @ModifyExpressionValue(method = "fillEntityRenderStates", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;isThirdPerson()Z"))
    private boolean modifyIsThirdPerson(boolean original) {
        return Freecam.INSTANCE.isEnabled() || original;
    }

    @ModifyReturnValue(method = "hasBlindnessOrDarkness", at = @At("RETURN"))
    boolean modHasBlindnessOrDarkness(boolean original) {
        if (NoRender.INSTANCE.isEnabled() && (NoRender.getNoBlindness() || NoRender.getNoDarkness()))
            return false;

        return original;
    }
}
