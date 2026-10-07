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
import com.lambda.config.blocks.BuildConfig
import com.lambda.config.blocks.InventoryConfig
import com.lambda.context.Automated
import com.lambda.interaction.container.Container
import com.lambda.interaction.container.ContainerSerializer
import com.lambda.interaction.container.ContainerType
import com.lambda.interaction.container.PlacedContainer
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.containers.InventoryContainer
import com.lambda.interaction.container.containers.external.DoubleChestContainer
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.container.selection.containerSelection
import com.lambda.interaction.container.selection.select
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
import net.minecraft.entity.ItemEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos

/**
 * Exercises [com.lambda.task.tasks.ContainerTransferTask] inside a real singleplayer world: chests are placed and
 * filled with server commands, the real task opens them, clicks through the real inventory manager, and the
 * server-side inventories are read back afterwards.
 *
 * Every scenario additionally checks that no item was created or destroyed, that the client and server agree on
 * the player inventory (no desync), that no screen was left open and that the cursor is empty.
 *
 * Scenarios never throw mid-way; all expectation failures are collected and reported together at the end so one
 * run gives the full picture.
 */
@Suppress("UnstableApiUsage")
object ContainerTransferGameTest : FabricClientGameTest {
	private const val PLAYER = "Steve"
	private const val TRANSFER_TIMEOUT = 20 * 40
	private const val OPEN_TIMEOUT = 20 * 10

	private val STONE: Item get() = Items.STONE
	private val DIRT: Item get() = Items.DIRT
	private val COBBLE: Item get() = Items.COBBLESTONE
	private val OAK_PLANKS: Item get() = Items.OAK_PLANKS
	private val SAND: Item get() = Items.SAND
	private val GRAVEL: Item get() = Items.GRAVEL
	private val GLASS: Item get() = Items.GLASS
	private val BRICKS: Item get() = Items.BRICKS

	private val failures = mutableListOf<String>()
	private val passed = mutableListOf<String>()

	override fun runTest(context: ClientGameTestContext) {
		failures.clear()
		passed.clear()

		val singleplayer = context.worldBuilder()
			.adjustSettings { it.gameMode = WorldCreator.Mode.SURVIVAL }
			.create()

		try {
			singleplayer.clientWorld.waitForChunksDownload()
			val arena = Arena(context, singleplayer.server)
			arena.prepareWorld()

			scenarios.forEach { scenario -> arena.run(scenario) }
		} catch (e: ClientGoneException) {
			LOG.error("Container transfer game test aborted: ${e.message}")
			failures += "[run] aborted: the client is no longer running"
		} finally {
			runCatching { context.runOnClient<RuntimeException> { RootTask.cancel() } }
			runCatching { singleplayer.close() }
		}

		LOG.info("Container transfer game test: ${passed.size} scenarios passed, ${failures.size} expectations failed")
		passed.forEach { LOG.info("  PASS $it") }
		failures.forEach { LOG.error("  FAIL $it") }
		check(failures.isEmpty()) {
			"${failures.size} container transfer expectations failed:\n" + failures.joinToString("\n") { "  - $it" }
		}
	}

	// =====================================================================================================
	// Scenario DSL
	// =====================================================================================================

	/** A stack as the `/item` command spells it. */
	data class Spec(val id: String, val count: Int, val components: String = "") {
		override fun toString() = "minecraft:$id$components $count"
	}

	private fun stone(count: Int) = Spec("stone", count)
	private fun dirt(count: Int) = Spec("dirt", count)
	private fun cobble(count: Int) = Spec("cobblestone", count)
	private fun namedStone(count: Int, name: String) = Spec("stone", count, "[custom_name='\"$name\"']")
	private fun planks(count: Int) = Spec("oak_planks", count)
	private fun sand(count: Int) = Spec("sand", count)
	private fun gravel(count: Int) = Spec("gravel", count)
	private fun glass(count: Int) = Spec("glass", count)
	private fun bricks(count: Int) = Spec("bricks", count)

	class Scenario(val name: String, val body: Arena.() -> Unit)

	/** The client window was closed (or crashed) underneath the test; nothing further can run. */
	class ClientGoneException(cause: Throwable) : RuntimeException("the client is no longer running", cause)

	private fun scenario(name: String, body: Arena.() -> Unit) = Scenario(name, body)

	class Outcome(val state: Task.State, val error: String?, val ticks: Int) {
		val succeeded get() = state == Task.State.Completed
		val failed get() = state == Task.State.Failed
	}

