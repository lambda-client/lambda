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
import com.lambda.config.automation.AutomationConfig
import com.lambda.context.Automated
import com.lambda.interaction.container.ContainerType
import com.lambda.interaction.container.PlacedContainer
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.containerSelection
import com.lambda.interaction.container.selection.stackSelection
import com.lambda.interaction.handler.handlers.ContainerSearchScope
import com.lambda.task.RootTask
import com.lambda.task.Task
import com.lambda.task.start
import com.lambda.task.tasks.openContainer
import com.lambda.task.tasks.transfer
import com.lambda.task.tasks.wrappers.thenAction
import com.lambda.threading.runSafe
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.client.gui.screen.world.WorldCreator
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos

/**
 * In-game replication of the nested-shulker desync: pick up a shulker box, move it into a chest
 * on another screen, then pick up an *identical* shulker box into the *same* slot.
 *
 * The second pickup is new information from the server and must show up client-side. It used to
 * be dropped: the applied first pickup was re-recorded as a phantom prediction (the snapshot was
 * never refreshed), and moving the first shulker away happened on the chest screen, so the
 * phantom survived under the player screen. The identical second shulker then matched the phantom
 * and its update was canceled, so the shulker never appeared and the task chain failed looking
 * for it.
 *
 * A plain chest stands in for the ender chest here: what matters for the bug is only that the
 * move-away happens on a different screen (different sync id) than the pickups.
 */
@Suppress("UnstableApiUsage")
object ShulkerPickupGameTest : FabricClientGameTest {
    private const val PLAYER = "Steve"
    private const val TRANSFER_TIMEOUT = 20 * 40
    private const val OPEN_TIMEOUT = 20 * 10
    private const val PICKUP_TIMEOUT = 100

    /** hotbar.0, i.e. mainStacks[0]: the only free slot, so both pickups land here. */
    private const val SLOT = 0

