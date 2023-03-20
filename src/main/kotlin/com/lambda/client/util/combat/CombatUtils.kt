package com.lambda.client.util.combat

import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.mixin.extension.size
import com.lambda.client.util.items.attackDamage
import com.lambda.client.util.items.filterByStack
import com.lambda.client.util.items.hotbarSlots
import com.lambda.client.util.items.swapToSlot
import com.lambda.client.util.threads.safeListener
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.EnchantmentProtection
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.monster.EntityMob
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.MobEffects
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemSword
import net.minecraft.item.ItemTool
import net.minecraft.util.CombatRules
import net.minecraft.util.DamageSource
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Explosion
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sqrt


object CombatUtils {
    private val cachedArmorValues = WeakHashMap<EntityLivingBase, Pair<Float, Float>>()

    /**
     * @return The world's difficulty factor
     */
    private fun SafeClientEvent.getDifficultyFactor(): Float {
        return world.difficulty.id * 0.5f
    }

    /**
     * @param value Returns the strength of the explosion
     */
    enum class ExplosionStrength(val value: Float) {
        EndCrystal(6.0f),
        ChargedCreeper(6.0f),
        Bed(5.0f),
        TNT(4.0f),
        Creeper(3.0f),
        WitherSkull(1.0f),
        Fireball(1.0f),
    }

    /**
     * @param type The type of explosion
     * @return The radius of the explosion of a given strength
     */
    fun getExplosionRadius(type: ExplosionStrength): Double {
        return 1.3 * (type.value/0.225) * 0.3
    }

    fun SafeClientEvent.calcDamageFromPlayer(entity: EntityPlayer, assumeCritical: Boolean = false): Float {
        val itemStack = entity.heldItemMainhand
        var damage = itemStack.attackDamage
        if (assumeCritical) damage *= 1.5f

        return calcDamage(player, damage)
    }

    fun SafeClientEvent.calcDamageFromMob(entity: EntityMob): Float {
        var damage = entity.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).attributeValue.toFloat()
        damage += EnchantmentHelper.getModifierForCreature(entity.heldItemMainhand, entity.creatureAttribute)