	/** Server-side truth after a scenario: chest contents by label, the player's 36 main slots, ender chest, items on the ground. */
	class Snapshot(
		val chests: Map<String, List<ItemStack>>,
		val player: List<ItemStack>,
		val enderChest: List<ItemStack>,
		val dropped: List<ItemStack>
	) {
		fun chest(label: String, item: Item) = chests[label].orEmpty().filter { it.item == item }.sumOf { it.count }
		fun hotbar(item: Item) = player.take(9).filter { it.item == item }.sumOf { it.count }
		fun inventory(item: Item) = player.drop(9).filter { it.item == item }.sumOf { it.count }
		fun player(item: Item) = hotbar(item) + inventory(item)
		fun enderChest(item: Item) = enderChest.filter { it.item == item }.sumOf { it.count }

		fun totals(): Map<Item, Int> {
			val totals = mutableMapOf<Item, Int>()
			(chests.values.flatten() + player + enderChest + dropped)
				.filter { !it.isEmpty }
				.forEach { totals.merge(it.item, it.count, Int::plus) }
			return totals
		}

		fun describe() = buildString {
			chests.forEach { (label, stacks) -> append(label).append('=').append(stacks.filter { !it.isEmpty }).append(' ') }
			append("player=").append(player.filter { !it.isEmpty })
			if (enderChest.any { !it.isEmpty }) append(" enderChest=").append(enderChest.filter { !it.isEmpty })
			if (dropped.isNotEmpty()) append(" dropped=").append(dropped)
		}
	}

	class Arena(private val context: ClientGameTestContext, val server: TestServerContext) {
		private val center = BlockPos(0, -60, 0)

		/** Single chest slots around the player, each two blocks away and facing the player. */
		private val singles = mapOf(
			"A" to (center.add(2, 0, 0) to "west"),
			"B" to (center.add(-2, 0, 0) to "east"),
			"C" to (center.add(0, 0, 2) to "north"),
			"D" to (center.add(0, 0, -2) to "south")
		)

		/**
		 * A double chest ("E") next to B, made of two east-facing halves. For an east-facing chest vanilla expects
		 * the `left` half's partner to its south, so `left` sits at z+1 and `right` at z+2.
		 */
		private val doubleLeft = center.add(-2, 0, 1)
		private val doubleRight = center.add(-2, 0, 2)

		private val placed = linkedMapOf<String, List<BlockPos>>()
		private lateinit var scenarioName: String
		private var before: Snapshot? = null

		val automated: Automated get() = AutomationConfig.DEFAULT
		val player get() = ContainerSelection.HOTBAR_AND_INVENTORY

		fun prepareWorld() {
			server.runCommand("/gamemode survival $PLAYER")
			// Daylight, weather and mob spawning are already disabled by the world builder's consistent settings.
			server.runCommand("/difficulty peaceful")
			server.runCommand("/time set noon")
			server.runCommand("/weather clear")
			server.runCommand("/fill ${center.x - 5} ${center.y - 1} ${center.z - 5} ${center.x + 5} ${center.y - 1} ${center.z + 5} minecraft:stone")
			server.runCommand("/fill ${center.x - 5} ${center.y} ${center.z - 5} ${center.x + 5} ${center.y + 4} ${center.z + 5} minecraft:air")
			teleportPlayer()
			context.waitTicks(10)
		}

		fun run(scenario: Scenario) {
			scenarioName = scenario.name
			LOG.info("=== Transfer scenario: ${scenario.name}")
			val failuresBefore = failures.size
			try {
				reset()
				scenario.body(this)
			} catch (e: ClientGoneException) {
				throw e
			} catch (e: Throwable) {
				fail("threw ${e::class.simpleName}: ${e.message}")
				LOG.error("Scenario '${scenario.name}' threw", e)
			} finally {
				cancelTasks()
			}
			if (failures.size == failuresBefore) {
				passed += scenario.name
				LOG.info("PASS ${scenario.name}")
			} else {
				LOG.warn("FAIL ${scenario.name} (${failures.size - failuresBefore} expectation(s))")
			}
		}

		// ---- world setup ----------------------------------------------------------------------------------

		private fun reset() {
			cancelTasks()
			server.runCommand("/clear $PLAYER")
			server.runCommand("/kill @e[type=item]")
			placed.values.flatten().forEach { server.runCommand("/setblock ${it.x} ${it.y} ${it.z} minecraft:air") }
			onClient { allPositions().forEach(ContainerSerializer::removeContainer) }
			repeat(27) { server.runCommand("/item replace entity $PLAYER enderchest.$it with minecraft:air") }
			placed.clear()
			before = null
			teleportPlayer()
			context.waitTicks(5)
		}

		private fun teleportPlayer() {
			server.runCommand("/tp $PLAYER ${center.x + 0.5} ${center.y} ${center.z + 0.5} 0 0")
		}

		private fun allPositions() = singles.values.map { it.first } + doubleLeft + doubleRight

		/** Places a single chest at slot [label] holding [contents] in order. */
		fun chest(label: String, vararg contents: Spec): String {
			val (pos, facing) = singles.getValue(label)
			server.runCommand("/setblock ${pos.x} ${pos.y} ${pos.z} minecraft:chest[facing=$facing]")
			fill(pos, contents.toList())
			placed[label] = listOf(pos)
			return label
		}

