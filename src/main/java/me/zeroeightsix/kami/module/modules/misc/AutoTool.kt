package me.zeroeightsix.kami.module.modules.misc

import me.zeroeightsix.kami.event.SafeClientEvent
import me.zeroeightsix.kami.event.events.PlayerAttackEvent
import me.zeroeightsix.kami.mixin.extension.syncCurrentPlayItem
import me.zeroeightsix.kami.module.Category
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.util.combat.CombatUtils
import me.zeroeightsix.kami.util.combat.CombatUtils.equipBestWeapon
import me.zeroeightsix.kami.util.items.hotbarSlots
import me.zeroeightsix.kami.util.items.swapToSlot
import me.zeroeightsix.kami.util.threads.safeListener
import net.minecraft.block.state.IBlockState
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.EntityLivingBase
import net.minecraft.init.Enchantments
import net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Mouse
import kotlin.math.pow

internal object AutoTool : Module(
    name = "AutoTool",
    description = "Automatically switch to the best tools when mining or attacking",
    category = Category.MISC
) {
    private val switchBack = setting("SwitchBack", true)
    private val timeout = setting("Timeout", 20, 1..100, 5, { switchBack.value })
    private val swapWeapon = setting("SwitchWeapon", false)
    private val preferWeapon = setting("Prefer", CombatUtils.PreferWeapon.SWORD)

    private var shouldMoveBack = false
    private var lastSlot = 0
    private var lastChange = 0L

    init {
        safeListener<LeftClickBlock> {
            if (shouldMoveBack || !switchBack.value) equipBestTool(world.getBlockState(it.pos))
        }

        safeListener<PlayerAttackEvent> {
            if (swapWeapon.value && it.entity is EntityLivingBase) equipBestWeapon(preferWeapon.value)
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (mc.currentScreen != null || !switchBack.value) return@safeListener

            val mouse = Mouse.isButtonDown(0)
            if (mouse && !shouldMoveBack) {
                lastChange = System.currentTimeMillis()
                shouldMoveBack = true
                lastSlot = player.inventory.currentItem
                playerController.syncCurrentPlayItem()
            } else if (!mouse && shouldMoveBack && (lastChange + timeout.value * 10 < System.currentTimeMillis())) {
                shouldMoveBack = false
                player.inventory.currentItem = lastSlot
                playerController.syncCurrentPlayItem()
            }
        }
    }

    private fun SafeClientEvent.equipBestTool(blockState: IBlockState) {
        player.hotbarSlots.maxByOrNull {
            val stack = it.stack
            if (stack.isEmpty) {
                0.0f
            } else {
                var speed = stack.getDestroySpeed(blockState)

                if (speed > 1.0f) {
                    val efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantments.EFFICIENCY, stack)
                    if (efficiency > 0) {
                        speed += efficiency * efficiency + 1.0f
                    }
                }

                speed
            }
        }?.let {
            swapToSlot(it)
        }
    }

    init {
        switchBack.valueListeners.add { _, it ->
            if (!it) shouldMoveBack = false
        }
    }
}