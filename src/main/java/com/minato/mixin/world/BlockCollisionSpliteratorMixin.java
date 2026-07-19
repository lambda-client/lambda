
package com.minato.mixin.world;

import com.google.common.collect.AbstractIterator;
import com.minato.event.EventFlow;
import com.minato.event.events.WorldEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockCollisionSpliterator;
import net.minecraft.world.CollisionView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockCollisionSpliterator.class)
public abstract class BlockCollisionSpliteratorMixin<T extends AbstractIterator<T>> {
    @WrapOperation(method = "computeNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/ShapeContext;getCollisionShape(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/CollisionView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;"))
    private VoxelShape wrapCollisionShape(ShapeContext instance, BlockState blockState, CollisionView collisionView, BlockPos blockPos, Operation<VoxelShape> original) {
        VoxelShape collisionShape = original.call(instance, blockState, collisionView, blockPos);
        WorldEvent.Collision event = EventFlow.post(new WorldEvent.Collision(blockPos, blockState, collisionShape));
        return event.getShape();
    }
}
