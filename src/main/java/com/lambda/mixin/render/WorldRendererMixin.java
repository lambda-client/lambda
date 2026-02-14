
package com.lambda.mixin.render;

import com.lambda.event.EventFlow;
import com.lambda.event.events.RenderEvent;
import com.lambda.graphics.RenderMain;
import com.lambda.graphics.outline.OutlineManager;
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
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.Handle;
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
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;

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

    @Unique
    @Nullable
    private Entity lambda$currentEntity;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        framebufferStack = new ObjectArrayList<>();
        framebufferHandleStack = new ObjectArrayList<>();
    }

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
    private void onRender(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
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
    private void injectFillEntityRenderStates(Camera camera, Frustum frustum, RenderTickCounter tickCounter, WorldRenderState renderStates, CallbackInfo ci, Vec3d vec3d, double d, double e, double f, TickManager tickManager, boolean bl, Iterator var14, Entity entity) {
        this.lambda$currentEntity = entity;
    }

    @ModifyExpressionValue(method = "fillEntityRenderStates", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;isRenderingReady(Lnet/minecraft/util/math/BlockPos;)Z"))
    private boolean lambda$bypassIsRenderingReady(boolean original) {
        if (this.lambda$currentEntity != null && OutlineManager.shouldCapture(this.lambda$currentEntity.getId()))
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
}
