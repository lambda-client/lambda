package com.lambda.client.module.modules.player

import com.lambda.client.event.events.WindowClickEvent
import com.lambda.client.manager.managers.PlayerInventoryManager
import com.lambda.client.manager.managers.PlayerInventoryManager.addInventoryTask
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import com.lambda.mixin.player.MixinPlayerControllerMP

/**
 * @see MixinPlayerControllerMP.onWindowClick
 * @see PlayerInventoryManager
 */
object NoGhostItems : Module(
    name = "NoGhostItems",
    description = "Syncs inventory interactions for strict environments",
    category = Category.PLAYER
) {
    val syncMode by setting("Scope", SyncMode.MODULES)
    val baritoneSync by setting("Baritone pause", true, description = "Pauses Baritone until transaction is complete.")
    val timeout by setting("Timeout in ms", 800, 1..2500, 25)
    val maxRetries by setting("Max retries", 8, 0..20, 1)
    val debugLog by setting("Debug log", false)
    private val clearQueue = setting("Clear Transaction Queue", false)

    enum class SyncMode {
        ALL, PLAYER, MODULES
    }

    init {
        safeListener<WindowClickEvent> {
            if (syncMode == SyncMode.MODULES) return@safeListener

            addInventoryTask(PlayerInventoryManager.ClickInfo(it.windowId, it.slotId, it.mouseButton, it.type))
            it.cancel()
        }

        clearQueue.consumers.add { _, it ->
            if (it) PlayerInventoryManager.reset()
            false
        }
    }
}
