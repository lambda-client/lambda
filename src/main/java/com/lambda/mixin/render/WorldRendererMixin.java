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

import com.google.common.collect.Sets;
import com.lambda.event.EventFlow;
import com.lambda.event.events.RenderEvent;
import com.lambda.graphics.RenderMain;
import com.lambda.graphics.outline.OutlineHandler;
import com.lambda.module.modules.render.Freecam;
import com.lambda.module.modules.render.CameraTweaks;
import com.lambda.module.modules.render.NoRender;
import net.minecraft.client.world.ClientWorld;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.*;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.tick.TickManager;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import java.util.Set;

import java.util.Iterator;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Shadow
    @Final
    private BlockEntityRenderManager blockEntityRenderManager;
    @Shadow
    private ClientWorld world;

    @Unique
    @Nullable
    private Entity lambda$currentEntity;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline,
            Camera camera, Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        RenderMain.updateState(positionMatrix, basicProjectionMatrix, projectionMatrix);
        EventFlow.post(RenderEvent.PreRenderWorld.INSTANCE);
    }

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

    @Inject(method = "fillEntityRenderStates", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderManager;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD)
    private void injectFillEntityRenderStates(Camera camera, Frustum frustum, RenderTickCounter tickCounter,
            WorldRenderState renderStates, CallbackInfo ci, Vec3d vec3d, double d, double e, double f,
            TickManager tickManager, boolean bl, Iterator var14, Entity entity) {
        this.lambda$currentEntity = entity;
    }

    @ModifyExpressionValue(method = "fillEntityRenderStates", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;isRenderingReady(Lnet/minecraft/util/math/BlockPos;)Z"))
    private boolean lambda$bypassIsRenderingReady(boolean original) {
        if (this.lambda$currentEntity != null && OutlineHandler.shouldCapture(this.lambda$currentEntity.getId()))
            return true;
        return original;
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

    @Inject(method = "fillBlockEntityRenderStates", at = @At("TAIL"))
    private void injectOutlineBlockEntities(Camera camera, float tickProgress, WorldRenderState renderStates, CallbackInfo ci) {
        if (!OutlineHandler.INSTANCE.hasBlockOutlines()) return;

        Set<BlockPos> xRayTargets = OutlineHandler.INSTANCE.getXrayBlockStyles().keySet();
        Set<BlockPos> depthTargets = OutlineHandler.INSTANCE.getDepthTestedBlockStyles().keySet();

        for (BlockPos target : Sets.union(xRayTargets, depthTargets)) {
            if (!this.world.getChunkManager().isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)) continue;
            boolean alreadyCollected = renderStates.blockEntityRenderStates.stream()
                    .anyMatch(state -> state.pos.equals(target));
            if (alreadyCollected) continue;

            BlockEntity blockEntity = this.world.getBlockEntity(target);
            if (blockEntity != null && !blockEntity.isRemoved()) {
                BlockEntityRenderState state = this.blockEntityRenderManager.getRenderState(blockEntity, tickProgress, null);
                if (state != null) renderStates.blockEntityRenderStates.add(state);
            }
        }
    }
}
