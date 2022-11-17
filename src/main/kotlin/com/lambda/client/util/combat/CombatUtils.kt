package com.lambda.client.util.combat

import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.util.items.attackDamage
import com.lambda.client.util.items.filterByStack
import com.lambda.client.util.items.hotbarSlots
import com.lambda.client.util.items.swapToSlot
import com.lambda.client.util.threads.safeListener
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.EnchantmentProtection
import net.minecraft.enchantment.EnchantmentWaterWalker
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.item.EntityEnderCrystal
import net.minecraft.entity.monster.EntityMob
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.MobEffects
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemSword
import net.minecraft.item.ItemTool
import net.minecraft.util.CombatRules
import net.minecraft.util.DamageSource
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Explosion
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.round


object CombatUtils {
    private val cachedArmorValues = WeakHashMap<EntityLivingBase, Pair<Float, Float>>()

    /**
     * @return The world's difficulty factor
     */
    fun SafeClientEvent.getDifficultyFactor(): Float {
        return world.difficulty.id * 0.5f
    }

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
     * @return The radius of the explosion of a given strength
     */
    private fun getExplosionRadius(type: ExplosionStrength): Double {
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

        if (source != DamageSource.OUT_OF_WORLD) {
            entity.getActivePotionEffect(MobEffects.RESISTANCE)?.let {
                damage *= max(1.0f - (it.amplifier + 1) * 0.2f, 0.0f) // Use this in the future
            }
        }

        damage -= damage * getProtectionModifier(entity, source)

        return if (roundDamage) round(damage) else damage
    }

    fun SafeClientEvent.calculateExplosion(pos: Vec3d, entity: EntityLivingBase, explosionType: ExplosionStrength): Double {
        if (entity is EntityPlayer && entity.isCreative) return 0.0 // Return 0 directly if entity is a player and in creative mode
        val radius = getExplosionRadius(explosionType)
        val distance = entity.positionVector.distanceTo(pos) / radius

        val blockDensity = entity.world.getBlockDensity(pos, entity.entityBoundingBox)
        val v = (1.0 - distance) * blockDensity
        val damage = (v * v + v) / 2.0 * 7.0 * (radius + 1.0)

        val explosion = Explosion(player.world, entity, pos.x, pos.y, pos.z, explosionType.value, false, true)
        return getBlastReduction(entity, explosion, damage.toFloat() * getDifficultyFactor())
    }


    private fun getBlastReduction(entity: EntityLivingBase, explosion: Explosion?, damageL: Float): Double {
        val armorValue = entity.totalArmorValue
        val entityAttributes = entity.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS)
        var damage = CombatRules.getDamageAfterAbsorb(damageL, armorValue.toFloat(), entityAttributes.attributeValue.toFloat())
        val damageSource = if (explosion == null) DamageSource.GENERIC else DamageSource.causeExplosionDamage(explosion)
        val damageReduction = EnchantmentHelper.getEnchantmentModifierDamage(entity.armorInventoryList, damageSource)
        val clamp = MathHelper.clamp(damageReduction.toFloat(), 0.0f, 20.0f)

        damage *= 1.0f - clamp / 25.0f
        if (entity.isPotionActive(MobEffects.RESISTANCE)) damage -= damage / 4.0f

        // Calculate blast protection reduction
        /*val blastLevel = getProtectionModifier(entity, damageSource)
        damage -= damage * blastLevel*/ // TODO: Fix this
        return damage.coerceAtLeast(0.0f).toDouble()
    }

    /*private fun getProtectionModifier(entity: EntityLivingBase): Float {
        var damageAttenuation = 0.0f
        var stackedEnchantments = 0
        for (armor in entity.armorInventoryList.toList()) {
            if (!armor.isItemEnchanted) continue
            val enchantments = EnchantmentHelper.getEnchantments(armor)
            enchantments.forEach { (enchantment, level) ->
                if (enchantment !is EnchantmentProtection) return@forEach
                stackedEnchantments += level
                when (enchantment.protectionType) {
                    EnchantmentProtection.Type.EXPLOSION -> damageAttenuation += (8 * level.coerceAtMost(4)).toFloat()
                    EnchantmentProtection.Type.FIRE -> damageAttenuation += (8 * level.coerceAtMost(4)).toFloat()
                    EnchantmentProtection.Type.FALL -> damageAttenuation += (8 * level.coerceAtMost(4)).toFloat()
                    EnchantmentProtection.Type.PROJECTILE -> damageAttenuation += (8 * level.coerceAtMost(4)).toFloat()
                    else -> {}
                }
            }
        }
        return damageAttenuation
    }*/

    private fun getProtectionModifier(entity: EntityLivingBase, damageSource: DamageSource): Float {
        var modifier = 0

        for (armor in entity.armorInventoryList.toList()) {
            if (armor.isEmpty) continue // Skip if item stack is empty
            val nbtTagList = armor.enchantmentTagList
            for (i in 0 until nbtTagList.tagCount()) {
                val compoundTag = nbtTagList.getCompoundTagAt(i)

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
            for (entity in world.loadedEntityList) {
                if (entity !is EntityLivingBase) continue
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