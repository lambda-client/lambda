package com.lambda.util.world.raycast

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.interaction.rotation.Rotation
import com.lambda.threading.runSafe
import com.lambda.util.math.VecUtils.distSq
import net.minecraft.entity.Entity
import net.minecraft.entity.projectile.ProjectileUtil
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import kotlin.math.max
import kotlin.math.pow

object RayCastUtils {
    private val entityPredicate = { entity: Entity -> !entity.isSpectator && entity.canHit() }

    fun SafeContext.rayCast(
        start: Vec3d,
        end: Vec3d,
        reach: Double,
        mask: RayCastMask,
        fluids: Boolean = false,
    ): HitResult? {
        val vec = end.multiply(reach)
        val point = start.add(vec)

        val block = run {
            if (!mask.block) return@run null

            val fluidHandling = if (fluids) RaycastContext.FluidHandling.ANY else RaycastContext.FluidHandling.NONE
            val context = RaycastContext(start, point, RaycastContext.ShapeType.OUTLINE, fluidHandling, player)
            val result = world.raycast(context)

            result?.blockResult
        }

        val entity = run {
            if (!mask.entity) return@run null

            val playerBox = player.boundingBox.stretch(vec).expand(1.0)
            val result = ProjectileUtil.raycast(player, start, point, playerBox, entityPredicate, reach.pow(2))

            result?.entityResult
        }

        return listOfNotNull(block, entity).minByOrNull { start distSq it.pos }
    }

    // ToDo: Should rather move player hitbox down and check collision
    fun distanceToGround(maxDist: Double = 100.0) = runSafe {
        val pos = player.pos.add(0.0, 0.1, 0.0)
        val cast = Rotation.DOWN.rayCast(maxDist, pos, false, RayCastMask.BLOCK) ?: return@runSafe maxDist

        return@runSafe max(0.0, pos.y - cast.pos.y)
    }

    val HitResult.entityResult: EntityHitResult?
        get() {
            if (type == HitResult.Type.MISS) return null
            return this as? EntityHitResult
        }

    val HitResult.blockResult: BlockHitResult?
        get() {
            if (type == HitResult.Type.MISS) return null
            return this as? BlockHitResult
        }

    fun HitResult.distanceTo(pos: Vec3d) = this.pos.distanceTo(pos)

    val HitResult.orNull get() = entityResult ?: blockResult

    val HitResult?.orMiss
        get() = this ?: object : HitResult(mc.player?.eyePos ?: Vec3d.ZERO) {
            override fun getType() = Type.MISS
        }
}