        return calcDamage(player, damage)
    }

    fun calcDamage(entity: EntityLivingBase, damageIn: Float = 100f, source: DamageSource = DamageSource.GENERIC, roundDamage: Boolean = false): Float {
        if (entity is EntityPlayer && entity.isCreative) return 0.0f // Return 0 directly if entity is a player and in creative mode

        val pair = cachedArmorValues[entity] ?: return 0.0f
        var damage = CombatRules.getDamageAfterAbsorb(damageIn, pair.first, pair.second)

        if (!source.canHarmInCreative()) {
            damage *= getProtectionModifier(entity, source)
            damage *= getResistanceReduction(entity)
        }

        return if (roundDamage) round(damage) else damage
    }

    /**
     * @param pos The position of the explosion
     * @param entity The entity to calculate the damage for
     * @param explosionType The strength of the explosion
     * @return The damage dealt by the explosion
     */
    fun SafeClientEvent.calculateExplosion(pos: Vec3d, entity: EntityLivingBase?, explosionType: ExplosionStrength): Float {
        if (entity is EntityPlayer && entity.isCreative || entity == null) return 0.0f // Return 0 directly if entity is a player and in creative mode or null
        val size = explosionType.value * 2.0
        val distance = entity.positionVector.distanceTo(pos) / size
        val blockDensity = world.getBlockDensity(pos, entity.entityBoundingBox)
        val impact = (1.0 - distance) * blockDensity
        val damage = (impact * impact + impact) / 2.0 * 7.0 * size + 1

        val explosion = Explosion(player.world, entity, pos.x, pos.y, pos.z, explosionType.value, false, true)
        return getBlastReduction(entity, explosion, damage.toFloat() * getDifficultyFactor())
    }

    fun SafeClientEvent.getExplosionVelocity(entity: EntityLivingBase, explosion: Explosion): Vec3d {
        val size = explosion.size * 2.0

        val distance = entity.positionVector.distanceTo(explosion.position) / size
        val blockDensity = entity.world.getBlockDensity(explosion.position, entity.entityBoundingBox)
        val impact = (1.0 - distance) * blockDensity

        val x = entity.posX - explosion.position.x
        val y = entity.posY + entity.eyeHeight - explosion.position.y
        val z = entity.posZ - explosion.position.z
        val result = sqrt(x * x + y * y + z * z)
        if (result == 0.0) return Vec3d.ZERO

        val delta = Vec3d(x / result, y / result, z / result)
        val reduction = EnchantmentProtection.getBlastDamageReduction(entity, impact)
        return delta.scale(reduction)
    }

    /**
     * @param typeClass The class of the entity
     * @param pos The position of the explosion
     * @param explosionType The strength of the explosion
     * @return a list of affected entities
     */
    fun <T: Entity> SafeClientEvent.getExplosionAffectedEntities(typeClass: Class<T>, pos: Vec3d, explosionType: ExplosionStrength): List<T> {
        val size = explosionType.value * 2.0

        return world.getEntitiesWithinAABB(typeClass, AxisAlignedBB(
            floor(pos.x - size - 1.0),
            floor(pos.y - size - 1.0),
            floor(pos.z - size - 1.0),
            floor(pos.x + size + 1.0),
            floor(pos.y + size + 1.0),
            floor(pos.z + size + 1.0))
        )
    }


    /**
     * @param entity The entity to get the blast reduction from
     * @param explosion The explosion to get the blast from
     * @param damageIn The damage to reduce
     * @return The damage after blast reduction
     */
    private fun getBlastReduction(entity: EntityLivingBase, explosion: Explosion, damageIn: Float): Float {
        val armorValue = entity.totalArmorValue.toFloat()
        val damageSource = DamageSource.causeExplosionDamage(explosion)
        val damage =
            CombatRules.getDamageAfterAbsorb(damageIn, armorValue, cachedArmorValues[entity]?.second ?: 0.0f) *
            getProtectionModifier(entity, damageSource) * // Apply protection modifier
            getResistanceReduction(entity) // Apply resistance reduction

        return damage.coerceAtLeast(0.0f)
    }

    /**
     * @param entity The entity to get the protection modifier from
     * @return The resistance absorption modifier. From 0 to 1
     */
    private fun getResistanceReduction(entity: EntityLivingBase): Float {
        val amplifier = entity.getActivePotionEffect(MobEffects.RESISTANCE)?.amplifier ?: return 1.0f
        return 1.0f - (8 * amplifier) / 100.0f // See https://minecraft.fandom.com/wiki/Blast_Protection#Usage
    }

    private fun getProtectionModifier(entity: EntityLivingBase, damageSource: DamageSource): Float {
        var modifier = 0

        entity.armorInventoryList.filter { !it.isEmpty }
            .forEach { armor ->
                val nbtTagList = armor.enchantmentTagList

                for (index in 0 until nbtTagList.tagCount()) {
                    val compoundTag = nbtTagList.getCompoundTagAt(index)

                    val id = compoundTag.getInteger("id")
                    val level = compoundTag.getInteger("lvl")

                    EnchantmentProtection.getEnchantmentByID(id)?.let {
                        modifier += it.calcModifierDamage(level, damageSource)
                    }
                }
            }

        modifier = modifier.coerceIn(0, 20)

        return 1.0f - modifier / 25.0f
    }

    /**
     * @param durability The durability of the armor
     * @param entity The entity to get the armor value from
     * @return Whether the armor is under the durability threshold
     */
    fun isArmorUnderThreshold(durability: Int, entity: EntityLivingBase): Boolean =
        !entity.armorInventoryList.none { it.maxDamage - it.itemDamage <= durability }


    fun SafeClientEvent.equipBestWeapon(preferWeapon: PreferWeapon = PreferWeapon.NONE, allowTool: Boolean = false) {
        player.hotbarSlots.filterByStack {
            val item = it.item
            item is ItemSword || item is ItemAxe || allowTool && item is ItemTool
        }.maxByOrNull {
            val itemStack = it.stack
            val item = itemStack.item
            val damage = itemStack.attackDamage

            when {
                preferWeapon == PreferWeapon.SWORD && item is ItemSword -> damage * 10.0f
                preferWeapon == PreferWeapon.AXE && item is ItemAxe -> damage * 10.0f
                else -> damage
            }
        }?.let {
            swapToSlot(it)
        }
    }

    val EntityLivingBase.scaledHealth: Float
        get() = this.health + this.absorptionAmount * (this.health / this.maxHealth)

    init {
        safeListener<TickEvent.ClientTickEvent> {
            for (entity in world.loadedEntityList.filterIsInstance<EntityLivingBase>()) {
                val armorValue = entity.totalArmorValue.toFloat()
                val toughness = entity.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).attributeValue.toFloat()

                cachedArmorValues[entity] = armorValue to toughness
            }
        }

        listener<ConnectionEvent.Disconnect> {
            cachedArmorValues.clear()
        }

        LambdaEventBus.subscribe(this)
    }

    enum class PreferWeapon {
        SWORD, AXE, NONE
    }
}