    private const val SHULKER_NBT =
        "minecraft:white_shulker_box[container=[{slot:0,item:{id:\"minecraft:ender_chest\",count:1}}]]"

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
            LOG.info("Shulker pickup game test passed")
        } else {
            LOG.error("Shulker pickup game test failed:")
            failures.forEach { LOG.error("  FAIL $it") }
        }
        check(failures.isEmpty()) {
            "${failures.size} shulker pickup expectations failed:\n" + failures.joinToString("\n") { "  - $it" }
        }
    }

    private class Outcome(val state: Task.State, val error: String?, val ticks: Int) {
        val succeeded get() = state == Task.State.Completed
    }

    private fun runScenario(context: ClientGameTestContext, server: TestServerContext) {
        val auto: Automated = AutomationConfig.DEFAULT
        val center = BlockPos(0, -60, 0)
        val chestPos = BlockPos(2, -60, 0)

        prepareWorld(context, server, center)
        server.runCommand("/clear $PLAYER")
        server.runCommand("/kill @e[type=item]")
        server.runCommand("/setblock ${chestPos.x} ${chestPos.y} ${chestPos.z} minecraft:chest[facing=west]")

        // Every player slot except hotbar.0 holds junk, so both shulkers land in the same slot.
        repeat(8) { server.runCommand("/item replace entity $PLAYER hotbar.${it + 1} with minecraft:stone 64") }
        repeat(27) { server.runCommand("/item replace entity $PLAYER inventory.$it with minecraft:stone 64") }

        // Let Lambda learn the chest the way it does in normal play: open it once, close it again.
        // closeHandledScreen (not closeScreen): the server must be told, or it keeps the chest open
        // and every later server update carries the stale sync id and is ignored client-side.
        val open = runTask(context, OPEN_TIMEOUT) {
            with(auto) { openContainer(chestPos) }.thenAction { player.closeHandledScreen() }
        }
        if (!open.succeeded) {
            fail("could not open the chest at $chestPos: ${open.state} ${open.error}")
            return
        }
        context.waitFor({ it.currentScreen == null }, OPEN_TIMEOUT)
        context.waitTicks(2)

        // First shulker arrives server-side (same packet path as an item-entity pickup).
        // The transfer below reads the client inventory, so wait until the client actually shows
        // it: a wait only cures lateness, it can never mask a canceled update (those never arrive).
        server.runCommand("/give $PLAYER $SHULKER_NBT 1")
        waitForClientSlot(context, "first shulker")
        context.waitTicks(3)
        val first = serverPlayerStacks(server)[SLOT]
        if (first.item != Items.WHITE_SHULKER_BOX) {
            fail("setup broke: hotbar.0 holds $first instead of the first shulker")
            return
        }

        // Move it into the chest through the real transfer task: real clicks on another screen,
        // real full inventory answers from the server.
        val chestSelection = containerSelection(ContainerSearchScope.All) {
            ofAnyType(ContainerType.Chest)
            predicate { it is PlacedContainer && it.pos == chestPos }
        }
        val moved = runTask(context, TRANSFER_TIMEOUT) {
            with(auto) {
                transfer(
                    stackSelection { isItem(Items.WHITE_SHULKER_BOX) },
                    ContainerSelection.HOTBAR_AND_INVENTORY,
                    chestSelection
                )
            }
        }
        if (!moved.succeeded) {
            val seen = context.computeOnClient<List<ItemStack>, RuntimeException> {
                it.player!!.inventory.mainStacks.take(9).map { s -> s.copy() }
            }
            fail("could not move the first shulker into the chest: ${moved.state} ${moved.error} (client hotbar shows $seen)")
            return
        }
        context.waitFor({ it.currentScreen == null }, OPEN_TIMEOUT)
        context.waitTicks(3)

        val chestHasIt = chestStacks(server, chestPos).any { it.item == Items.WHITE_SHULKER_BOX }
        val slotFreed = serverPlayerStacks(server)[SLOT].isEmpty
        if (!chestHasIt || !slotFreed) {
            fail("setup broke: after the move the chest holds a shulker=$chestHasIt and hotbar.0 is empty=$slotFreed")
            return
        }

        // Second, identical shulker arrives into the same slot. This is the step that used to die:
        // its update matched the phantom left behind by the first pickup and was canceled.
        server.runCommand("/give $PLAYER $SHULKER_NBT 1")
        waitForClientSlot(context, "second shulker")
        context.waitTicks(5)

        // The client must agree with the server everywhere, especially on that slot.
        val serverStacks = serverPlayerStacks(server)
        val clientStacks = clientPlayerStacks(context)
        val desynced = clientStacks.indices.filter { !ItemStack.areEqual(clientStacks[it], serverStacks[it]) }
        if (desynced.isNotEmpty()) {
            fail(
                "client and server disagree on player slots $desynced: " +
                    "client=${desynced.map { clientStacks[it] }} server=${desynced.map { serverStacks[it] }}"
            )
        }
        if (serverStacks[SLOT].item != Items.WHITE_SHULKER_BOX) {
            fail("hotbar.0 holds ${serverStacks[SLOT]} server-side instead of the second shulker")
        }
        val chestShulkers = chestStacks(server, chestPos).count { it.item == Items.WHITE_SHULKER_BOX }
        val playerShulkers = serverStacks.count { it.item == Items.WHITE_SHULKER_BOX }
        if (chestShulkers != 1 || playerShulkers != 1) {
            fail("shulker conservation broke: chest=$chestShulkers player=$playerShulkers, expected 1 and 1")
        }
    }

    private fun runTask(
        context: ClientGameTestContext,
        timeout: Int,
        build: () -> Task<*>
    ): Outcome {
        var task: Task<*>? = null
        var error: String? = null
        context.runOnClient<RuntimeException> {
            runSafe {
                task = build().onFailure { error = it.message }.start()
            }
        }
        val started = task ?: return Outcome(Task.State.Failed, "no safe context to start the task", 0)
        val ticks = try {
            context.waitFor({
                started.state != Task.State.Running && started.state != Task.State.Paused
            }, timeout)
        } catch (_: Throwable) {
            timeout
        }
        return Outcome(started.state, error, ticks)
    }

    private fun waitForClientSlot(context: ClientGameTestContext, what: String) {
        try {
            context.waitFor({ mc ->
                mc.player?.inventory?.mainStacks?.getOrNull(SLOT)?.let {
                    !it.isEmpty && it.item == Items.WHITE_SHULKER_BOX
                } == true
            }, PICKUP_TIMEOUT)
        } catch (_: Throwable) {
            fail("the $what never appeared in hotbar.0 within $PICKUP_TIMEOUT ticks; ${dumpState(context)}")
        }
    }

    private fun dumpState(context: ClientGameTestContext): String {
        val client = runCatching { clientPlayerStacks(context).take(9) }.getOrNull() ?: listOf("n/a")
        return "client hotbar=$client"
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

    private fun serverPlayerStacks(server: TestServerContext): List<ItemStack> =
        server.computeOnServer<List<ItemStack>, RuntimeException> { mc ->
            mc.playerManager.playerList.first { it.gameProfile.name == PLAYER }
                .inventory.mainStacks.map { it.copy() }
        }

    private fun clientPlayerStacks(context: ClientGameTestContext): List<ItemStack> =
        context.computeOnClient<List<ItemStack>, RuntimeException> {
            it.player!!.inventory.mainStacks.map { s -> s.copy() }
        }

    private fun chestStacks(server: TestServerContext, pos: BlockPos): List<ItemStack> =
        server.computeOnServer<List<ItemStack>, RuntimeException> { mc ->
            val entity = mc.overworld.getBlockEntity(pos) as? ChestBlockEntity
            if (entity == null) emptyList()
            else (0 until entity.size()).map { entity.getStack(it).copy() }
        }

    private fun fail(message: String) {
        failures += message
        LOG.warn("FAIL [shulker-pickup] $message")
    }
}