		/** Places a full chest at [label] whose every slot holds [stack]. */
		fun fullChest(label: String, stack: Spec) = chest(label, *Array(27) { stack })

		/** Places the double chest "E" (54 slots) holding [contents] in order. */
		fun doubleChest(vararg contents: Spec): String {
			server.runCommand("/setblock ${doubleRight.x} ${doubleRight.y} ${doubleRight.z} minecraft:chest[facing=east,type=right]")
			server.runCommand("/setblock ${doubleLeft.x} ${doubleLeft.y} ${doubleLeft.z} minecraft:chest[facing=east,type=left]")
			fill(doubleRight, contents.take(27))
			fill(doubleLeft, contents.drop(27))
			placed["E"] = listOf(doubleLeft, doubleRight)
			return "E"
		}

		private fun fill(pos: BlockPos, contents: List<Spec>) {
			contents.forEachIndexed { slot, spec ->
				server.runCommand("/item replace block ${pos.x} ${pos.y} ${pos.z} container.$slot with $spec")
			}
		}

		fun hotbar(slot: Int, spec: Spec) = server.runCommand("/item replace entity $PLAYER hotbar.$slot with $spec")
		fun inventory(slot: Int, spec: Spec) = server.runCommand("/item replace entity $PLAYER inventory.$slot with $spec")
		fun enderchest(slot: Int, spec: Spec) = server.runCommand("/item replace entity $PLAYER enderchest.$slot with $spec")
		fun fillHotbar(spec: Spec) = repeat(9) { hotbar(it, spec) }
		fun fillInventory(spec: Spec) = repeat(27) { inventory(it, spec) }

		/**
		 * Lets Lambda learn about every placed chest the way it does in normal play: the player opens each one once
		 * and closes it again, which records the container and its contents on disk. Must be called after the
		 * chests were filled. Also takes the "before" snapshot for the conservation check.
		 */
		fun ready() {
			context.waitTicks(2)
			val truth = snapshot()
			placed.forEach { (label, positions) ->
				val pos = positions.first()
				val expectedItems = truth.chests[label].orEmpty().sumOf { it.count }
				var recorded = false
				repeat(2) { attempt ->
					if (recorded) return@repeat
					val open = runTask(OPEN_TIMEOUT) {
						with(automated) { openContainer(pos) }.thenAction { player.closeScreen() }
					}
					if (!open.succeeded) fail("could not open chest $label at ${pos.toShortString()}: ${open.state} ${open.error}")
					context.waitFor({ it.currentScreen == null }, OPEN_TIMEOUT)
					context.waitTicks(2)
					recorded = context.computeOnClient<Boolean, RuntimeException> {
						ContainerSerializer.serializedContainers.any { container ->
							container.isAt(positions.toSet()) && container.stacks.sumOf { it.count } == expectedItems
						}
					}
					if (!recorded && attempt == 0) LOG.warn("chest $label was recorded with the wrong contents, opening it again")
				}
				if (!recorded) fail("chest $label was not recorded with its $expectedItems items after opening it")
			}
			before = truth
		}

		// ---- running the task ----------------------------------------------------------------------------

		fun chests(vararg labels: String): ContainerSelection {
			val positions = labels.flatMap { placed.getValue(it) }.toSet()
			return containerSelection(ContainerSearchScope.All) {
				ofAnyType(ContainerType.Chest)
				predicate { it.isAt(positions) }
			}
		}

		fun transfer(
			fromStack: StackSelection,
			fromSelection: ContainerSelection = ContainerSelection.ACCESSED,
			toSelection: ContainerSelection,
			toStack: StackSelection = StackSelection.ANYTHING,
			automated: Automated = this.automated,
			onTick: (net.minecraft.client.MinecraftClient) -> Unit = {}
		): Outcome {
			if (before == null) ready()
			val outcome = runTask(TRANSFER_TIMEOUT, onTick) {
				createTransferTask(automated, fromStack, fromSelection, toSelection, toStack)
			}
			if (!outcome.succeeded) cancelTasks()
			context.waitFor({ it.currentScreen == null }, OPEN_TIMEOUT)
			context.waitTicks(5)
			return outcome
		}

		private fun runTask(
			timeout: Int,
			onTick: (net.minecraft.client.MinecraftClient) -> Unit = {},
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
				context.waitFor({ client ->
					onTick(client)
					started.state != Task.State.Running && started.state != Task.State.Paused
				}, timeout)
			} catch (_: Throwable) {
				timeout
			}
			return Outcome(started.state, error, ticks)
		}

		private fun cancelTasks() {
			onClient { RootTask.cancel() }
			// A cancelled task may still have an "open container" packet in flight; the server answers it a few
			// ticks later with a screen that would otherwise leak into the next scenario.
			repeat(4) {
				context.waitTicks(3)
				onClient { it.player?.takeIf { p -> p.currentScreenHandler !== p.playerScreenHandler }?.closeScreen() }
			}
			context.waitTicks(2)
		}

