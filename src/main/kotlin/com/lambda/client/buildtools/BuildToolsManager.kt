package com.lambda.client.buildtools

import com.lambda.client.buildtools.Statistics.totalBlocksBroken
import com.lambda.client.buildtools.Statistics.totalBlocksPlaced
import com.lambda.client.buildtools.Statistics.updateStatistics
import com.lambda.client.buildtools.blueprint.StructureTask
import com.lambda.client.buildtools.pathfinding.BaritoneHelper
import com.lambda.client.buildtools.task.RestockHandler.handleRestock
import com.lambda.client.buildtools.task.TaskFactory.populateTasks
import com.lambda.client.buildtools.task.TaskProcessor
import com.lambda.client.buildtools.task.TaskProcessor.doTickOnTasks
import com.lambda.client.commons.extension.firstEntryOrNull
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.AbstractModule
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.disableMode
import com.lambda.client.module.modules.client.BuildTools.leastFood
import com.lambda.client.module.modules.client.BuildTools.manageFood
import com.lambda.client.module.modules.client.BuildTools.proxyCommand
import com.lambda.client.module.modules.client.BuildTools.storageManagement
import com.lambda.client.module.modules.client.BuildTools.usingProxy
import com.lambda.client.module.modules.combat.AutoLog
import com.lambda.client.module.modules.misc.AntiAFK
import com.lambda.client.module.modules.misc.AutoObsidian
import com.lambda.client.module.modules.movement.AntiHunger
import com.lambda.client.module.modules.player.AutoEat
import com.lambda.client.module.modules.player.LagNotifier
import com.lambda.client.module.modules.player.NoGhostItems
import com.lambda.client.process.PauseProcess
import com.lambda.client.util.items.countItem
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.onMainThreadSafe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.init.SoundEvents
import net.minecraft.item.ItemFood
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString
import net.minecraft.util.text.TextFormatting
import net.minecraft.world.EnumDifficulty
import java.util.*

object BuildToolsManager {
    private val structureTasks = TreeMap<AbstractModule, MutableList<StructureTask>>(compareByDescending { it.modulePriority })

    fun AbstractModule.buildStructure(vararg structureTask: StructureTask) {
        structureTasks[this] = structureTask.toMutableList()
    }

    fun SafeClientEvent.doTickOnStructure() {
        structureTasks.firstEntryOrNull()?.let { (module, task) ->
            /* all structures are finished */
            if (task.isEmpty()) {
                structureTasks.remove(module)
                return
            }

            task.firstOrNull()?.let { structureTask ->
                if (!structureTask.inProgress) {
                    TaskProcessor.reset()
                    structureTask.inProgress = true
                    populateTasks(structureTask)
                }

                if (TaskProcessor.isDone()) {
                    if (structureTask.blueprintStrategy.isDone()) {
                        task.remove(structureTask)
                        BaritoneHelper.resetBaritone()
                        Statistics.printFinishStats(structureTask.blueprintStrategy.getFinishMessage())
                        return
                    } else {
                        structureTask.blueprint = structureTask.blueprintStrategy.getNext(structureTask.blueprint)

                        populateTasks(structureTask)
                    }
                }

                if (!shouldPause()) {
                    updateStatistics()

                    /* Fulfill basic needs */
                    if (storageManagement
                        && manageFood
                        && player.inventorySlots.countItem<ItemFood>() <= leastFood
                    ) {
                        handleRestock<ItemFood>()
                        return
                    }

                    doTickOnTasks(structureTask)
                }
            }
        }
    }

    private fun SafeClientEvent.shouldPause(): Boolean =
        !PacketReceiver.rubberbandTimer.tick(BuildTools.rubberbandTimeout.toLong(), false)
            || player.inventory.isEmpty
            || PauseProcess.isActive
            || AutoObsidian.isActive()
            || isInQueue()
            || !player.isEntityAlive

    private fun SafeClientEvent.isInQueue() =
        world.difficulty == EnumDifficulty.PEACEFUL
            && player.dimension == 1
            && @Suppress("UNNECESSARY_SAFE_CALL")
        player.serverBrand?.contains("2b2t") == true

    fun isActive() = structureTasks.isNotEmpty() || !TaskProcessor.isDone()

    fun printSettingsAndInfo() {
        StringBuilder(BuildTools.ignoreBlocks.size + 1).run {
            append("${BuildTools.chatName} Settings" +
                "\n §9> §rIgnored Blocks:")

            BuildTools.ignoreBlocks.forEach {
                append("\n     §9> §7$it")
            }

            append("\n    §9> §7Placed blocks: §a%,d§r".format(totalBlocksPlaced) +
                "\n    §9> §7Destroyed blocks: §a%,d§r".format(totalBlocksBroken))

            MessageSendHelper.sendRawChatMessage(toString())
        }
    }

    private fun SafeClientEvent.printWarnings() {
        if (AntiHunger.isEnabled) {
            MessageSendHelper.sendRawChatMessage("    §c[!] AntiHunger slows down mining speed.")
        }

        if (LagNotifier.isDisabled) {
            MessageSendHelper.sendRawChatMessage("    §c[!] You should activate LagNotifier to make ${BuildTools.name} stop on server lag.")
        }

        if (AutoEat.isDisabled) {
            MessageSendHelper.sendRawChatMessage("    §c[!] You should activate AutoEat to not die on starvation.")
        }

        if (AutoLog.isDisabled) {
            MessageSendHelper.sendRawChatMessage("    §c[!] You should activate AutoLog to prevent most deaths when afk.")
        }

        if (isInQueue()) {
            MessageSendHelper.sendRawChatMessage("    §c[!] You should not activate ${BuildTools.name} in queue.")
        }

        if (NoGhostItems.isDisabled) {
            MessageSendHelper.sendRawChatMessage("    §c[!] Please consider using the module NoGhostItems to minimize item desyncs")
        }
    }

    fun SafeClientEvent.disableError(error: String) {
        MessageSendHelper.sendErrorMessage("${BuildTools.chatName} §c[!] $error")
        mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
        // ToDo: cancel structure
        when (disableMode) {
            BuildTools.DisableMode.ANTI_AFK -> {
                MessageSendHelper.sendWarningMessage("${BuildTools.chatName} §c[!] ${TextFormatting.AQUA}Going into AFK mode.")
                AntiAFK.enable()
            }
            BuildTools.DisableMode.LOGOUT -> {
                MessageSendHelper.sendWarningMessage("${BuildTools.chatName} §c[!] ${TextFormatting.DARK_RED}CAUTION: Logging off in 1 minute!")
                defaultScope.launch {
                    delay(6000L)
                    if (disableMode == BuildTools.DisableMode.LOGOUT && BuildTools.isDisabled) {
                        onMainThreadSafe {
                            if (usingProxy) {
                                player.sendChatMessage(proxyCommand)
                            } else {
                                connection.networkManager.closeChannel(TextComponentString("Done building."))
                            }
                        }
                    } else {
                        MessageSendHelper.sendChatMessage("${BuildTools.chatName} ${TextFormatting.GREEN}Logout canceled.")
                    }
                }
            }
            BuildTools.DisableMode.NONE -> {
                // Nothing
            }
        }
    }
}