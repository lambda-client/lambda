package com.lambda.client.manager.managers

import com.lambda.client.LambdaMod
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.elements.world.ChestCounter
import com.lambda.client.manager.Manager
import com.lambda.client.module.modules.render.StorageESP
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.safeListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.tileentity.TileEntityShulkerBox
import net.minecraftforge.fml.common.gameevent.TickEvent

object ChestCountManager : Manager {
    private const val searchDelayTicks = 5L
    private val delayTimer: TickTimer = TickTimer(TimeUnit.TICKS)
    var chestCount = 0
        private set
    var dubsCount = 0
        private set
    var shulkerCount = 0
        private set
    private var chestCountSearchJob: Job? = null

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@safeListener
            if (!ChestCounter.visible && !StorageESP.chestCountSetting) return@safeListener
            if (chestCountSearchJob?.isActive == true) return@safeListener
            if (delayTimer.tick(searchDelayTicks)) {
                chestCountSearchJob = defaultScope.launch {
                    searchLoadedTileEntities()
                    delayTimer.reset()
                }
            }
        }
    }

    private fun SafeClientEvent.searchLoadedTileEntities() {
        try {
            var dubsC = 0
            var chestC = 0
            var shulkC = 0
            for (tileEntity in world.loadedTileEntityList) {
                if (tileEntity is TileEntityChest) {
                    chestC++
                    if (tileEntity.adjacentChestXPos != null || tileEntity.adjacentChestZPos != null) dubsC++
                } else if (tileEntity is TileEntityShulkerBox) shulkC++
            }
            dubsCount = dubsC
            chestCount = chestC
            shulkerCount = shulkC
        } catch (e: Exception) {
            LambdaMod.LOG.error("ChestCounter: Error searching loaded tile entities", e)
        }
    }
}