package com.lambda.client.util.combat

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.CombatManager
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
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.min

object CrystalUtils {
    /* Position Finding */
    fun SafeClientEvent.getBestPlace(target: EntityLivingBase): CombatManager.CrystalPlaceInfo? {
        val placeRange = CrystalAura.placeRange.toDouble()
        return VectorUtils.getBlocksInRange(target.positionVector,
            Vec3d(-placeRange, -2.0, -placeRange),
            Vec3d(placeRange, 1.0, placeRange)
        )
            .filter { canPlace(it) }
            .maxByOrNull {
                val place = calcCrystalDamage(getOptimalOffset(it), target)
                place.targetDamage - place.selfDamage
            }
            ?.let { CombatManager.CrystalPlaceInfo(getOptimalOffset(it), calcCrystalDamage(getOptimalOffset(it), target)) }
    }

    private fun getOptimalOffset(position: BlockPos): Vec3d {
        return Vec3d(
            min(position.x + 0.5, position.x - 0.5),
            position.y + 1.0,
            min(position.z + 0.5, position.z - 0.5),
        )
    }

    fun SafeClientEvent.canPlaceCrystal(should: (CombatManager.CrystalDamage) -> Boolean): CombatManager.CrystalPlaceInfo? {
        if (!CrystalAura.doPlace) return null
        val target = getTarget() ?: return null
        val place = getPlaceInfo(target) ?: return null
        if (place.position.distanceTo(player.positionVector) > CrystalAura.placeRange) return null
        return if (should(place.info) && player.allSlots.countItem(Items.END_CRYSTAL) > 0) place else null
    }

    fun SafeClientEvent.canExplodeCrystal(): CombatManager.Crystal? {
        if (!CrystalAura.doExplode) return null
        val crystal = getTarget()?.let { target ->
            CombatManager.placedCrystals
                .filter {
                    it.entity.distanceTo(target.position) <= CrystalAura.explodeRange
                        && it.entity.distanceTo(player.position) <= CrystalAura.explodeRange
                        && it.damage.targetDamage >= CrystalAura.explodeMinDamage
                        && it.damage.selfDamage <= CrystalAura.explodeMaxSelfDamage
                }
                .maxByOrNull { it.damage.targetDamage - it.damage.selfDamage }
        } ?: return null
        return crystal
    }

    fun getPlaceInfo(target: EntityLivingBase?): CombatManager.CrystalPlaceInfo? {
        return CombatManager.toPlaceList[target]
    }

    private fun getTarget(): EntityLivingBase? {
        return CombatManager.target
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
            && canPlaceCollide(pos, entity)
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
    fun SafeClientEvent.canPlaceCollide(pos: BlockPos, entity: Entity? = null): Boolean {
        return mc.world?.let { world ->
            !world.getEntitiesWithinAABBExcludingEntity(entity, getCrystalPlacingBB(pos.up())).any {
                !it.isDead && it is EntityLivingBase && it.health > 0.0f
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
     * @param pos The position to check, use the center of the crystal
     * @param entity The entity to check for
     * @return The damage the crystal will deal to the entity
     */
    fun SafeClientEvent.calcCrystalDamage(pos: Vec3d?, entity: EntityLivingBase?): CombatManager.CrystalDamage {
        val position =
            pos ?: (entity?.positionVector ?: Vec3d.ZERO)
                .toBlockPos(0.5, 1.0, 0.5).toVec3d()

        // Calculate raw damage (based on blocks and distance)
        val targetDamage = calculateExplosion(position, entity, CombatUtils.ExplosionStrength.EndCrystal)
        val selfDamage = calculateExplosion(position, player, CombatUtils.ExplosionStrength.EndCrystal)
        val targetDistance =
            if (entity != null) position.distanceTo(entity.positionVector)
            else Double.MAX_VALUE

        val selfDistance = position.distanceTo(player.positionVector)

        /**
         * We use the [getClosestVisibleSide] function instead of raytracing because it's faster
         */
        val walls = getClosestVisibleSide(position)

        // Return the damage
        return CombatManager.CrystalDamage(targetDamage, selfDamage, targetDistance, selfDistance, walls == null)
    }
    /* End of damage calculation */
}