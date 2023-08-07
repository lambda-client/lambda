package com.lambda.client.manager.managers

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
    private const val SEARCH_DELAY_TICKS = 5L
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
            if (!delayTimer.tick(SEARCH_DELAY_TICKS)) return@safeListener

            chestCountSearchJob = defaultScope.launch {
                searchLoadedTileEntities()
                delayTimer.reset()
            }
        }
    }

    private fun SafeClientEvent.searchLoadedTileEntities() {
        val chests = world.loadedTileEntityList.filterIsInstance<TileEntityChest>()

        dubsCount = chests.count { it.adjacentChestXPos != null || it.adjacentChestZPos != null }
        chestCount = chests.size
        shulkerCount = world.loadedTileEntityList.filterIsInstance<TileEntityShulkerBox>().size
    }
}