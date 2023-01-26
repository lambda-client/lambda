package com.lambda.client.util.combat

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.manager.managers.CrystalManager
import com.lambda.client.module.modules.combat.CrystalAura
import com.lambda.client.util.combat.CombatUtils.calculateExplosion
import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.countItem
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.math.VectorUtils.toVec3d
import com.lambda.client.util.world.getClosestVisibleSide
import net.minecraft.block.material.Material
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityEnderCrystal
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.util.EntitySelectors
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.min

object CrystalUtils {
    /* Position Finding */
    fun SafeClientEvent.getBestPlace(target: EntityLivingBase): CrystalManager.CrystalPlaceInfo? {
        val placeRange = CrystalAura.placeRangeOffset
        return VectorUtils.getBlockPosInSphere(target.positionVector, placeRange)
            .filter { canPlace(it, target) }
            .maxByOrNull {
                val place = calcCrystalDamage(getOptimalOffset(it.toVec3d()), target)
                place.targetDamage - place.selfDamage
            }?.let {
                val position = getOptimalOffset(it.toVec3d())
                CrystalManager.CrystalPlaceInfo(position, calcCrystalDamage(position, target))
            }
    }

    private fun getOptimalOffset(position: Vec3d): Vec3d {
        return Vec3d(
            min(position.x + 0.5, position.x - 0.5),
            position.y,
            min(position.z + 0.5, position.z - 0.5)
        )
    }

    fun SafeClientEvent.canPlaceCrystal(place: CrystalManager.CrystalPlaceInfo, should: (CrystalManager.CrystalDamage) -> Boolean): CrystalManager.CrystalPlaceInfo? {
        if (!CrystalAura.doPlace) return null
        return if
            (
                !should(place.damage)
                ||
                player.allSlots.countItem(Items.END_CRYSTAL) < 0
                || player.distanceTo(place.position) > CrystalAura.placeRange
            ) null
        else place
    }

    fun SafeClientEvent.canExplodeCrystal(focus: CrystalManager.Crystal, should: (CrystalManager.CrystalDamage) -> Boolean): CrystalManager.Crystal? {
        return if
            (
                !CrystalAura.doExplode
                ||
                !should(focus.info.damage)
                || player.distanceTo(focus.info.position) > CrystalAura.explodeRange
            ) null
        else focus
    }

    fun getPlaceInfo(target: EntityLivingBase?): CrystalManager.CrystalPlaceInfo? {
        return CrystalManager.toPlaceList[target]
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
            && !canPlaceCollide(pos, entity, true)
            && mc.world?.let {
                isValidMaterial(it.getBlockState(posUp1).material)
                && isValidMaterial(it.getBlockState(posUp2).material)
            } ?: false
    }

    /**
     * @param pos The position to check
     * @return Whether the block can be placed on the position or not
     */
    fun SafeClientEvent.canPlaceOn(pos: BlockPos): Boolean {
        val block = mc.world.getBlockState(pos).block
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
    fun SafeClientEvent.canPlaceCollide(pos: BlockPos, entity: Entity? = null, includePlayer: Boolean = false): Boolean {
        val predicate = if (includePlayer) EntitySelectors.IS_ALIVE else EntitySelectors.NOT_SPECTATING
        return world.getEntities(Entity::class.java, predicate)
            .filter { it != entity }
            .any { getCrystalPlacingBB(pos).intersects(it.entityBoundingBox) }
    }
    /* End of position finding */

    /* Damage calculation */

    fun SafeClientEvent.calcCrystalDamage(crystal: EntityEnderCrystal?) =
        calcCrystalDamage(crystal, CombatManager.target)

    fun SafeClientEvent.calcCrystalDamage(crystal: EntityEnderCrystal?, entity: EntityLivingBase?) =
        calcCrystalDamage(crystal?.positionVector, entity)

    /**
     * @param pos The position to check, use the center of the crystal
     * @param entity The entity to check for
     * @return The damage the crystal will deal to the entity
     */
    fun SafeClientEvent.calcCrystalDamage(pos: Vec3d?, entity: EntityLivingBase?): CrystalManager.CrystalDamage {
        val position =
            pos ?: (entity?.positionVector ?: Vec3d.ZERO)

        // Calculate raw damage (based on blocks and distance)
        val targetDamage = calculateExplosion(position, entity, CombatUtils.ExplosionStrength.EndCrystal)
        val selfDamage = calculateExplosion(position, player, CombatUtils.ExplosionStrength.EndCrystal)
        val targetDistance =
            if (entity != null) position.distanceTo(entity.position)
            else Double.MAX_VALUE

        val selfDistance = position.distanceTo(player.positionVector)

        /**
         * We use the [getClosestVisibleSide] function instead of raytracing because it's faster
         */
        val walls = getClosestVisibleSide(position)

        // Return the damage
        return CrystalManager.CrystalDamage(targetDamage, selfDamage, targetDistance, selfDistance, walls == null)
    }
    /* End of damage calculation */
}