		/** [ClientGameTestContext.runOnClient] that turns a closed client into a fatal [ClientGoneException]. */
		private fun onClient(block: (net.minecraft.client.MinecraftClient) -> Unit) {
			try {
				context.runOnClient<RuntimeException> { block(it) }
			} catch (e: IllegalStateException) {
				if (e.message?.contains("no client is running") == true) throw ClientGoneException(e)
				throw e
			}
		}

		// ---- reading the world -----------------------------------------------------------------------------

		fun snapshot(): Snapshot {
			val labels = placed.toMap()
			return server.computeOnServer<Snapshot, RuntimeException> { mc ->
				val world = mc.overworld
				val chests = labels.mapValues { (_, positions) ->
					positions.flatMap { pos ->
						val entity = world.getBlockEntity(pos) as? ChestBlockEntity ?: return@flatMap emptyList()
						(0 until entity.size()).map { entity.getStack(it).copy() }
					}
				}
				val serverPlayer = mc.playerManager.playerList.first { it.gameProfile.name == PLAYER }
				val player = serverPlayer.inventory.mainStacks.map { it.copy() }
				val enderChest = serverPlayer.enderChestInventory.let { ec ->
					(0 until ec.size()).map { ec.getStack(it).copy() }
				}
				val dropped = world.iterateEntities().filterIsInstance<ItemEntity>().map { it.stack.copy() }
				Snapshot(chests, player, enderChest, dropped)
			}
		}

		/** The player's main inventory as the client sees it, to detect desync against the server. */
		private fun clientPlayerStacks(): List<ItemStack> =
			context.computeOnClient<List<ItemStack>, RuntimeException> { it.player!!.inventory.mainStacks.map { s -> s.copy() } }

		// ---- expectations ------------------------------------------------------------------------------------

		fun fail(message: String) {
			failures += "[$scenarioName] $message"
			LOG.warn("FAIL [$scenarioName] $message")
		}

		fun expect(condition: Boolean, message: () -> String) {
			if (!condition) fail(message())
		}

		fun expectEquals(expected: Any?, actual: Any?, what: String) {
			if (expected != actual) fail("$what: expected $expected but was $actual")
		}

		fun expectSuccess(outcome: Outcome) =
			expect(outcome.succeeded) { "task did not succeed: ${outcome.state} ${outcome.error ?: ""} after ${outcome.ticks} ticks" }

		fun expectFailure(outcome: Outcome) =
			expect(outcome.failed) { "task should have failed but was ${outcome.state} (${outcome.error ?: ""}) after ${outcome.ticks} ticks" }

		/** Item conservation, client/server agreement, closed screens and an empty cursor. Returns the snapshot. */
		fun expectClean(): Snapshot {
			val after = snapshot()
			before?.let { expectEquals(it.totals(), after.totals(), "items were created or destroyed (${after.describe()})") }
			expect(after.dropped.isEmpty()) { "items ended up on the ground: ${after.dropped}" }

			val client = clientPlayerStacks()
			val desynced = client.indices.filter { !ItemStack.areEqual(client[it], after.player[it]) }
			expect(desynced.isEmpty()) {
				"client and server disagree on player slots $desynced: client=${desynced.map { client[it] }} server=${desynced.map { after.player[it] }}"
			}

			val screen = context.computeOnClient<String, RuntimeException> { mc ->
				mc.currentScreen?.let { it::class.simpleName } ?: ""
			}
			expect(screen.isEmpty()) { "a screen was left open: $screen" }
			val cursor = context.computeOnClient<ItemStack, RuntimeException> { it.player!!.currentScreenHandler.cursorStack.copy() }
			expect(cursor.isEmpty) { "cursor still holds $cursor" }
			return after
		}

		// ---- configuration variants ----------------------------------------------------------------------

		fun automatedWithout(vararg types: ContainerType): Automated {
			val base = automated
			val inventory = object : InventoryConfig by base.inventoryConfig {
				override val allowedContainers: Collection<ContainerType> = ContainerType.entries - types.toSet()
			}
			return object : Automated by base {
				override val inventoryConfig = inventory
			}
		}

