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
import com.lambda.task.RootTask
import com.lambda.task.Task
import com.lambda.task.start
import com.lambda.task.tasks.PickedUpItem
import com.lambda.task.tasks.collectDrops
import com.lambda.threading.runSafe
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.gui.screen.world.WorldCreator
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos

/**
 * In-game coverage for [com.lambda.task.tasks.CollectDropsTask]'s pickup report.
 *
 * Drops are summoned with a pickup delay so entity references can be captured before
 * anything is collected, then the real task walks Baritone over and the payload is
 * checked: which slot each drop landed in and in what amount.
 *
 * The merge scenario is the regression core: a drop joining an existing partial stack
 * must report the delta (3), not the whole slot (63). The old pursuit-start baseline
 * reported the whole slot; per-tick indexing reports the delta.
 */
@Suppress("UnstableApiUsage")
object CollectDropsGameTest : FabricClientGameTest {
    private const val PLAYER = "Steve"
    private const val COLLECT_TIMEOUT = 20 * 90

    private val failures = mutableListOf<String>()

    override fun runTest(context: ClientGameTestContext) {
        failures.clear()

        val singleplayer = context.worldBuilder()
            .adjustSettings { it.gameMode = WorldCreator.Mode.SURVIVAL }
            .create()

        try {
            singleplayer.clientWorld.waitForChunksDownload()
            runScenarios(context, singleplayer.server)
        } finally {
            runCatching { context.runOnClient<RuntimeException> { RootTask.cancel() } }
            runCatching { singleplayer.close() }
        }

        if (failures.isEmpty()) {
            LOG.info("Collect drops game test passed")
        } else {
            LOG.error("Collect drops game test failed:")
            failures.forEach { LOG.error("  FAIL $it") }
        }
        check(failures.isEmpty()) {
            "${failures.size} collect drops expectations failed:\n" + failures.joinToString("\n") { "  - $it" }
        }
    }

    private class Outcome(val state: Task.State, val error: String?, val result: List<PickedUpItem>?)

    private fun runScenarios(context: ClientGameTestContext, server: TestServerContext) {
        val auto: Automated = AutomationConfig.DEFAULT
        val center = BlockPos(0, -60, 0)
        prepareWorld(context, server, center)

        // 1. Single drop lands whole into an empty slot: one exact record.
        server.runCommand("/clear $PLAYER")
        server.runCommand("/kill @e[type=item]")
        summonAtFeet(server, center, Items.COBBLESTONE, 5)
        collectAll(context, auto, "single drop").result?.let { pickups ->
            checkSingle(pickups, Items.COBBLESTONE, 5, "single drop")
            checkTotals(context, server, mapOf(Items.COBBLESTONE to 5), "single drop")
        }

        // 2. Drop merges into a partial stack: the record must be the delta (3),
        // not the whole slot (63). This fails on the old whole-slot reporting.
        server.runCommand("/clear $PLAYER")
        server.runCommand("/kill @e[type=item]")
        server.runCommand("/item replace entity $PLAYER hotbar.0 with minecraft:cobblestone 60")
        summonAtFeet(server, center, Items.COBBLESTONE, 3)
        collectAll(context, auto, "merge drop").result?.let { pickups ->
            checkSingle(pickups, Items.COBBLESTONE, 3, "merge drop")
            checkTotals(context, server, mapOf(Items.COBBLESTONE to 63), "merge drop")
        }

        // 3. Two drops of different kinds land the same tick: each attributed to
        // its own slot. Same spot is safe: different items never merge server-side.
        server.runCommand("/clear $PLAYER")
        server.runCommand("/kill @e[type=item]")
        summonAtFeet(server, center, Items.COBBLESTONE, 3)
        summonAtFeet(server, center, Items.DIRT, 4)
        collectAll(context, auto, "multi drop").result?.let { pickups ->
            checkTotals(context, server, mapOf(Items.COBBLESTONE to 3, Items.DIRT to 4), "multi drop")
            val cobble = pickups.filter { it.stack.item == Items.COBBLESTONE }.sumOf { it.stack.count }
            val dirt = pickups.filter { it.stack.item == Items.DIRT }.sumOf { it.stack.count }
            if (cobble != 3 || dirt != 4) {
                fail("multi drop payload covers cobble=$cobble dirt=$dirt, expected 3 and 4 in $pickups")
            }
            val unknown = pickups.filter { it.slot == null }
            if (unknown.isNotEmpty()) {
                fail("multi drop left clean landings unattributed: $unknown")
            }
        }

        // 4. Drop destroyed mid-pursuit: no collector, no growth. The task must fail
        // naming it instead of crediting a phantom pickup. /kill stands in for
        // void, lava or cactus: despawn without any pickup animation.
        server.runCommand("/clear $PLAYER")
        server.runCommand("/kill @e[type=item]")
        summonAtFeet(server, center, Items.COBBLESTONE, 7)
        val lost = collectAll(context, auto, "destroyed drop") {
            server.runCommand("/kill @e[type=item]")
        }
        if (lost.state != Task.State.Failed) {
            fail("destroyed drop: expected task failure but was ${lost.state}")
        } else if (lost.error?.contains("Failed to collect") != true) {
            fail("destroyed drop: wrong failure cause: ${lost.error}")
        }
        if ((lost.result ?: emptyList()).isNotEmpty()) {
            fail("destroyed drop: nothing was collected but the payload is not empty: ${lost.result}")
        }
        checkTotals(context, server, emptyMap(), "destroyed drop")
    }

