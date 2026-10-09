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
import com.lambda.interaction.container.selection.containerSelection
import com.lambda.interaction.container.selection.select
import com.lambda.interaction.handler.handlers.ContainerSearchScope
import com.lambda.task.RootTask
import com.lambda.task.Task
import com.lambda.task.start
import com.lambda.task.tasks.openContainer
import com.lambda.task.tasks.transfer
import com.lambda.task.tasks.wrappers.actionTask
import com.lambda.task.tasks.wrappers.then
import com.lambda.threading.runSafe
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.gui.screen.world.WorldCreator
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos

/**
 * Pins down two task-system guarantees that used to break in long sessions:
 *
 * 1. A failing task must never wedge the root: the child's own error handling runs, siblings
 *    survive, and [RootTask] stays Running so `;task cancel` keeps working.
 * 2. Cancel actually clears: a stuck task (here an [openContainer] somewhere unreachable that
 *    keeps retrying) ends up Cancelled and disappears from the tree.
 */
@Suppress("UnstableApiUsage")
object TaskLifecycleGameTest : FabricClientGameTest {
    private const val PLAYER = "Steve"
    private const val TRANSFER_TIMEOUT = 20 * 40
    private const val OPEN_TIMEOUT = 20 * 10

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
            LOG.info("Task lifecycle game test passed")
        } else {
            LOG.error("Task lifecycle game test failed:")
            failures.forEach { LOG.error("  FAIL $it") }
        }
        check(failures.isEmpty()) {
            "${failures.size} task lifecycle expectations failed:\n" + failures.joinToString("\n") { "  - $it" }
        }
    }

    private class Outcome(val state: Task.State, val error: String?, val ticks: Int) {
        val succeeded get() = state == Task.State.Completed
        val failed get() = state == Task.State.Failed
    }

    private fun runScenario(context: ClientGameTestContext, server: TestServerContext) {
        val auto: Automated = AutomationConfig.DEFAULT
        val center = BlockPos(0, -60, 0)
        val chestPos = BlockPos(2, -60, 0)

        prepareWorld(context, server, center)
        server.runCommand("/clear $PLAYER")
        server.runCommand("/kill @e[type=item]")
        server.runCommand("/setblock ${chestPos.x} ${chestPos.y} ${chestPos.z} minecraft:chest[facing=west]")
        server.runCommand("/item replace block ${chestPos.x} ${chestPos.y} ${chestPos.z} container.0 with minecraft:dirt 64")

        // 1. A transfer with no source must fail the transfer only: the root stays Running.
        val chests = containerSelection(ContainerSearchScope.All) {
            ofAnyType(ContainerType.Chest)
            predicate { it is PlacedContainer && it.pos == chestPos }
        }
        val doomed = runTask(context, TRANSFER_TIMEOUT) {
            with(auto) {
                transfer(
                    Items.STONE.select(10),
                    chests,
                    chests
                )
            }
        }
        if (!doomed.failed) {
            fail("a sourceless transfer should have failed but was ${doomed.state}")
            return
        }
        val rootAfterFailure = context.computeOnClient<Task.State, RuntimeException> { RootTask.state }
        if (rootAfterFailure != Task.State.Running) {
            fail("RootTask must stay Running after a child failure but was $rootAfterFailure")
            return
        }

        // 1b. A failing inner task must fail its wrapper instead of stranding it Running. This
        // proves chained children are parented to their wrapper: with flat root parenting the
        // wrapper would never hear about the failure and hang forever.
        val chained = runTask(context, TRANSFER_TIMEOUT) {
            with(auto) {
                transfer(
                    Items.STONE.select(10),
                    chests,
                    chests
                ).then { actionTask { } }
            }
        }
        if (!chained.failed) {
            fail("a chained wrapper over a failing transfer should have failed but was ${chained.state}")
            return
        }

        // 2. A stuck open (nothing openable out there) keeps retrying; cancel must kill it.
        val farPos = BlockPos(center.x + 5, center.y, center.z + 5)
        var stuck: Task<*>? = null
        context.runOnClient<RuntimeException> {
            runSafe {
                stuck = with(auto) { openContainer(farPos) }.start()
            }
        }
        val stuckTask = stuck
        if (stuckTask == null) {
            fail("could not start the stuck open task")
            return
        }
        context.waitTicks(30)
        val stillRunning = context.computeOnClient<Boolean, RuntimeException> {
            stuckTask.state == Task.State.Running
        }
        if (!stillRunning) {
            fail("the far open task should still be retrying but was ${stuckTask.state}")
            return
        }
        context.runOnClient<RuntimeException> { RootTask.cancel() }
        context.waitTicks(3)
        val cancelled = context.computeOnClient<Task.State, RuntimeException> { stuckTask.state }
        if (cancelled != Task.State.Cancelled) {
            fail("cancel must terminate the stuck task but it was $cancelled")
            return
        }
        val rootAfterCancel = context.computeOnClient<Task.State, RuntimeException> { RootTask.state }
        if (rootAfterCancel != Task.State.Running) {
            fail("RootTask must stay Running after cancel but was $rootAfterCancel")
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
        LOG.warn("FAIL [task-lifecycle] $message")
    }
}
