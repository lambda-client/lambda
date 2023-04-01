package com.lambda.client.util.combat

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.manager.managers.CrystalManager
import com.lambda.client.util.combat.CombatUtils.calculateExplosion
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.math.VectorUtils.toVec3d
import com.lambda.client.util.world.getClosestVisibleSide
import net.minecraft.block.material.Material
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityEnderCrystal
import net.minecraft.init.Blocks
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

object CrystalUtils {
    fun SafeClientEvent.getBestPlace(target: EntityLivingBase): CrystalManager.CrystalPlaceInfo? {
        return CrystalManager.toPlaceList[target]?.maxByOrNull { it.damage.targetDamage - it.damage.selfDamage - (it.damage.targetDistance - it.damage.selfDistance) }
    }

    fun SafeClientEvent.getBestPlace(target: EntityLivingBase, placeRange: Float): CrystalManager.CrystalPlaceInfo? {
        return getPlaces(target, placeRange).values.flatten().maxByOrNull { it.damage.targetDamage - it.damage.selfDamage - (it.damage.targetDistance - it.damage.selfDistance) }
    }

    fun SafeClientEvent.getPlaces(target: EntityLivingBase, placeRange: Float): Map<EntityLivingBase, List<CrystalManager.CrystalPlaceInfo>> {
        return VectorUtils.getBlockPosInSphere(target.positionVector, placeRange)
            .filter { canPlace(it, target) }
            .map { CrystalManager.CrystalPlaceInfo(it.toVec3d(), calcCrystalDamage(it.toVec3d(0.5, 1.0, 0.5), target)) }
            .groupBy { target }
    }


    fun SafeClientEvent.getBestCrystal(): CrystalManager.Crystal? {
        return CrystalManager.placedCrystals.maxByOrNull { it.info.damage.targetDamage - it.info.damage.selfDamage }
    }

    fun SafeClientEvent.getBestCrystal(placer: EntityLivingBase, range: Float): CrystalManager.Crystal? {
        return CombatManager.target?.let { target ->
            getCrystals(placer, target, range)
                .maxByOrNull { it.info.damage.targetDamage - it.info.damage.selfDamage }
        }
    }

    fun SafeClientEvent.getCrystals(source: EntityLivingBase, target: EntityLivingBase, range: Float): List<CrystalManager.Crystal> {
        return world.loadedEntityList
            .filter {
                    it is EntityEnderCrystal
                    && it.distanceTo(target.positionVector) <= range
            }.map {
                CrystalManager.Crystal(it as EntityEnderCrystal, CrystalManager.CrystalPlaceInfo(it.positionVector, calcCrystalDamage(it.positionVector, target)))
            }
    }

    /**
     * @param pos The position to check
     * @param entity The entity to check for
     * @return Whether the crystal can be placed at the position or not
     */
    fun SafeClientEvent.canPlace(pos: BlockPos, entity: EntityLivingBase? = null): Boolean {
        val posUp1 = pos.up()
        val posUp2 = posUp1.up()

        return canPlaceOn(pos)
            && (entity == null || !getCrystalPlacingBB(posUp1).intersects(entity.entityBoundingBox))
            && canPlaceCollide(pos)
            && mc.world?.let {
            isValidMaterial(it.getBlockState(posUp1).material)
                && isValidMaterial(it.getBlockState(posUp2).material)
        } ?: false
    }

    /** Checks if the block is valid for placing crystal */
    fun SafeClientEvent.canPlaceOn(pos: BlockPos): Boolean {
        val block = mc.world?.getBlockState(pos)?.block
        return block == Blocks.BEDROCK || block == Blocks.OBSIDIAN
    }


    private fun isValidMaterial(material: Material) =
        !material.isLiquid && material.isReplaceable

    private fun getCrystalPlacingBB(pos: BlockPos) =
        AxisAlignedBB(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + 2.0, pos.z + 1.0
        )

    private fun getCrystalBB(pos: BlockPos): AxisAlignedBB =
        AxisAlignedBB(
            pos.x - 0.5, pos.y - 0.5, pos.z - 0.5,
            pos.x + 1.5, pos.y + 2.0, pos.z + 1.5
        )

    /**
     * @param pos The position to check
     * @return Whether the placement collision box intersects with entities or not
     */
    fun SafeClientEvent.canPlaceCollide(pos: BlockPos): Boolean {
        val placingBB = getCrystalPlacingBB(pos.up())
        return mc.world?.let { world ->
            world.getEntitiesWithinAABBExcludingEntity(null, placingBB).all {
                it.isDead || it is EntityLivingBase && it.health <= 0.0f
            }
        } ?: false
    }
    /* End of position finding */

    /* Damage calculation */

    fun SafeClientEvent.calcCrystalDamage(crystal: EntityEnderCrystal?) =
        calcCrystalDamage(crystal, CombatManager.target)

    fun SafeClientEvent.calcCrystalDamage(crystal: EntityEnderCrystal?, entity: EntityLivingBase?) =
        calcCrystalDamage(crystal?.positionVector, entity)

    /**
     * @param src The position to check, use the center of the crystal
     * @param entity The entity to check for
     * @return The damage the crystal will deal to the entity
     */
    fun SafeClientEvent.calcCrystalDamage(src: Vec3d?, entity: EntityLivingBase?): CrystalManager.CrystalDamage {
        val position = src ?: Vec3d.ZERO

        // Calculate raw damage (based on blocks and distance)
        val targetDamage = calculateExplosion(position, entity, CombatUtils.ExplosionStrength.EndCrystal)
        val selfDamage = calculateExplosion(position, player, CombatUtils.ExplosionStrength.EndCrystal)

        val targetDistance = entity?.let { target -> position.distanceTo(target.position) } ?: Double.MAX_VALUE
        val selfDistance = position.distanceTo(player.getPositionEyes(1.0f))

        /**
         * We use the [getClosestVisibleSide] function instead of raytracing because it's faster
         */
        val walls = getClosestVisibleSide(position)

        // Return the damage
        return CrystalManager.CrystalDamage(targetDamage, selfDamage, targetDistance, selfDistance, walls == null)
    }
    /* End of damage calculation */
}