package com.lambda.client.util.combat

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.combat.CombatUtils.calculateExplosion
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.math.VectorUtils.distanceTo
import net.minecraft.block.material.Material
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityEnderCrystal
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

object CrystalUtils {
    /* Position Finding */
    fun SafeClientEvent.getPlacePos(target: EntityLivingBase?, center: Entity?, radius: Float): List<BlockPos> {
        if (center == null) return emptyList()
        val centerPos = if (center == player) center.getPositionEyes(1f) else center.positionVector
        return VectorUtils.getBlockPosInSphere(centerPos, radius).filter { canPlace(it, target) }
    }

    fun SafeClientEvent.getCrystalList(center: Vec3d, range: Float): List<EntityEnderCrystal> =
        world.loadedEntityList.toList()
            .filterIsInstance<EntityEnderCrystal>()
            .filter { entity -> entity.isEntityAlive && entity.distanceTo(center) <= range }

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
        val block = mc.world?.getBlockState(pos)?.block
        return block == Blocks.BEDROCK || block == Blocks.OBSIDIAN
    }

    private fun isValidMaterial(material: Material) =
        !material.isLiquid && material.isReplaceable

    fun getCrystalPlacingBB(pos: BlockPos) =
        AxisAlignedBB(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + 2.0, pos.z + 1.0
        )

    fun getCrystalBB(pos: BlockPos): AxisAlignedBB =
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
    fun SafeClientEvent.calcCrystalDamage(crystal: EntityEnderCrystal, entity: EntityLivingBase) =
        calcCrystalDamage(crystal.positionVector, entity)

    fun SafeClientEvent.calcCrystalDamage(pos: BlockPos, entity: EntityLivingBase) =
        calcCrystalDamage(Vec3d(pos).add(0.5, 1.0, 0.5), entity)

    fun SafeClientEvent.calcCrystalDamage(pos: Vec3d, entity: EntityLivingBase): Float {
        // Return 0 directly if entity is a player and in creative mode
        if (entity is EntityPlayer && entity.isCreative) return 0.0f

        // Calculate raw damage (based on blocks and distance)
        val damage = calculateExplosion(pos, entity, CombatUtils.ExplosionStrength.EndCrystal)

        // Return the damage
        return damage.coerceAtLeast(0.0).toFloat()
    }
    /* End of damage calculation */
}