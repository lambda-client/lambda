package com.lambda.util.world.raycast

import com.lambda.context.SafeContext
import com.lambda.manager.rotation.Rotation
import com.lambda.threading.runSafe
import com.lambda.util.math.VecUtils.distSq
import net.minecraft.entity.Entity
import net.minecraft.entity.projectile.ProjectileUtil.raycast
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import kotlin.math.max

object RayCastUtils {
    private val entityPredicate = { entity: Entity -> !entity.isSpectator && entity.canHit() }

    fun SafeContext.rayCast(
        pos: Vec3d,
        dir: Vec3d,
        reach: Double,
        mask: RayCastMask,
        fluids: Boolean = false
    ): HitResult? {
        val vec = dir.multiply(reach)
        val point = pos.add(vec)

        val block = run {
            if (!mask.block) return@run null

            val fluidHandling = if (fluids) RaycastContext.FluidHandling.ANY else RaycastContext.FluidHandling.NONE
            val context = RaycastContext(pos, point, RaycastContext.ShapeType.OUTLINE, fluidHandling, player)
            val block = world.raycast(context)

            block?.blockResult
        }

        val entity = run {
            if (!mask.entity) return@run null

            val box = player.boundingBox.stretch(vec).expand(1.0)
            val entity = raycast(player, pos, point, box, entityPredicate, reach * reach)

            entity?.entityResult
        }

        return listOfNotNull(block, entity).minByOrNull { pos distSq it.pos }
    }

    fun distanceToGround(maxDist: Double = 100.0) = runSafe {
        val pos = player.pos.add(0.0, 0.1, 0.0)
        val cast = Rotation.DOWN.rayCast(maxDist, RayCastMask.BLOCK, pos, false) ?: return@runSafe maxDist

        return@runSafe max(0.0, pos.y - cast.pos.y)
    }

    val HitResult.entityResult: EntityHitResult? get() {
        if (type == HitResult.Type.MISS) return null
        return this as? EntityHitResult
    }

    val HitResult.blockResult: BlockHitResult? get() {
        if (type == HitResult.Type.MISS) return null
        return this as? BlockHitResult
    }
}