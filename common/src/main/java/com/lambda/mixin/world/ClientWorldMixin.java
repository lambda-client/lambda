package com.lambda.mixin.world;

import com.lambda.event.EventFlow;
import com.lambda.event.events.WorldEvent;
import com.lambda.module.modules.render.WorldColors;
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
    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), cancellable = true)
    private void handleBlockUpdateInject(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        if (EventFlow.post(new WorldEvent.BlockUpdate(pos, state, flags)).isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void addEntity(Entity entity, CallbackInfo ci) {
        if (EventFlow.post(new WorldEvent.EntitySpawn(entity)).isCanceled()) ci.cancel();
    }

    @Inject(method = "getCloudsColor", at = @At("HEAD"), cancellable = true)
    private void getCloudsColorInject(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (WorldColors.INSTANCE.isEnabled() && WorldColors.getCustomClouds()) {
            var color = WorldColors.getCloudColor();

            cir.setReturnValue(
                    new Vec3d(color.getRed(), color.getGreen(), color.getBlue()));
        }
    }

    @Inject(method = "getSkyColor", at = @At("HEAD"), cancellable = true)
    private void getSkyColorInject(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (WorldColors.INSTANCE.isEnabled() && WorldColors.getCustomSky()) {
            var color = WorldColors.getSkyColor();

            cir.setReturnValue(
                    new Vec3d(color.getRed(), color.getGreen(), color.getBlue()));
        }
    }
}