		fun automatedWithInventoryLimit(limit: Int): Automated {
			val base = automated
			val build = object : BuildConfig by base.buildConfig {
				override val inventoryLimit = limit
			}
			return object : Automated by base {
				override val buildConfig = build
			}
		}
	}

	/** Lives outside [Arena] so the call is not shadowed by [Arena.transfer]. */
	private fun createTransferTask(
		automated: Automated,
		fromStack: StackSelection,
		fromSelection: ContainerSelection,
		toSelection: ContainerSelection,
		toStack: StackSelection
	): Task<*> = with(automated) { transfer(fromStack, fromSelection, toSelection, toStack) }

	private fun Container.isAt(positions: Set<BlockPos>) =
		when (this) {
			is DoubleChestContainer -> leftPos in positions || rightPos in positions
			is PlacedContainer -> pos in positions
			else -> false
		}

	// =====================================================================================================
	// Scenarios
	// =====================================================================================================

	private val scenarios = listOf(
		scenario("pull a whole stack from a chest") {
			chest("A", stone(64))
			val outcome = transfer(STONE.select(64), chests("A"), player)
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(64, after.player(STONE), "stone with the player")
			expectEquals(0, after.chest("A", STONE), "stone left in A")
		},

		scenario("pull an exact partial count from a chest") {
			chest("A", stone(64))
			val outcome = transfer(STONE.select(20), chests("A"), player)
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(20, after.player(STONE), "stone with the player")
			expectEquals(44, after.chest("A", STONE), "stone left in A")
		},

		scenario("pull a count spanning several stacks") {
			chest("A", stone(64), stone(64))
			val outcome = transfer(STONE.select(100), chests("A"), player)
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(100, after.player(STONE), "stone with the player")
			expectEquals(28, after.chest("A", STONE), "stone left in A")
		},

		scenario("a count of zero moves everything matching and nothing else") {
			chest("A", stone(64), dirt(64), stone(64), stone(5))
			val outcome = transfer(STONE.select(0), chests("A"), player)
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(133, after.player(STONE), "stone with the player")
			expectEquals(0, after.chest("A", STONE), "stone left in A")
			expectEquals(64, after.chest("A", DIRT), "dirt must stay in A")
			expectEquals(0, after.player(DIRT), "dirt with the player")
		},

		scenario("push a stack into a chest, merging with its partial stack") {
			chest("A", stone(10))
			hotbar(0, stone(64))
			val outcome = transfer(STONE.select(64), player, chests("A"))
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(74, after.chest("A", STONE), "stone in A")
			expectEquals(0, after.player(STONE), "stone with the player")
		},

		scenario("push an exact partial count into a chest") {
			chest("A")
			hotbar(0, stone(64))
			val outcome = transfer(STONE.select(13), player, chests("A"))
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(13, after.chest("A", STONE), "stone in A")
			expectEquals(51, after.player(STONE), "stone with the player")
		},

		scenario("swap a foreign stack out of a full chest") {
			fullChest("A", dirt(64))
			hotbar(0, stone(64))
			val outcome = transfer(STONE.select(64), HotbarContainer.select(), chests("A"))
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(64, after.chest("A", STONE), "stone in A")
			expectEquals(26 * 64, after.chest("A", DIRT), "dirt in A")
			expectEquals(64, after.player(DIRT), "dirt with the player")
		},

		scenario("move a stack between two chests through the player inventory") {
			chest("A", stone(64))
			chest("B")
			val outcome = transfer(STONE.select(64), chests("A"), chests("B"))
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(64, after.chest("B", STONE), "stone in B")
			expectEquals(0, after.chest("A", STONE), "stone in A")
			expectEquals(0, after.player(STONE), "stone with the player")
		},

		scenario("move an exact partial count between chests") {
			chest("A", stone(64), stone(64))
			chest("B")
			val outcome = transfer(STONE.select(70), chests("A"), chests("B"))
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(70, after.chest("B", STONE), "stone in B")
			expectEquals(58, after.chest("A", STONE), "stone in A")
			expectEquals(0, after.player(STONE), "stone with the player")
		},

		scenario("a source larger than the player inventory needs several trips into a double chest") {
			fullChest("A", stone(64))
			fullChest("C", stone(64))
			doubleChest()
			val outcome = transfer(STONE.select(0), chests("A", "C"), chests("E"))
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(54 * 64, after.chest("E", STONE), "stone in the double chest")
			expectEquals(0, after.chest("A", STONE) + after.chest("C", STONE), "stone left in the sources")
			expectEquals(0, after.player(STONE), "stone with the player")
		},

		scenario("draw from a double chest as the source") {
			doubleChest(*Array(54) { stone(64) })
			chest("A")
			val outcome = transfer(STONE.select(27 * 64), chests("E"), chests("A"))
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(27 * 64, after.chest("A", STONE), "stone in A")
			expectEquals(27 * 64, after.chest("E", STONE), "stone left in the double chest")
			expectEquals(0, after.player(STONE), "stone with the player")
		},

		scenario("continue to the next destination when the first fills up mid trip") {
			chest("A", stone(64), stone(1))
			chest("B", *Array(26) { dirt(64) })
			chest("C")
			val outcome = transfer(STONE.select(65), chests("A"), chests("B", "C"), stackSelection { isEmpty() })
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(64, after.chest("B", STONE), "stone in B")
			expectEquals(1, after.chest("C", STONE), "stone in C")
			expectEquals(0, after.player(STONE), "stone stranded with the player")
		},

		scenario("trips do not push the player's own matching items") {
			chest("A", stone(64))
			chest("B")
			hotbar(0, stone(5))
			val outcome = transfer(STONE.select(0), chests("A"), chests("B"))
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(64, after.chest("B", STONE), "stone in B")
			expectEquals(5, after.player(STONE), "the player's own stone")
		},

		scenario("a stack selection over several items moves only those items") {
			chest("A", stone(10), dirt(10), cobble(10))
			val outcome = transfer(stackSelection(20) { ofAnyItems(listOf(STONE, DIRT)) }, chests("A"), player)
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(20, after.player(STONE) + after.player(DIRT), "stone and dirt with the player")
			expectEquals(0, after.player(COBBLE), "cobblestone with the player")
		},

		scenario("named and unnamed variants of an item are not swapped against each other") {
			chest("A", namedStone(64, "Keep"))
			hotbar(0, stone(10))
			val outcome = transfer(STONE.select(64), chests("A"), player)
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(0, after.chest("A", STONE), "stone left in A")
			expectEquals(74, after.player(STONE), "stone with the player")
		},

		scenario("whole stack moves land in the requested player container") {
			chest("A", stone(64))
			val outcome = transfer(STONE.select(64), chests("A"), InventoryContainer.select())
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(64, after.inventory(STONE), "stone in the main inventory")
			expectEquals(0, after.hotbar(STONE), "stone in the hotbar")
		},

		scenario("the stack selection comparator picks the source stack") {
			chest("A", stone(1), stone(64))
			val selection = stackSelection(64) {
				isItem(STONE)
				sortedWith { compareByDescending { it.stack.count } }
			}
			val outcome = transfer(selection, chests("A"), player)
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(64, after.player(STONE), "stone with the player")
			expectEquals(1, after.chest("A", STONE), "the single stone must stay")
		},

		scenario("hasSpace in the destination selection skips full chests") {
			fullChest("B", dirt(64))
			chest("C")
			hotbar(0, stone(64))
			val outcome = transfer(
				STONE.select(64),
				player,
				containerSelection(ContainerSearchScope.All) {
					ofAnyType(ContainerType.Chest)
					hasSpace(STONE.select(1))
				}
			)
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(64, after.chest("C", STONE), "stone in C")
			expectEquals(27 * 64, after.chest("B", DIRT), "B must be untouched")
		},

		scenario("destination filter NOTHING still allows empty slots") {
			chest("A")
			hotbar(0, stone(64))
			val outcome = transfer(STONE.select(64), player, chests("A"), StackSelection.NOTHING)
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(64, after.chest("A", STONE), "stone in A")
		},

		scenario("shulker box scope moves the box holding the item") {
			chest("A", Spec("shulker_box", 1, "[container=[{slot:0,item:{id:\"minecraft:diamond\",count:64}}]]"), stone(64))
			val outcome = transfer(
				stackSelection(1) {
					isItem(Items.DIAMOND)
					alsoInShulkerBoxes()
				},
				chests("A"),
				player
			)
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(1, after.player(Items.SHULKER_BOX), "shulker boxes with the player")
			expectEquals(64, after.chest("A", STONE), "stone must stay in A")
		},

		scenario("fails cleanly when no source holds the item") {
			chest("A", dirt(64))
			val outcome = transfer(STONE.select(10), chests("A"), player)
			expectFailure(outcome)
			val after = expectClean()
			expectEquals(64, after.chest("A", DIRT), "dirt in A")
			expectEquals(0, after.player(DIRT), "dirt with the player")
		},

		scenario("fails cleanly when sources hold less than requested") {
			chest("A", stone(10))
			val outcome = transfer(STONE.select(11), chests("A"), player)
			expectFailure(outcome)
			val after = expectClean()
			expectEquals(10, after.chest("A", STONE), "stone in A")
		},

		scenario("fails cleanly when source and destination are the same chest") {
			chest("A", stone(64))
			val outcome = transfer(STONE.select(64), chests("A"), chests("A"))
			expectFailure(outcome)
			val after = expectClean()
			expectEquals(64, after.chest("A", STONE), "stone in A")
		},

		scenario("trips respect a disallowed hotbar") {
			chest("A", stone(64))
			chest("B")
			var stoneSeenInHotbar = false
			val outcome = transfer(
				STONE.select(64), chests("A"), chests("B"),
				automated = automatedWithout(ContainerType.Hotbar),
				onTick = { client ->
					val hotbar = client.player?.inventory?.mainStacks?.take(9).orEmpty()
					if (hotbar.any { it.item == STONE }) stoneSeenInHotbar = true
				}
			)
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(64, after.chest("B", STONE), "stone in B")
			expect(!stoneSeenInHotbar) { "stone was staged in the hotbar although the hotbar is not an allowed container" }
		},

		scenario("an inventory packet budget of one still completes whole stack moves") {
			chest("A", stone(64), stone(64), stone(64))
			val outcome = transfer(STONE.select(192), chests("A"), player, automated = automatedWithInventoryLimit(1))
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(192, after.player(STONE), "stone with the player")
		},

		// ---- complex / messy scenarios -------------------------------------------------------------------

		scenario("wildcard ANYTHING pulls every item from a mixed chest") {
			// A chest with a hodgepodge of blocks: stone, dirt, cobble, sand, planks. Player inventory has
			// random junk too. ANYTHING with count 0 should empty the entire chest into the player.
			chest("A", stone(64), dirt(32), cobble(48), sand(16), planks(10))
			hotbar(0, glass(64))
			hotbar(3, bricks(30))
			inventory(5, gravel(20))
			val outcome = transfer(StackSelection.ANYTHING, chests("A"), player)
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(0, after.chest("A", STONE) + after.chest("A", DIRT) + after.chest("A", COBBLE) +
				after.chest("A", SAND) + after.chest("A", OAK_PLANKS), "nothing should remain in A")
			expectEquals(64, after.player(STONE), "stone with the player")
			expectEquals(32, after.player(DIRT), "dirt with the player")
			expectEquals(48, after.player(COBBLE), "cobblestone with the player")
			expectEquals(16, after.player(SAND), "sand with the player")
			expectEquals(10, after.player(OAK_PLANKS), "planks with the player")
			// pre-existing junk must still be there
			expectEquals(64, after.player(GLASS), "glass with the player")
			expectEquals(30, after.player(BRICKS), "bricks with the player")
			expectEquals(20, after.player(GRAVEL), "gravel with the player")
		},

		scenario("exclusion-based selection moves everything except one item type") {
			// Move everything from A except cobblestone. Player already holds some gravel.
			chest("A", stone(64), cobble(64), dirt(64), sand(32))
			hotbar(0, gravel(15))
			val selection = stackSelection(0) { notItem(COBBLE) }
			val outcome = transfer(selection, chests("A"), player)
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(64, after.chest("A", COBBLE), "cobblestone must stay in A")
			expectEquals(0, after.chest("A", STONE), "stone must leave A")
			expectEquals(0, after.chest("A", DIRT), "dirt must leave A")
			expectEquals(0, after.chest("A", SAND), "sand must leave A")
			expectEquals(64, after.player(STONE), "stone with the player")
			expectEquals(64, after.player(DIRT), "dirt with the player")
			expectEquals(32, after.player(SAND), "sand with the player")
			expectEquals(15, after.player(GRAVEL), "pre-existing gravel untouched")
		},

		scenario("multi-item partial move with a cluttered player inventory") {
			// Only move 20 total of stone+dirt from A, but the player's inventory is nearly full.
			// Only hotbar slot 8 and inventory slots 25-26 are free.
			chest("A", stone(64), dirt(64), cobble(64))
			// Fill most of the player: 8 hotbar slots + 25 inventory slots = 33 slots used
			repeat(8) { hotbar(it, glass(64)) }
			repeat(25) { inventory(it, bricks(64)) }
			val selection = stackSelection(20) { ofAnyItems(listOf(STONE, DIRT)) }
			val outcome = transfer(selection, chests("A"), player)
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(20, after.player(STONE) + after.player(DIRT), "exactly 20 stone+dirt with the player")
			expectEquals(0, after.player(COBBLE), "cobblestone must not leak out")
			// pre-existing items unchanged
			expectEquals(8 * 64, after.player(GLASS), "glass with the player")
			expectEquals(25 * 64, after.player(BRICKS), "bricks with the player")
		},

		scenario("chest-to-chest transfer with fully cluttered player inventory needs trips") {
			// Move 64 stone from A to B, but the player has only 2 free slots.
			// Task must still succeed using trips, even with very limited staging space.
			chest("A", stone(64), stone(64))
			chest("B", dirt(10))
			// Fill 34 of 36 player slots
			repeat(9) { hotbar(it, glass(64)) }
			repeat(25) { inventory(it, bricks(64)) }
			// Free up exactly 2 slots
			server.runCommand("/item replace entity $PLAYER hotbar.7 with minecraft:air")
			server.runCommand("/item replace entity $PLAYER hotbar.8 with minecraft:air")
			val outcome = transfer(STONE.select(128), chests("A"), chests("B"))
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(128, after.chest("B", STONE), "stone in B")
			expectEquals(0, after.chest("A", STONE), "stone in A")
			expectEquals(0, after.player(STONE), "stone stranded with the player")
		},

		scenario("shulker box scope finds the right shulker among junk-filled chests") {
			// Two chests: A has random junk + a shulker containing diamonds among junk.
			// B has only junk. The task should find and pull the shulker from A.
			val junkShulker = Spec("shulker_box", 1, "[container=[{slot:0,item:{id:\"minecraft:gravel\",count:64}}]]")
			val targetShulker = Spec("shulker_box", 1,
				"[container=[{slot:0,item:{id:\"minecraft:diamond\",count:32}},{slot:1,item:{id:\"minecraft:sand\",count:64}},{slot:2,item:{id:\"minecraft:dirt\",count:64}}]]")
			chest("A", stone(64), targetShulker, dirt(64), junkShulker, cobble(64))
			// Player has random stuff too
			hotbar(0, glass(10))
			inventory(0, sand(32))
			val outcome = transfer(
				stackSelection(1) {
					isItem(Items.DIAMOND)
					alsoInShulkerBoxes()
				},
				chests("A"),
				player
			)
			expectSuccess(outcome)
			val after = expectClean()
			// The shulker box with the diamond must be with the player now
			expectEquals(1, after.player(Items.SHULKER_BOX), "shulker with the player")
			// The other shulker (with gravel) must still be in A
			expectEquals(64, after.chest("A", STONE), "stone stays in A")
			expectEquals(64, after.chest("A", COBBLE), "cobble stays in A")
		},

		scenario("multi-source broad move collects from several chests into one destination") {
			// Three source chests with mixed items. Move everything into a double chest.
			chest("A", stone(64), dirt(64), sand(32))
			chest("B", cobble(64), planks(64), glass(20))
			chest("C", gravel(48), bricks(64))
			doubleChest()
			val outcome = transfer(StackSelection.ANYTHING, chests("A", "B", "C"), chests("E"))
			expectSuccess(outcome)
			val after = expectClean()
			// All three sources should be empty
			expectEquals(0, after.chest("A", STONE) + after.chest("A", DIRT) + after.chest("A", SAND), "A should be empty")
			expectEquals(0, after.chest("B", COBBLE) + after.chest("B", OAK_PLANKS) + after.chest("B", GLASS), "B should be empty")
			expectEquals(0, after.chest("C", GRAVEL) + after.chest("C", BRICKS), "C should be empty")
			// Everything should be in the double chest
			expectEquals(64, after.chest("E", STONE), "stone in E")
			expectEquals(64, after.chest("E", DIRT), "dirt in E")
			expectEquals(32, after.chest("E", SAND), "sand in E")
			expectEquals(64, after.chest("E", COBBLE), "cobble in E")
			expectEquals(64, after.chest("E", OAK_PLANKS), "planks in E")
			expectEquals(20, after.chest("E", GLASS), "glass in E")
			expectEquals(48, after.chest("E", GRAVEL), "gravel in E")
			expectEquals(64, after.chest("E", BRICKS), "bricks in E")
			expectEquals(0, after.player(STONE) + after.player(DIRT) + after.player(COBBLE), "nothing stranded with the player")
		},

		scenario("broad exclusion move between chests does not strand player items") {
			// Move everything except sand from A to B. Player already has some sand (the excluded
			// item) — those must survive. Also verify the transfer itself is correct.
			chest("A", stone(64), dirt(64), sand(64), cobble(32), planks(16))
			chest("B")
			hotbar(0, sand(10))
			inventory(0, sand(5))
			val outcome = transfer(stackSelection(0) { notItem(SAND) }, chests("A"), chests("B"))
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(64, after.chest("A", SAND), "sand must stay in A")
			expectEquals(0, after.chest("A", STONE), "stone must leave A")
			expectEquals(64, after.chest("B", STONE), "stone in B")
			expectEquals(64, after.chest("B", DIRT), "dirt in B")
			expectEquals(32, after.chest("B", COBBLE), "cobble in B")
			expectEquals(16, after.chest("B", OAK_PLANKS), "planks in B")
			expectEquals(15, after.player(SAND), "player's own sand untouched")
		},

		scenario("large wildcard move across trips with mixed item types") {
			// Large broad transfer (everything) from two full chests of different items to a double chest.
			// This exercises multi-trip wildcard moves where the staging area cycles through many item types.
			fullChest("A", stone(64))
			chest("C", *Array(27) { if (it % 2 == 0) dirt(64) else planks(64) })
			doubleChest()
			val outcome = transfer(StackSelection.ANYTHING, chests("A", "C"), chests("E"))
			expectSuccess(outcome)
			val after = expectClean()
			expectEquals(27 * 64, after.chest("E", STONE), "stone in the double chest")
			expectEquals(14 * 64, after.chest("E", DIRT), "dirt in the double chest")
			expectEquals(13 * 64, after.chest("E", OAK_PLANKS), "planks in the double chest")
			expectEquals(0, after.chest("A", STONE), "stone left in A")
			expectEquals(0, after.chest("C", DIRT) + after.chest("C", OAK_PLANKS), "items left in C")
			expectEquals(0, after.player(STONE) + after.player(DIRT) + after.player(OAK_PLANKS),
				"nothing stranded with the player")
		},

		// Last on purpose: a task that never settles leaves "open container" packets in flight.
		scenario("a destination that reports space but accepts nothing settles instead of looping") {
			chest("A", stone(64))
			chest("B")
			val outcome = transfer(STONE.select(64), chests("A"), chests("B"), StackSelection.NOTHING)
			expect(outcome.state != Task.State.Running && outcome.state != Task.State.Paused) {
				"task was still ${outcome.state} after ${outcome.ticks} ticks"
			}
			expectClean()
		}
	)
}
