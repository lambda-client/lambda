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
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.task.RootTask
import com.lambda.task.Task
import com.lambda.task.start
import com.lambda.task.tasks.breakAndCollect
import com.lambda.task.tasks.build
import com.lambda.threading.runSafe
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.block.Blocks
import net.minecraft.client.gui.screen.world.WorldCreator
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pins down the [BuildTask][com.lambda.task.tasks.BuildTask] pre-tick hook contract:
 *
 * 1. The callback fires every tick with a sane status snapshot (idle collect state).
 * 2. A veto (`true`) stalls the whole tick: while vetoed the target block stays untouched.
 * 3. Releasing the veto lets the build finish normally.
 * 4. A break-and-collect round surfaces the picked-up item type in later statuses.
 */
@Suppress("UnstableApiUsage")
object BuildPreTickGameTest : FabricClientGameTest {
    private const val PLAYER = "Steve"
    private const val VETO_TICKS = 20
    private const val BUILD_TIMEOUT = 20 * 30

    private data class Seen(
        val drops: Int,
        val types: Set<Item>
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
            LOG.info("Build pre-tick game test passed")
        } else {
            LOG.error("Build pre-tick game test failed:")
            failures.forEach { LOG.error("  FAIL $it") }
        }
        check(failures.isEmpty()) {
            "${failures.size} build pre-tick expectations failed:\n" + failures.joinToString("\n") { "  - $it" }
        }
    }

    private fun runScenario(context: ClientGameTestContext, server: TestServerContext) {
        val auto: Automated = AutomationConfig.DEFAULT
        val center = BlockPos(0, -60, 0)
        val target = BlockPos(1, -60, 0)

        prepareWorld(context, server, center)
        server.runCommand("/clear $PLAYER")
        server.runCommand("/give $PLAYER minecraft:dirt 64")

        val seen = CopyOnWriteArrayList<Seen>()
        val veto = AtomicBoolean(true)
        var error: String? = null
        var task: Task<*>? = null
        context.runOnClient<RuntimeException> {
            runSafe {
                task = with(auto) {
                    target.toStructure(TargetState.Block(Blocks.DIRT))
                        .build(finishOnDone = true, collectDrops = false)
                        .onPreTick {
                            seen += Seen(
                                it.dropsPending.size,
                                it.pickedUpTypes.toSet()
                            )
                            veto.get()
                        }
                }.onFailure { error = it.message }.start()
            }
        }
        val build = task
        if (build == null) {
            fail("could not start the build task")
            return
        }

        // 1 + 2. While vetoed the hook must fire every tick but nothing may build.
        context.waitTicks(VETO_TICKS)
        if (build.state != Task.State.Running) {
            fail("vetoed build should still be running but was ${build.state} ($error)")
            return
        }
        if (seen.isEmpty()) {
            fail("pre-tick hook never fired during the veto window")
            return
        }
        val first = seen.first()
        if (first.drops != 0) fail("status should report no pending drops but had ${first.drops}")
        if (first.types.isNotEmpty()) fail("no item types should surface with collectDrops=false but saw ${first.types}")
        if (!isAir(context, target)) {
            fail("vetoed build must not place anything")
            return
        }

        // 3. Releasing the veto lets the build finish.
        veto.set(false)
        try {
            context.waitFor({ build.state != Task.State.Running && build.state != Task.State.Paused }, BUILD_TIMEOUT)
        } catch (_: Throwable) {
            fail("build did not finish within ${BUILD_TIMEOUT} ticks of releasing the veto (state=${build.state}, $error)")
            return
        }
        if (build.state != Task.State.Completed) {
            fail("build should have completed but was ${build.state} ($error)")
            return
        }
        val placed = context.computeOnClient<Boolean, RuntimeException> {
            mc.world?.getBlockState(target)?.isOf(Blocks.DIRT) ?: false
        }
        if (!placed) fail("dirt should be placed after the veto was released")
        if (failures.isNotEmpty()) return

        // 4. Breaking the placed dirt collects its drop, surfacing dirt as a picked-up type.
        // (a shovel is required: the break sim only accepts suitable tools and yields
        // WrongItemSelection otherwise, which stalls the build by design of handleResult.)
        server.runCommand("/give $PLAYER minecraft:stone_shovel 1")
        val seen2 = CopyOnWriteArrayList<Seen>()
        var error2: String? = null
        var task2: Task<*>? = null
        context.runOnClient<RuntimeException> {
            runSafe {
                task2 = with(auto) {
                    breakAndCollect(target)
                        .onPreTick {
                            seen2 += Seen(
                                it.dropsPending.size,
                                it.pickedUpTypes.toSet()
                            )
                            false
                        }
                }.onFailure { error2 = it.message }.start()
            }
        }
        val breaking = task2
        if (breaking == null) {
            fail("could not start the break-and-collect task")
            return
        }
        try {
            context.waitFor({ breaking.state != Task.State.Running && breaking.state != Task.State.Paused }, BUILD_TIMEOUT)
        } catch (_: Throwable) {
            val last = seen2.lastOrNull()
            val stillDirt = context.computeOnClient<Boolean, RuntimeException> {
                mc.world?.getBlockState(target)?.isOf(Blocks.DIRT) ?: false
            }
            fail("break-and-collect did not finish within $BUILD_TIMEOUT ticks " +
                "(state=${breaking.state}, $error2, callbacks=${seen2.size}, " +
                "drops=${last?.drops}, stillDirt=$stillDirt)")
            return
        }
        if (breaking.state != Task.State.Completed) {
            fail("break-and-collect should have completed but was ${breaking.state} ($error2)")
            return
        }
        if (seen2.none { Items.DIRT in it.types }) fail("dirt should surface as a picked-up type after collecting its drop")
    }

    private fun isAir(context: ClientGameTestContext, pos: BlockPos) =
        context.computeOnClient<Boolean, RuntimeException> {
            mc.world?.getBlockState(pos)?.isAir ?: false
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
        LOG.warn("FAIL [build-pretick] $message")
    }
}
