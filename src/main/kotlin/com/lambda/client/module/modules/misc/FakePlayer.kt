package com.lambda.client.module.modules.misc

import com.lambda.client.command.CommandManager
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.GuiEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.formatValue
import com.lambda.client.util.threads.onMainThread
import com.lambda.client.util.threads.onMainThreadSafe
import com.lambda.client.util.threads.runSafeR
import com.mojang.authlib.GameProfile
import kotlinx.coroutines.runBlocking
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.client.gui.GuiGameOver
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Enchantments
import net.minecraft.init.Items
import net.minecraft.init.MobEffects
import net.minecraft.item.ItemStack
import net.minecraft.potion.PotionEffect
import java.util.*

object FakePlayer : Module(
    name = "FakePlayer",
    description = "Spawns a client sided fake player",
    category = Category.MISC
) {
    private val copyInventory by setting("Copy Inventory", true)
    private val copyPotions by setting("Copy Potions", true)
    private val maxArmor by setting("Max Armor", false)
    private val gappleEffects by setting("Gapple Effects", false)
    private val playerName by setting("Player Name", "Player")

    private const val ENTITY_ID = -696969420
    private var fakePlayer: EntityOtherPlayerMP? = null

    init {
        onEnable {
            runSafeR {
                if (playerName == "Player") {
                    MessageSendHelper.sendChatMessage("You can use ${formatValue("${CommandManager.prefix}set FakePlayer PlayerName <name>")} to set a custom name")
                }
                spawnFakePlayer()
            } ?: disable()
        }

        onDisable {
            runBlocking {
                onMainThreadSafe {
                    fakePlayer?.setDead()
                    world.removeEntityFromWorld(ENTITY_ID)
                    fakePlayer = null
                }
            }
        }

        listener<ConnectionEvent.Disconnect> {
            disable()
        }

        listener<GuiEvent.Displayed> {
            if (it.screen is GuiGameOver) disable()
        }
    }

    private fun SafeClientEvent.spawnFakePlayer() {
        fakePlayer = EntityOtherPlayerMP(world, GameProfile(UUID.randomUUID(), playerName)).apply {
            copyLocationAndAnglesFrom(player)
            rotationYawHead = player.rotationYawHead

            if (copyInventory) inventory.copyInventory(player.inventory)
            if (copyPotions) copyPotions(player)
            if (maxArmor) addMaxArmor()
            if (gappleEffects) addGappleEffects()
        }.also {
            runBlocking {
                onMainThread {
                    world.addEntityToWorld(ENTITY_ID, it)
                }
            }
        }
    }

    private fun EntityPlayer.copyPotions(otherPlayer: EntityPlayer) {
        for (potionEffect in otherPlayer.activePotionEffects) {
            addPotionEffectForce(PotionEffect(potionEffect.potion, Int.MAX_VALUE, potionEffect.amplifier))
        }
    }

    private fun EntityPlayer.addMaxArmor() {
        inventory.armorInventory[3] = ItemStack(Items.DIAMOND_HELMET).apply {
            addMaxEnchantment(Enchantments.PROTECTION)
            addMaxEnchantment(Enchantments.UNBREAKING)
            addMaxEnchantment(Enchantments.RESPIRATION)
            addMaxEnchantment(Enchantments.AQUA_AFFINITY)
            addMaxEnchantment(Enchantments.MENDING)
        }

        inventory.armorInventory[2] = ItemStack(Items.DIAMOND_CHESTPLATE).apply {
            addMaxEnchantment(Enchantments.PROTECTION)
            addMaxEnchantment(Enchantments.UNBREAKING)
            addMaxEnchantment(Enchantments.MENDING)
        }

        inventory.armorInventory[1] = ItemStack(Items.DIAMOND_LEGGINGS).apply {
            addMaxEnchantment(Enchantments.BLAST_PROTECTION)
            addMaxEnchantment(Enchantments.UNBREAKING)
            addMaxEnchantment(Enchantments.MENDING)
        }

        inventory.armorInventory[0] = ItemStack(Items.DIAMOND_BOOTS).apply {
            addMaxEnchantment(Enchantments.PROTECTION)
            addMaxEnchantment(Enchantments.FEATHER_FALLING)
            addMaxEnchantment(Enchantments.DEPTH_STRIDER)
            addMaxEnchantment(Enchantments.UNBREAKING)
            addMaxEnchantment(Enchantments.MENDING)
        }
    }

    private fun ItemStack.addMaxEnchantment(enchantment: Enchantment) {
        addEnchantment(enchantment, enchantment.maxLevel)
    }

    private fun EntityPlayer.addGappleEffects() {
        addPotionEffectForce(PotionEffect(MobEffects.REGENERATION, Int.MAX_VALUE, 1))
        addPotionEffectForce(PotionEffect(MobEffects.ABSORPTION, Int.MAX_VALUE, 3))
        addPotionEffectForce(PotionEffect(MobEffects.RESISTANCE, Int.MAX_VALUE, 0))
        addPotionEffectForce(PotionEffect(MobEffects.FIRE_RESISTANCE, Int.MAX_VALUE, 0))
    }

    private fun EntityPlayer.addPotionEffectForce(potionEffect: PotionEffect) {
        addPotionEffect(potionEffect)
        potionEffect.potion.applyAttributesModifiersToEntity(this, this.attributeMap, potionEffect.amplifier)
    }
}