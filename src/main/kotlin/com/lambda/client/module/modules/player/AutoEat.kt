package com.lambda.client.module.modules.player

import com.lambda.client.commons.extension.next
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.process.PauseProcess.pauseBaritone
import com.lambda.client.process.PauseProcess.unpauseBaritone
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.combat.CombatUtils.scaledHealth
import com.lambda.client.util.items.*
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import com.lambda.mixin.player.MixinPlayerControllerMP
import net.minecraft.init.Items
import net.minecraft.init.MobEffects
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemTool
import net.minecraft.network.play.client.CPacketPlayerTryUseItem
import net.minecraft.util.EnumHand
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.function.BiPredicate

/**
 * @see MixinPlayerControllerMP.onStoppedUsingItemMixin
 */
object AutoEat : Module(
    name = "AutoEat",
    description = "Automatically eat when hungry",
    category = Category.PLAYER
) {
    private val belowHunger by setting("Below Hunger", 15, 1..20, 1)
    private val belowHealth by setting("Below Health", 10, 1..20, 1, description = "When to eat a golden apple")
    private val eGapOnFire by setting("Fire Prot", false, description = "Eats an enchanted golden apple whilst on fire")
    private val eatBadFood by setting("Eat Bad Food", false)
    private val packetDelay by setting("Packet Delay", 20, 1..100, 1, description = "How many ticks delay between packets")
    private val pauseBaritone by setting("Pause Baritone", true)

    private var lastSlot = -1
    private val eatTimer = TickTimer(TimeUnit.TICKS)
    var eating = false

    enum class PreferredFood : BiPredicate<ItemStack, ItemFood> {
        GAP {
            override fun test(itemStack: ItemStack, item: ItemFood): Boolean {
                return item == Items.GOLDEN_APPLE && itemStack.metadata == 0
            }
        },
        EGAP {
            override fun test(itemStack: ItemStack, item: ItemFood): Boolean {
                return item == Items.GOLDEN_APPLE && itemStack.metadata == 1
            }
        },
        NORMAL {
            override fun test(itemStack: ItemStack, item: ItemFood): Boolean {
                return item != Items.GOLDEN_APPLE
            }
        }
    }

    override fun isActive(): Boolean {
        return isEnabled && eating
    }

    init {
        onDisable {
            stopEating()
            swapBack()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            if (!player.isEntityAlive) {
                if (eating) stopEating()
                return@safeListener
            }

            val preferredFood = when {
                needFireProtect() -> PreferredFood.EGAP
                player.scaledHealth < belowHealth -> PreferredFood.GAP
                else -> PreferredFood.NORMAL
            }

            if (shouldEat(preferredFood)) {
                when {
                    isValidAndPreferred(player.heldItemOffhand, preferredFood) -> eat(EnumHand.OFF_HAND)
                    isValidAndPreferred(player.heldItemMainhand, preferredFood) -> eat(EnumHand.MAIN_HAND)
                    swapToFood(preferredFood) -> {
                        when {
                            isValidAndPreferredRecursive(player.heldItemOffhand, preferredFood) -> eat(EnumHand.OFF_HAND)
                            isValidAndPreferredRecursive(player.heldItemMainhand, preferredFood) -> eat(EnumHand.MAIN_HAND)
                        }
                    }
                }
            } else {
                if (eating) {
                    stopEating()
                } else {
                    swapBack()
                }
            }
        }
    }

    private fun SafeClientEvent.needFireProtect() =
        eGapOnFire
            && player.isBurning
            && !player.isPotionActive(MobEffects.FIRE_RESISTANCE)
            && player.allSlots.firstItem(Items.GOLDEN_APPLE) { it.metadata == 1 } != null

    private fun SafeClientEvent.shouldEat(preferredFood: PreferredFood) =
        player.foodStats.foodLevel < belowHunger
            || preferredFood != PreferredFood.NORMAL

    private fun SafeClientEvent.eat(hand: EnumHand) {
        if (eatTimer.tick(packetDelay)) {
            connection.sendPacket(CPacketPlayerTryUseItem(hand))
        }
        startEating()
    }

    private fun startEating() {
        if (pauseBaritone) pauseBaritone()
        eating = true
    }

    private fun stopEating() {
        unpauseBaritone()

        eating = false
    }

    private fun swapBack() {
        val slot = lastSlot
        if (slot == -1) return

        lastSlot = -1
        runSafe {
            swapToSlot(slot)
        }
    }

    /**
     * @return `true` if food found and moved
     */
    private fun SafeClientEvent.swapToFood(preferredFood: PreferredFood): Boolean {
        lastSlot = player.inventory.currentItem
        val slotToSwitchTo = getFoodSlot(preferredFood, player.hotbarSlots)?.let {
            swapToSlot(it as HotbarSlot)
            true
        } ?: false

        return if (slotToSwitchTo) {
            true
        } else {
            lastSlot = -1
            moveFoodToHotbar(preferredFood)
        }
    }

    /**
     * @return `true` if food found and moved
     */
    private fun SafeClientEvent.moveFoodToHotbar(preferredFood: PreferredFood): Boolean {
        val slotFrom = getFoodSlot(preferredFood, player.storageSlots) ?: return false

        moveToHotbar(this@AutoEat, slotFrom) {
            val item = it.item
            item !is ItemTool && item !is ItemBlock
        }

        return true
    }

    private fun SafeClientEvent.getFoodSlot(preferredFood: PreferredFood, inventory: List<Slot>, attempts: Int = 3): Slot? {
        return inventory.firstItem<ItemFood, Slot> {
            isValidAndPreferred(it, preferredFood)
        } ?: if (attempts > 1) {
            getFoodSlot(preferredFood.next(), inventory, attempts - 1)
        } else {
            null
        }
    }

    private fun SafeClientEvent.isValidAndPreferredRecursive(itemStack: ItemStack, preferredFood: PreferredFood): Boolean {
        val item = itemStack.item

        return item is ItemFood
            && isValid(itemStack, item)
            && isPreferredRecursive(itemStack, item, preferredFood)
    }

    private fun SafeClientEvent.isPreferredRecursive(itemStack: ItemStack, item: ItemFood, preferredFood: PreferredFood, attempts: Int = 3): Boolean {
        return preferredFood.test(itemStack, item)
            || isPreferredRecursive(itemStack, item, preferredFood.next(), attempts - 1)
    }

    private fun SafeClientEvent.isValidAndPreferred(itemStack: ItemStack, preferredFood: PreferredFood): Boolean {
        val item = itemStack.item

        return item is ItemFood
            && isValid(itemStack, item)
            && preferredFood.test(itemStack, item)
    }

    private fun SafeClientEvent.isValid(itemStack: ItemStack, item: ItemFood): Boolean {
        return item != Items.CHORUS_FRUIT
            && (eatBadFood || !isBadFood(itemStack, item))
            && player.canEat(item == Items.GOLDEN_APPLE)
    }

    private fun isBadFood(itemStack: ItemStack, item: ItemFood) =
        item == Items.ROTTEN_FLESH
            || item == Items.SPIDER_EYE
            || item == Items.POISONOUS_POTATO
            || item == Items.FISH && (itemStack.metadata == 3 || itemStack.metadata == 2) // Puffer fish, Clown fish
}