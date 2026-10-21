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
import com.lambda.interaction.container.ContainerSerializer
import com.lambda.interaction.container.ContainerType
import com.lambda.interaction.container.PlacedContainer
import com.lambda.interaction.container.containers.external.ChestContainer
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.containerSelection
import com.lambda.interaction.handler.handlers.ContainerHandler.findContainers
import com.lambda.interaction.handler.handlers.ContainerSearchScope
import com.lambda.util.FolderRegistry
import com.lambda.util.extension.dim
import com.lambda.util.world.Dimension
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.gui.screen.world.WorldCreator
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Pins the per-world/per-dimension container storage layout:
 *
 * 1. Overworld and Nether chests at the same chunk are saved under separate dimension folders.
 * 2. The in-memory index is keyed by dimension, so it can be queried across dimensions.
 * 3. The ender chest stays at the world folder because its contents are dimension-independent.
 * 4. Container searches see every dimension by default and can be restricted with [ContainerSelection].
 */
object ContainerStorageGameTest : FabricClientGameTest {
    private const val PLAYER = "Steve"

    private val failures = mutableListOf<String>()

    override fun runTest(context: ClientGameTestContext) {
        failures.clear()

        val singleplayer = context.worldBuilder()
            .adjustSettings { it.gameMode = WorldCreator.Mode.CREATIVE }
            .create()

        try {
            singleplayer.clientWorld.waitForChunksDownload()
            val worldName = context.computeOnClient<String, RuntimeException> { client ->
                client.server?.getSavePath(WorldSavePath.ROOT)?.normalize()?.fileName?.toString()
                    ?: "unknown"
            }
            val worldDir = FolderRegistry.containers.resolve(worldName)
            val overworldPos = BlockPos(0, 100, 0)
            val netherPos = BlockPos(0, 64, 0)

            recordChest(context, singleplayer.server, overworldPos)
            expectFile(worldDir.resolve("Overworld").resolve(chestFileName(overworldPos)), "overworld chest file")

            singleplayer.server.runCommand(
                "/execute in minecraft:the_nether run tp $PLAYER " +
                    "${netherPos.x + 0.5} ${netherPos.y} ${netherPos.z + 0.5}"
            )
            context.waitFor({ it.world?.dim == Dimension.Nether }, 20 * 10)
            context.waitTicks(5)

            recordChest(context, singleplayer.server, netherPos)
            context.runOnClient<RuntimeException> { ContainerSerializer.saveEnderChest() }
            expectFile(worldDir.resolve("Nether").resolve(chestFileName(netherPos)), "nether chest file")
            expectFile(worldDir.resolve("ender_chest.json"), "world ender chest file")
            expectMissing(worldDir.resolve("Nether").resolve("ender_chest.json"), "per-dimension ender chest file")

            val netherIndex = savedPositions(context)
            if (netherIndex != setOf(netherPos)) {
                fail("nether index should contain only $netherPos but got $netherIndex")
            }

            singleplayer.server.runCommand(
                "/execute in minecraft:overworld run tp $PLAYER " +
                    "${overworldPos.x + 0.5} ${overworldPos.y} ${overworldPos.z + 0.5}"
            )
            context.waitFor({ it.world?.dim == Dimension.Overworld }, 20 * 10)
            context.waitTicks(5)

            val overworldIndex = savedPositions(context)
            if (overworldIndex != setOf(overworldPos)) {
                fail("overworld index should contain only $overworldPos but got $overworldIndex")
            }

            val netherIndexFromOverworld = context.computeOnClient<Map<BlockPos, ContainerType>, RuntimeException> {
                ContainerSerializer.savedIn(Dimension.Nether, ChunkPos(0, 0))
            }
            if (netherIndexFromOverworld != mapOf(netherPos to ContainerType.Chest)) {
                fail("nether index should remain available from overworld but got $netherIndexFromOverworld")
            }

            val dimensions = context.computeOnClient<Map<Dimension, Set<BlockPos>>, RuntimeException> {
                ContainerSerializer.serializedContainers
                    .filterIsInstance<ChestContainer>()
                    .groupBy({ it.dimension }, { it.pos })
                    .mapValues { it.value.toSet() }
            }
            if (dimensions[Dimension.Overworld] != setOf(overworldPos)) {
                fail("overworld containers should contain only $overworldPos but got ${dimensions[Dimension.Overworld]}")
            }
            if (dimensions[Dimension.Nether] != setOf(netherPos)) {
                fail("nether containers should contain only $netherPos but got ${dimensions[Dimension.Nether]}")
            }

            val allChests = chestPositions(
                context,
                containerSelection(ContainerSearchScope.All) {
                    ofAnyType(ContainerType.Chest)
                }
            )
            if (allChests != setOf(overworldPos, netherPos)) {
                fail("unfiltered container search should find both chests but got $allChests")
            }

            val netherChests = chestPositions(
                context,
                containerSelection(ContainerSearchScope.All) {
                    ofAnyType(ContainerType.Chest)
                    inAnyDimension(Dimension.Nether)
                }
            )
            if (netherChests != setOf(netherPos)) {
                fail("nether-filtered container search should find only $netherPos but got $netherChests")
            }

            val overworldChests = chestPositions(
                context,
                containerSelection(ContainerSearchScope.All) {
                    ofAnyType(ContainerType.Chest)
                    inAnyDimension(Dimension.Overworld)
                }
            )
            if (overworldChests != setOf(overworldPos)) {
                fail("overworld-filtered container search should find only $overworldPos but got $overworldChests")
            }
        } finally {
            runCatching { singleplayer.close() }
        }

        if (failures.isEmpty()) {
            LOG.info("Container storage game test passed")
        } else {
            LOG.error("Container storage game test failed:")
            failures.forEach { LOG.error("  FAIL $it") }
        }
        check(failures.isEmpty()) {
            "${failures.size} container storage expectations failed:\n" + failures.joinToString("\n") { "  - $it" }
        }
    }

    private fun recordChest(context: ClientGameTestContext, server: TestServerContext, pos: BlockPos) {
        server.runCommand("/setblock ${pos.x} ${pos.y} ${pos.z} minecraft:chest")
        context.waitTicks(2)
        context.runOnClient<RuntimeException> {
            ContainerSerializer.saveContainer(
                ChestContainer(pos, List(27) { ItemStack(Items.STONE, 5) })
            )
        }
    }

    private fun savedPositions(context: ClientGameTestContext): Set<BlockPos> =
        context.computeOnClient<Set<BlockPos>, RuntimeException> {
            ContainerSerializer.savedIn(ChunkPos(0, 0)).keys
        }

    private fun chestPositions(
        context: ClientGameTestContext,
        selection: ContainerSelection
    ): Set<BlockPos> =
        context.computeOnClient<Set<BlockPos>, RuntimeException> {
            with(AutomationConfig.DEFAULT) {
                findContainers(selection, sorted = false)
                    .mapNotNull { (it as? PlacedContainer)?.pos }
                    .toSet()
            }
        }

    private fun chestFileName(pos: BlockPos): String {
        val chunkX = pos.x shr 4
        val chunkZ = pos.z shr 4
        return "${chunkX}_${chunkZ}_${pos.x}_${pos.y}_${pos.z}_Chest.json"
    }

    private fun expectFile(file: Path, label: String) {
        if (!file.exists()) fail("$label should exist at $file")
    }

    private fun expectMissing(file: Path, label: String) {
        if (file.exists()) fail("$label should not exist at $file")
    }

    private fun fail(message: String) {
        failures += message
        LOG.warn("FAIL $message")
    }
}