    /** Runs the real task over every item entity currently loaded client-side. */
    private fun collectAll(
        context: ClientGameTestContext,
        auto: Automated,
        what: String,
        afterStart: (ClientGameTestContext) -> Unit = {},
    ): Outcome {
        context.waitTicks(3)
        val drops = context.computeOnClient<MutableSet<ItemEntity>, RuntimeException> {
            it.world?.entities?.filterIsInstance<ItemEntity>()?.toMutableSet() ?: mutableSetOf()
        }
        if (drops.isEmpty()) {
            fail("$what: no item entities client-side to collect; summon setup broke")
            return Outcome(Task.State.Failed, "no drops", null)
        }
        var result: List<PickedUpItem>? = null
        var error: String? = null
        var task: Task<*>? = null
        context.runOnClient<RuntimeException> {
            runSafe {
                task = with(auto) { collectDrops(drops) }
                    .onSuccess { result = it }
                    .onFailure { error = it.message }
                    .start()
            }
        }
        val started = task ?: run {
            fail("$what: no safe context to start the task")
            return Outcome(Task.State.Failed, "no task", null)
        }
        afterStart(context)
        try {
            context.waitFor({
                started.state != Task.State.Running && started.state != Task.State.Paused
            }, COLLECT_TIMEOUT)
        } catch (_: Throwable) {
            fail("$what: task did not finish within $COLLECT_TIMEOUT ticks (state=${started.state})")
            runCatching { context.runOnClient<RuntimeException> { started.cancel() } }
            return Outcome(started.state, error, result)
        }
        if (started.state != Task.State.Completed && started.state != Task.State.Failed) {
            fail("$what: task ended ${started.state} (${error ?: "no error"})")
            runCatching { context.runOnClient<RuntimeException> { started.cancel() } }
            return Outcome(started.state, error, result)
        }
        if (started.state == Task.State.Completed && result == null) {
            fail("$what: task completed with no payload")
        }
        return Outcome(started.state, error, result)
    }

    private fun checkSingle(pickups: List<PickedUpItem>, item: net.minecraft.item.Item, count: Int, what: String) {
        if (pickups.size != 1) {
            fail("$what: expected exactly 1 pickup record, got ${pickups.size} in $pickups")
            return
        }
        val single = pickups.single()
        if (single.stack.item != item || single.stack.count != count) {
            fail("$what: expected $count of $item, got ${single.stack} (slot=${single.slot?.id})")
        }
        if (single.slot == null) {
            fail("$what: clean landing recorded with unknown slot")
        }
    }

    private fun checkTotals(
        context: ClientGameTestContext,
        server: TestServerContext,
        expected: Map<net.minecraft.item.Item, Int>,
        what: String
    ) {
        context.waitTicks(3)
        val serverStacks = serverPlayerStacks(server)
        val clientStacks = clientPlayerStacks(context)
        expected.forEach { (item, count) ->
            val serverTotal = serverStacks.filter { it.item == item }.sumOf { it.count }
            val clientTotal = clientStacks.filter { it.item == item }.sumOf { it.count }
            if (serverTotal != count || clientTotal != count) {
                fail("$what: expected $count of $item, server=$serverTotal client=$clientTotal")
            }
        }
    }

    /**
     * Drops spawn at the player's feet so pickup is by contact at delay expiry:
     * the test covers attribution, not Baritone locomotion.
     */
    private fun summonAtFeet(
        server: TestServerContext,
        center: BlockPos,
        item: net.minecraft.item.Item,
        count: Int
    ) {
        // PickupDelay keeps the drop on the ground until references are captured below.
        val id = net.minecraft.registry.Registries.ITEM.getId(item)
        val x = center.x + 0.5
        val z = center.z + 0.5
        server.runCommand("/summon minecraft:item $x ${center.y} $z {PickupDelay:100s,Item:{id:\"$id\",count:$count}}")
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
        context.computeOnClient<List<ItemStack>, RuntimeException> { mc ->
            checkNotNull(mc.player) { "collect-drops: no client player" }
                .inventory.mainStacks.map { s -> s.copy() }
        }

    private fun fail(message: String) {
        failures += message
        LOG.warn("FAIL [collect-drops] $message")
    }
}
