package com.lambda.client.mixin.extension

import com.lambda.mixin.accessor.world.AccessorExplosion
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Explosion

val Explosion.exploder: Entity? get() = (this as AccessorExplosion).exploder
val Explosion.size: Float get() = (this as AccessorExplosion).size
val Explosion.affectedBlockPositions: List<BlockPos> get() = (this as AccessorExplosion).affectedBlockPositions
val Explosion.playerKnockbackMap: Map<EntityPlayer, Vec3d> get() = (this as AccessorExplosion).playerKnockbackMap
val Explosion.position: Vec3d get() = (this as AccessorExplosion).position