package com.lambda.client.module.modules.combat

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.manager.managers.HotbarManager.resetHotbar
import com.lambda.client.manager.managers.HotbarManager.spoofHotbar
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.Bind
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.combat.SurroundUtils
import com.lambda.client.util.items.HotbarSlot
import com.lambda.client.util.items.firstBlock
import com.lambda.client.util.items.hotbarSlots
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.isActiveOrFalse
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.buildStructure
import com.lambda.client.util.world.isPlaceable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.InputEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard

@CombatManager.CombatModule
object AutoTrap : Module(
    name = "AutoTrap",
    description = "Traps your enemies in obsidian",
    category = Category.COMBAT,
    modulePriority = 60
) {
    private val trapMode by setting("Trap Mode", TrapMode.FULL_TRAP)
    private val selfTrap = setting("Self Trap", false)
    private val bindSelfTrap by setting("Bind Self Trap", Bind())
    private val autoDisable by setting("Auto Disable", true)
    private val strictDirection by setting("Strict Direction", false)
    private val placeSpeed by setting("Places Per Tick", 4f, 0.25f..5f, 0.25f)

    private var job: Job? = null

    override fun isActive(): Boolean {
        return isEnabled && job.isActiveOrFalse
    }

    init {
        onDisable {
            resetHotbar()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (!job.isActiveOrFalse && canRun()) job = runAutoTrap()

            if (job.isActiveOrFalse) {
                getObby()?.let {
                    spoofHotbar(it)
                } ?: return@safeListener
                sendPlayerPacket {
                    cancelRotate()
                }
            } else if (CombatManager.isOnTopPriority(AutoTrap)) {
                resetHotbar()
            }
        }

        listener<InputEvent.KeyInputEvent> {
            if (bindSelfTrap.isDown(Keyboard.getEventKey())) {
                selfTrap.value = !selfTrap.value
            }
        }
    }

    private fun SafeClientEvent.canRun(): Boolean {
        (if (selfTrap.value) player else CombatManager.target)?.positionVector?.toBlockPos()?.let {
            for (offset in trapMode.offset) {
                if (!world.isPlaceable(it.add(offset))) continue
                return true
            }
        }
        return false
    }

    private fun SafeClientEvent.getObby(): HotbarSlot? {
        val slots = player.hotbarSlots.firstBlock(Blocks.OBSIDIAN)

        if (slots == null) { // Obsidian check
            MessageSendHelper.sendChatMessage("$chatName No obsidian in hotbar, disabling!")
            disable()
            return null
        }

        return slots
    }

    private fun SafeClientEvent.runAutoTrap() = defaultScope.launch {
        val entity = if (selfTrap.value) player else CombatManager.target ?: return@launch

        buildStructure(
            entity.flooredPosition,
            SurroundUtils.surroundOffset,
            placeSpeed,
            3,
            4.25f,
            strictDirection
        ) {
            isEnabled && CombatManager.isOnTopPriority(AutoTrap)
        }

        if (autoDisable) disable()
    }

    @Suppress("UNUSED")
    private enum class TrapMode(val offset: Array<BlockPos>) {
        FULL_TRAP(arrayOf(
            BlockPos(1, 0, 0),
            BlockPos(-1, 0, 0),
            BlockPos(0, 0, 1),
            BlockPos(0, 0, -1),
            BlockPos(1, 1, 0),
            BlockPos(-1, 1, 0),
            BlockPos(0, 1, 1),
            BlockPos(0, 1, -1),
            BlockPos(0, 2, 0)
        )),
        CRYSTAL_TRAP(arrayOf(
            BlockPos(1, 1, 1),
            BlockPos(1, 1, 0),
            BlockPos(1, 1, -1),
            BlockPos(0, 1, -1),
            BlockPos(-1, 1, -1),
            BlockPos(-1, 1, 0),
            BlockPos(-1, 1, 1),
            BlockPos(0, 1, 1),
            BlockPos(0, 2, 0)
        ))
    }
}