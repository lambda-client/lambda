/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

@file:Suppress("unused")

package com.lambda

import com.lambda.Lambda.LOG
import com.lambda.Lambda.mc
import com.lambda.config.automation.AutomationConfig
import com.lambda.context.Automated
import com.lambda.interaction.container.SlotIdentity
import com.lambda.interaction.container.identity
import com.lambda.task.RootTask
import com.lambda.task.Task
import com.lambda.task.start
import com.lambda.task.tasks.openContainer
import com.lambda.threading.runSafe
import com.lambda.util.extension.containerSlots
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.gui.screen.world.WorldCreator
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos

/**
 * Pins down [SlotIdentity]: slots match across reopens without syncId.
 *
 * 1. A container slot recorded while a chest is open resolves to the same logical
 *    slot (index + contents) after close + reopen, even though the syncId changed.
 * 2. While the chest is closed the identity does not resolve (anchor gone).
 * 3. A player-inventory slot resolves identically with and without a screen open.
 */
@Suppress("UnstableApiUsage")
object SlotIdentityGameTest : FabricClientGameTest {
    private const val PLAYER = "Steve"
    private const val OPEN_TIMEOUT = 20 * 10

    private data class Snap(
        val identity: SlotIdentity?,
        val syncId: Int,
        val index: Int,
        val itemId: String
    )

    private val failures = mutableListOf<String>()

    override fun runTest(context: ClientGameTestContext) {
        failures.clear()

        val singleplayer = context.worldBuilder()
            .adjustSettings { it.gameMode = WorldCreator.Mode.SURVIVAL }
            .create()

        try {
            singleplayer.clientWorld.waitForChunksDownload()
            runScenario(context, singleplayer.server)
        } finally {
            runCatching { context.runOnClient<RuntimeException> { RootTask.cancel() } }
            runCatching { singleplayer.close() }
        }

        if (failures.isEmpty()) {
            LOG.info("Slot identity game test passed")
        } else {
            LOG.error("Slot identity game test failed:")
            failures.forEach { LOG.error("  FAIL $it") }
        }
        check(failures.isEmpty()) {
            "${failures.size} slot identity expectations failed:\n" + failures.joinToString("\n") { "  - $it" }
        }
    }

