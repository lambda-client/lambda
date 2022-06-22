package com.lambda.client.module.modules.combat

import com.lambda.client.commons.utils.MathUtils.reverseNumber
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.GuiEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.FriendManager
import com.lambda.client.manager.managers.PlayerPacketManager
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.isFakeOrSelf
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.items.clickSlot
import com.lambda.client.util.items.swapToSlot
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Enchantments
import net.minecraft.init.Items
import net.minecraft.inventory.ClickType
import net.minecraft.util.EnumHand
import net.minecraft.util.math.RayTraceResult
import net.minecraftforge.fml.common.gameevent.TickEvent

object AutoMend : Module(
    name = "AutoMend",
    description = "Automatically mends armour",
    category = Category.COMBAT
) {
    private val autoThrow by setting("Auto Throw", true)
    private val throwDelay = setting("Throw Delay", 2, 0..5, 1, description = "Number of ticks between throws to allow absorption", unit = " ticks")
    private val autoSwitch by setting("Auto Switch", true)
    private val autoDisableExp by setting("Auto Disable", false, { autoSwitch }, description = "Disable when you run out of XP bottles")
    private val autoDisableComplete by setting("Disable on Complete", false)
    private val takeOff by setting("Take Off", true)
    private val pauseAutoArmor = setting("Pause AutoArmor", true, { takeOff })
    private val cancelNearby by setting("Cancel Nearby", NearbyMode.OFF, description = "Don't mend when an enemy is nearby")
    private val pauseNearbyRadius by setting("Nearby Radius", 8, 1..8, 1, { cancelNearby != NearbyMode.OFF })
    private val threshold by setting("Repair At", 75, 1..100, 1, description = "Percentage to start repairing any armor piece")
    private val gui by setting("Allow GUI", false, description = "Allow mending when inside a GUI")

    private var initHotbarSlot = -1
    private var isGuiOpened = false
    private var pausedPending = false

    private val throwDelayTimer = TickTimer(TimeUnit.TICKS)

    @Suppress("unused")
    private enum class NearbyMode {
        OFF, PAUSE, DISABLE
    }

    init {
        onEnable {
            pausedPending = false
            if (autoSwitch) {
                runSafe {
                    initHotbarSlot = player.inventory.currentItem
                }
            }
        }

        onDisable {
            switchback()
            if (AutoArmor.isPaused && pauseAutoArmor.value) {
                AutoArmor.isPaused = false
            }
        }

        pauseAutoArmor.listeners.add {
            if (!pauseAutoArmor.value) {
                AutoArmor.isPaused = false
            }
        }

        listener<GuiEvent.Displayed> {
            isGuiOpened = it.screen != null
        }

        listener<GuiEvent.Closed> {
            isGuiOpened = false
        }

        safeListener<TickEvent.ClientTickEvent> {
            var isMending = false

            if (!isGuiOpened || gui) {
                if (cancelNearby != NearbyMode.OFF && isNearbyPlayer()) {
                    if (cancelNearby == NearbyMode.DISABLE) {
                        disable()
                    } else {
                        if (!pausedPending)
                            switchback()
                        pausedPending = true
                    }

                    return@safeListener
                }

                pausedPending = false

                // don't call twice in same tick so store in a var
                val shouldMend = shouldMend(0) || shouldMend(1) || shouldMend(2) || shouldMend(3)
                if (!shouldMend && autoDisableComplete) {
                    disable()
                }

                if ((autoSwitch || autoThrow) // avoid checking if no actions are going to be done
                    && hasBlockUnder() && shouldMend) {
                    if (autoSwitch && player.heldItemMainhand.item !== Items.EXPERIENCE_BOTTLE) {
                        val xpSlot = findXpPots()

                        if (xpSlot == -1) {
                            if (autoDisableExp) {
                                disable()
                            }

                            return@safeListener
                        }

                        player.inventory.currentItem = xpSlot
                    }

                    if (autoThrow && player.heldItemMainhand.item == Items.EXPERIENCE_BOTTLE) {
                        sendPlayerPacket {
                            rotate(Vec2f(player.rotationYaw, 90.0f))
                        }

                        isMending = true

                        if (validServerSideRotation() && throwDelayTimer.tick(throwDelay.value.toLong())) {
                            playerController.processRightClick(player, world, EnumHand.MAIN_HAND)
                        }
                    }
                }
            }

            if (pauseAutoArmor.value) {
                AutoArmor.isPaused = isMending

                if (takeOff && isMending) {
                    var minSlot = 9

                    for (i in 0..3) {
                        if (shouldMend(i) || !hasMending(i)) continue
                        val emptySlot = findEmptySlot(minSlot)
                        minSlot = emptySlot + 1

                        if (emptySlot == -1) break
                        clickSlot(this@AutoMend, player.inventoryContainer.windowId, 8 - i, 0, ClickType.PICKUP)
                        clickSlot(this@AutoMend, player.inventoryContainer.windowId, emptySlot, 0, ClickType.PICKUP)
                    }
                }
            }
        }
    }

    private fun SafeClientEvent.findEmptySlot(min: Int): Int {
        for (i in min..36) {
            if (player.inventory.getStackInSlot(i).isEmpty) {
                return i
            }
        }

        return -1
    }

    private fun SafeClientEvent.findXpPots(): Int {
        var slot = -1
        for (i in 0..8) {
            if (player.inventory.getStackInSlot(i).item == Items.EXPERIENCE_BOTTLE) {
                slot = i
                break
            }
        }
        return slot
    }

    private fun SafeClientEvent.hasMending(slot: Int): Boolean {
        val stack = player.inventory.armorInventory[slot]
        return EnchantmentHelper.getEnchantmentLevel(Enchantments.MENDING, stack) > 0
    }

    private fun SafeClientEvent.shouldMend(i: Int): Boolean { // (100 * damage / max damage) >= (100 - 70)
        val stack = player.inventory.armorInventory[i]
        return hasMending(i) && stack.isItemDamaged && 100 * stack.itemDamage / stack.maxDamage > reverseNumber(threshold, 1, 100)
    }

    private fun switchback() {
        if (autoSwitch) {
            runSafe {
                if (initHotbarSlot != -1 && initHotbarSlot != player.inventory.currentItem) {
                    swapToSlot(initHotbarSlot)
                }
            }
        }
    }

    private fun SafeClientEvent.isNearbyPlayer(): Boolean {
        for (entity in world.loadedEntityList) {
            if (entity !is EntityPlayer) continue
            if (entity.isFakeOrSelf) continue
            if (player.getDistance(entity) > pauseNearbyRadius) continue
            if (FriendManager.isFriend(entity.name)) continue
            return true
        }
        return false
    }

    private fun validServerSideRotation(): Boolean {
        val pitch = PlayerPacketManager.serverSideRotation.y
        return pitch in 80.0f..90.0f
    }

    private fun SafeClientEvent.hasBlockUnder(): Boolean {
        val posVec = player.positionVector
        val result = world.rayTraceBlocks(posVec, posVec.add(0.0, -5.33, 0.0), false, true, false)
        return result != null && result.typeOfHit == RayTraceResult.Type.BLOCK
    }
}
