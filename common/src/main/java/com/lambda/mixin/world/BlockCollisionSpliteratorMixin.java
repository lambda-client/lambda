package com.lambda.mixin.world;

import com.google.common.collect.AbstractIterator;
import com.lambda.event.EventFlow;
import com.lambda.event.events.WorldEvent;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockCollisionSpliterator;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockCollisionSpliterator.class)
public abstract class BlockCollisionSpliteratorMixin <T extends AbstractIterator<T>> {
    @Redirect(method = "computeNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;"))
    private VoxelShape collisionShapeRedirect(BlockState instance, BlockView blockView, BlockPos blockPos, ShapeContext shapeContext) {
        VoxelShape collisionShape = instance.getCollisionShape(blockView, blockPos, shapeContext);
        WorldEvent.Collision event = EventFlow.post(new WorldEvent.Collision(blockPos, instance, collisionShape));
        return event.getShape();
    }
}