    private fun runScenario(context: ClientGameTestContext, server: TestServerContext) {
        val auto: Automated = AutomationConfig.DEFAULT
        val center = BlockPos(0, -60, 0)
        val chestPos = BlockPos(2, -60, 0)

        prepareWorld(context, server, center)
        server.runCommand("/clear $PLAYER")
        server.runCommand("/item replace entity $PLAYER hotbar.0 with minecraft:stone 64")
        server.runCommand("/setblock ${chestPos.x} ${chestPos.y} ${chestPos.z} minecraft:chest[facing=west]")
        server.runCommand("/item replace block ${chestPos.x} ${chestPos.y} ${chestPos.z} container.0 with minecraft:dirt 64")

        // Player-half identity resolves with no screen open.
        var hotbarSnap: Snap? = null
        context.runOnClient<RuntimeException> {
            val handler = mc.player?.currentScreenHandler
            val slot = handler?.slots?.firstOrNull { it.inventory is PlayerInventory && it.index == 0 }
            if (handler != null && slot != null) {
                hotbarSnap = Snap(slot.identity(), handler.syncId, slot.index, Registries.ITEM.getId(slot.stack.item).toString())
            }
        }
        val hotbar = hotbarSnap
        if (hotbar == null || hotbar.identity == null) {
            fail("could not record the player hotbar slot identity")
            return
        }

        openChest(context, auto, chestPos) ?: return
        context.waitTicks(5)

        // 3. The player slot still resolves with a container screen open.
        var playerOpen: Pair<Int, String>? = null
        context.runOnClient<RuntimeException> {
            playerOpen = runSafe {
                hotbar.identity.resolve()?.let { it.index to Registries.ITEM.getId(it.stack.item).toString() }
            }
        }
        if (playerOpen?.first != 0 || playerOpen?.second != "minecraft:stone") {
            fail("player slot identity should resolve with a screen open but got $playerOpen")
        }

        var first: Snap? = null
        context.runOnClient<RuntimeException> {
            val handler = mc.player?.currentScreenHandler
            val slot = handler?.containerSlots?.firstOrNull()
            if (handler != null && slot != null) {
                first = Snap(slot.identity(), handler.syncId, slot.index, Registries.ITEM.getId(slot.stack.item).toString())
            }
        }
        val recorded = first
        if (recorded == null || recorded.identity == null) {
            fail("could not record the chest slot identity")
            return
        }
        if (recorded.index != 0 || recorded.itemId != "minecraft:dirt") {
            fail("expected chest slot 0 with dirt but got $recorded")
        }

        context.runOnClient<RuntimeException> {
            runSafe { player.closeHandledScreen() }
        }
        try {
            context.waitFor({ it.currentScreen == null }, OPEN_TIMEOUT)
        } catch (_: Throwable) {
            fail("chest screen did not close")
            return
        }

        // 2. Closed container: the identity suspends instead of resolving.
        val closed = context.computeOnClient<Boolean, RuntimeException> {
            runSafe { recorded.identity.resolve() != null } ?: false
        }
        if (closed) {
            fail("chest slot identity should not resolve while the chest is closed")
        }

        openChest(context, auto, chestPos) ?: return
        context.waitTicks(5)

        val secondSync = context.computeOnClient<Int, RuntimeException> {
            mc.player?.currentScreenHandler?.syncId ?: 0
        }
        if (secondSync == recorded.syncId) {
            fail("reopen should mint a fresh syncId but got ${recorded.syncId} twice")
        }

        // 1. Same logical slot across the reopen, syncId be damned.
        var reopened: Pair<Int, String>? = null
        context.runOnClient<RuntimeException> {
            reopened = runSafe {
                recorded.identity.resolve()?.let { it.index to Registries.ITEM.getId(it.stack.item).toString() }
            }
        }
        if (reopened?.first != 0 || reopened?.second != "minecraft:dirt") {
            fail("chest slot identity should resolve to slot 0 with dirt after reopen but got $reopened")
        }
    }

    private fun openChest(context: ClientGameTestContext, auto: Automated, chestPos: BlockPos): Task<*>? {
        var task: Task<*>? = null
        context.runOnClient<RuntimeException> {
            runSafe {
                task = with(auto) { openContainer(chestPos) }.start()
            }
        }
        val started = task
        if (started == null) {
            fail("could not start the chest open task")
            return null
        }
        try {
            context.waitFor({ it.player?.currentScreenHandler?.syncId != 0 }, OPEN_TIMEOUT)
        } catch (_: Throwable) {
            fail("chest did not open within $OPEN_TIMEOUT ticks")
            return null
        }
        return started
    }

    private fun prepareWorld(context: ClientGameTestContext, server: TestServerContext, center: BlockPos) {
        server.runCommand("/gamemode survival $PLAYER")
        server.runCommand("/difficulty peaceful")
        server.runCommand("/time set noon")
        server.runCommand("/weather clear")
        server.runCommand("/fill ${center.x - 5} ${center.y - 1} ${center.z - 5} ${center.x + 5} ${center.y - 1} ${center.z + 5} minecraft:stone")
        server.runCommand("/fill ${center.x - 5} ${center.y} ${center.z - 5} ${center.x + 5} ${center.y + 4} ${center.z + 5} minecraft:air")
        server.runCommand("/tp $PLAYER ${center.x + 0.5} ${center.y} ${center.z + 0.5} 0 0")
        context.waitTicks(10)
    }

    private fun fail(message: String) {
        failures += message
        LOG.warn("FAIL [slot-identity] $message")
    }
}
