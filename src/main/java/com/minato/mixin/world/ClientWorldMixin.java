
package com.minato.mixin.world;

import com.minato.event.EventFlow;
import com.minato.event.events.EntityEvent;
import com.minato.event.events.WorldEvent;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void onAddEntity(Entity entity, CallbackInfo ci) {
        if (EventFlow.post(new EntityEvent.Spawn(entity)).isCanceled()) ci.cancel();
    }

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void onRemoveEntity(int entityId, Entity.RemovalReason removalReason, CallbackInfo ci) {
        Entity entity = ((ClientWorld) (Object) this).getEntityById(entityId);
        if (entity == null) return;
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


    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), cancellable = true)
    private void handleBlockUpdateInject(BlockPos pos, BlockState newState, int flags, CallbackInfo ci) {
        if (EventFlow.post(new WorldEvent.BlockUpdate.Server(pos, newState)).isCanceled()) ci.cancel();
    }
}
