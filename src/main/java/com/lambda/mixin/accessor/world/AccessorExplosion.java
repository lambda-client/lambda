package com.lambda.mixin.accessor.world;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

@Mixin(Explosion.class)
public interface AccessorExplosion {

    @Accessor("exploder") @Nullable
    Entity getExploder();

    @Accessor("size")
    float getSize();

    @Accessor("affectedBlockPositions")
    List<BlockPos> getAffectedBlockPositions();

    @Accessor("playerKnockbackMap")
    Map<EntityPlayer, Vec3d> getPlayerKnockbackMap();

    @Accessor(value = "position", remap = false)
    Vec3d getPosition();
}
