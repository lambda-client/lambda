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

package com.lambda.module.modules.world

import baritone.api.pathing.goals.GoalBlock
import com.lambda.Lambda.mc
import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.config.settings.complex.Bind
import com.lambda.config.settings.complex.KeybindSetting.Companion.onPress
import com.lambda.context.SafeContext
import com.lambda.event.events.ButtonEvent
import com.lambda.event.events.ChatEvent
import com.lambda.event.events.GuiEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.graphics.mc.renderer.TickedRenderer.Companion.tickedRenderer
import com.lambda.interaction.BaritoneManager
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.managers.hotbar.HotbarRequest
import com.lambda.interaction.managers.interacting.InteractConfig
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.Rotation.Companion.dist
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.interaction.material.container.containers.EnderChestContainer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.task.tasks.OpenContainerTask
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils.blockEntity
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Communication.info
import com.lambda.util.Communication.logError
import com.lambda.util.Communication.warn
import com.lambda.util.NamedEnum
import com.lambda.util.TickTimer
import com.lambda.util.extension.containerSlots
import com.lambda.util.extension.containerStacks
import com.lambda.util.extension.rotation
import com.lambda.util.math.distSq
import com.lambda.util.math.setAlpha
import com.lambda.util.player.SlotUtils.allSlots
import com.lambda.util.player.SlotUtils.hotbarAndInventorySlots
import com.lambda.util.player.SlotUtils.hotbarAndInventoryStacks
import com.lambda.util.player.SlotUtils.hotbarSlots
import com.lambda.util.player.SlotUtils.hotbarStacks
import com.lambda.util.player.SlotUtils.inventoryStacks
import com.lambda.util.player.SlotUtils.offHandSlots
import com.lambda.util.text.bold
import com.lambda.util.text.buildText
import com.lambda.util.text.color
import com.lambda.util.text.literal
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.Blocks
import net.minecraft.block.ButtonBlock
import net.minecraft.block.entity.LootableContainerBlockEntity
import net.minecraft.client.gui.screen.DeathScreen
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.state.property.Properties
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.min
import kotlin.run
import kotlin.to

@Suppress("unused")
object StashMover : Module(
	name = "StashMover",
	description = "Moves items from one stash location to another",
	tag = ModuleTag.WORLD
) {
	private enum class Group(override val displayName: String) : NamedEnum {
		General("General"),
		CommandBinds("Command Binds")
	}

	enum class Role(val createTask: () -> Task<*>) {
		MoverBot({ MoverBot() }),
		PearlBot({ PearlBot() })
	}

	private enum class DropOffMode(override val displayName: String) : NamedEnum {
		Chests("Chests"),
		Drop("Drop")
	}

	val role: Role by setting("Role", Role.MoverBot).group(Group.General)
		.onValueChange { _, _ -> clearModule() }
	private val pearlBotName by setting("PearlBot Name", "Steve") { role == Role.MoverBot }.group(Group.General)
	private val moverBotName by setting("MoverBot Name", "Steve") { role == Role.PearlBot }.group(Group.General)
	private val dropOffMode by setting("Drop-Off Mode", DropOffMode.Chests) { role == Role.MoverBot }.group(Group.General)
	private var chestPullSelMode: Boolean by setting("Chest Pull Sel Mode", false, "Enables the mode to select the stash containers you want to move items from") { role == Role.MoverBot }.group(Group.General)
		.onValueChange { _, to -> if (to) { chestPutSelMode = false; StashMover.info("Enabled chest pull selection mode!") } }
	private var chestPutSelMode: Boolean by setting("Chest Put Sel Mode", false, "Enables the mod to select the stash containers you want to move items into") { role == Role.MoverBot }.group(Group.General)
		.onValueChange { _, to -> if (to) { chestPullSelMode = false; StashMover.info("Enabled chest put selection mode!") } }
	private val pearlMsgTimeout by setting("Pearl Msg Timeout", 100, 0..1500, 1, "Ticks before messaging the pearl bot again", "ticks") { role == Role.MoverBot }.group(Group.General)
	private val pearlButtonTimeout by setting("Pearl Button Timeout", 100, 0..1500, 1, "Ticks before pressing the pearl dispenser button again", "ticks") { role == Role.MoverBot }.group(Group.General)
	private val killRespawnTimeout by setting("Kill/Respawn Timeout", 100, 0..1500, 1, "Ticks before sending the kill command or attempting to respawn again", "ticks") { role == Role.MoverBot }.group(Group.General)
	private val actionDelay by setting("Action Delay", 3, 0..20, 1, "The delay after performing one action, before the next") { role == Role.MoverBot }.group(Group.General)
	private val useEnderChest by setting("Use Ender Chest", false, "Uses the ender chest to move more items at once. (Ender chests are included in the pull/put selections)") { role == Role.MoverBot }.group(Group.General)
		.onValueChange { _, to ->
			if (!to) {
				putEnderChests.clear()
				pullEnderChests.clear()
			}
		}
	private val breakEmptyPullContainers by setting("Break Empty Pull Containers", false, "Breaks empty pull containers after taking their items") { role == Role.MoverBot }.group(Group.General)
	private val disconnectOnFinish by setting("Disconnect On Finish", false, "Disconnects the mover bot when it's finished") { role == Role.MoverBot }.group(Group.General)
	private val disconnectOnFail by setting("Disconnect On Fail", false, "Disconnects the mover bot if it fails") { role == Role.MoverBot }.group(Group.General)
	private val startStop by setting("Start/Stop", Bind.EMPTY, "Starts and stops the selected role").group(Group.General)
		.onPress { event ->
			event.cancel()
			startStop()
		}
	private val pauseUnpause by setting("Pause/Unpause", Bind.EMPTY, "Pauses and unpauses the selected role").group(Group.General)
		.onPress { event ->
			event.cancel()
			pauseUnpause()
		}

	private val indexSelectedContainers by setting("Index Selected Containers", Bind.EMPTY, "Indexes the selected containers to pull/push items from/to") { role == Role.MoverBot }.group(Group.CommandBinds)
		.onPress { event ->
			event.cancel()
			indexSelectedContainers()
		}
	private val removeSelectedContainers by setting("Remove Selected Containers", Bind.EMPTY, "Removes the selected containers from being pull/pushed from/to") { role == Role.MoverBot }.group(Group.CommandBinds)
		.onPress { event ->
			event.cancel()
			removeSelectedContainers()
		}
	private val setItemThrowPosAndRotation by setting("Set Item Throw", Bind.EMPTY, "Sets the item throw position and rotation. (This is usually set to throw into hoppers to pickup the items)") { role == Role.MoverBot }.group(Group.CommandBinds)
		.onPress { event ->
			event.cancel()
			setItemThrow()
		}
	private val setPearlButtonPos by setting("Set Pearl Button Pos", Bind.EMPTY, "Sets the button used to dispense a pearl for the player") { role == Role.MoverBot }.group(Group.CommandBinds)
		.onPress { event ->
			event.cancel()
			setPearlButtonPos()
		}
	private val setPearlThrowPosAndRotation by setting("Set Pearl Throw", Bind.EMPTY, "Sets the pearl throw position and rotation. (This is best if you throw somewhat sideways into a line of bubble columns)") { role == Role.MoverBot }.group(Group.CommandBinds)
		.onPress { event ->
			event.cancel()
			setPearlThrow()
		}
	private val setPearlBotButton by setting("Set PearlBot Button", Bind.EMPTY, "Sets the button position for the pearl bot to press to load the mover bot") { role == Role.PearlBot }.group(Group.CommandBinds)
		.onPress { event ->
			event.cancel()
			setPearlBotButton()
		}

	private var sel1: BlockPos? = null
	private var sel2: BlockPos? = null

	private val pullContainers = hashSetOf<BlockPos>()
	private val putEnderChests = hashSetOf<BlockPos>()
	private val pulledContainers = hashSetOf<BlockPos>()
	private val putContainers = hashSetOf<BlockPos>()
	private val pullEnderChests = hashSetOf<BlockPos>()
	private val filledContainers = hashSetOf<BlockPos>()

	private var itemThrowPos: BlockPos? = null
	private var itemThrowRotation: Rotation? = null

	private var pearlDispensePos: BlockPos? = null
	private var pearlThrowPos: BlockPos? = null
	private var pearlRotation: Rotation? = null

	private var pearlBotButton: BlockPos? = null

	private var task: Task<*>? = null

	init {
		setModulePriority(100)
		setDefaultAutomationConfig {
			applyEdits {
				buildConfig.apply {
					editTyped(::pathing, ::stayInRange, ::checkSideVisibility) { defaultValue(true) }
					hide(::pathing, ::stayInRange, ::collectDrops, ::spleefEntities, ::entityReach)
					hideGroup(eatConfig)
				}
				interactConfig::airPlace.edit { defaultValue(InteractConfig.AirPlaceMode.None) }
				breakConfig.apply {
					editTyped(::suitableToolsOnly, ::efficientOnly) { defaultValue(false) }
				}
			}
		}

		listen<ButtonEvent.Mouse.Click> { event ->
			if (!chestPullSelMode && !chestPutSelMode) return@listen
			if (event.action != GLFW.GLFW_PRESS || mc.currentScreen != null) return@listen

			if (event.button == 0) {
				event.cancel()
				sel1 = mc.crosshairTarget?.blockResult?.blockPos ?: return@listen
			}
			else if (event.button == 1) {
				event.cancel()
				sel2 = mc.crosshairTarget?.blockResult?.blockPos ?: return@listen
			}
		}

		onDisable { clearModule() }

		tickedRenderer("StashMover Immediate Renderer") {
			if (!chestPullSelMode && !chestPutSelMode) return@tickedRenderer
			sel1?.let { sel1 ->
				box(Box(sel1)) {
					colors(Color.PINK.setAlpha(0.1), Color.PINK)
				}
			}
			sel2?.let { sel2 ->
				box(Box(sel2)) {
					colors(Color.MAGENTA.setAlpha(0.1), Color.MAGENTA)
				}
			}
			sel1?.let { sel1 ->
				sel2?.let { sel2 ->
					val box = Box(sel1).union(Box(sel2))
					box(box) {
						val color = if (chestPutSelMode) Color.GREEN else Color.BLUE
						colors(color.setAlpha(0.1), color)
					}
				}
			}
		}
	}

	private fun clearModule() {
		task?.cancel()
		task = null
		sel1 = null
		sel2 = null
		pullContainers.clear()
		putEnderChests.clear()
		pulledContainers.clear()
		putContainers.clear()
		pullEnderChests.clear()
		filledContainers.clear()
		pearlDispensePos = null
		pearlThrowPos = null
		pearlRotation = null
		pearlBotButton = null
		itemThrowPos = null
		itemThrowRotation = null
	}

	context(safeContext: SafeContext)
	fun indexSelectedContainers() {
		var addCount = 0
		consumeSelection { pos ->
			pulledContainers.remove(pos)
			filledContainers.remove(pos)
			if (useEnderChest && safeContext.blockState(pos).block === Blocks.ENDER_CHEST) {
				addCount++
				if (chestPullSelMode) {
					pullEnderChests.remove(pos)
					putEnderChests.add(pos)
				} else if (chestPutSelMode) {
					putEnderChests.remove(pos)
					pullEnderChests.add(pos)
				}
				return@consumeSelection
			}
			if (safeContext.blockEntity(pos) !is LootableContainerBlockEntity) return@consumeSelection
			addCount++
			if (chestPullSelMode) {
				putContainers.remove(pos)
				pullContainers.add(pos)
			} else if (chestPutSelMode) {
				pullContainers.remove(pos)
				putContainers.add(pos)
			}
		}
		StashMover.info("Indexed $addCount ${if (chestPullSelMode) "pull" else "put"} containers!")
	}

	context(safeContext: SafeContext)
	fun removeSelectedContainers() {
		var removeCount = 0
		consumeSelection { pos ->
			if (safeContext.blockEntity(pos) !is LootableContainerBlockEntity) return@consumeSelection
			if (pullContainers.remove(pos) ||
				pulledContainers.remove(pos) ||
				putContainers.remove(pos) ||
				filledContainers.remove(pos)) removeCount++
		}
		StashMover.info("Removed $removeCount containers!")
	}

	context(safeContext: SafeContext)
	fun setItemThrow() {
		itemThrowPos = safeContext.player.blockPos
		itemThrowRotation = safeContext.player.rotation
	}

	context(safeContext: SafeContext)
	fun setPearlButtonPos() {
		val pos = mc.crosshairTarget?.blockResult?.blockPos ?: return
		if (safeContext.blockState(pos).block !is ButtonBlock) {
			StashMover.warn("Given position does not contain a button!")
			return
		}
		pearlDispensePos = pos
		StashMover.info("Set pearl button position!")
	}

	context(safeContext: SafeContext)
	fun setPearlThrow() {
		pearlThrowPos = safeContext.player.blockPos
		pearlRotation = safeContext.player.rotation
		StashMover.info("Set pearl throw position and rotation!")
	}

	context(safeContext: SafeContext)
	fun setPearlBotButton() {
		val pos = mc.crosshairTarget?.blockResult?.blockPos ?: return
		if (safeContext.blockState(pos).block !is ButtonBlock) {
			StashMover.warn("Given position does not contain a button!")
			return
		}
		pearlBotButton = pos
		StashMover.info("Set pearl bot button position!")
	}

	fun startStop() {
		if (isDisabled) return
		chestPullSelMode = false
		chestPutSelMode = false
		task?.let { runningTask ->
			runningTask.cancel()
			task = null
			return
		}
		StashMover.info("Starting ${if (role == Role.MoverBot) "MoverBot" else "PearlBot"}!")
		task = role
			.createTask()
			.onFailOrNull {
				if (disconnectOnFail) connection.connection.disconnect(
					buildText {
						bold {
							literal("StashMover")
							color(Color.RED) { literal(" Failed") }
						}
					}
				)
				task = null
				null
			}
			.finally { message ->
				StashMover.info("Finished! $message")
				if (disconnectOnFinish) connection.connection.disconnect(
					buildText {
						bold {
							literal("StashMover")
							color(Color.GREEN) { literal(" Finished!") }
						}
					}
				)
				task = null
			}
			.run()
	}

	fun pauseUnpause() {
		if (isDisabled) return
		if (task?.isMuted == true) task?.activate()
		else task?.pause()
	}

	private fun consumeSelection(callback: (pos: BlockPos) -> Unit) {
		sel1?.let { sel1 ->
			sel2?.let { sel2 ->
				val minX = minOf(sel1.x, sel2.x)
				val maxX = maxOf(sel1.x, sel2.x)
				val minY = minOf(sel1.y, sel2.y)
				val maxY = maxOf(sel1.y, sel2.y)
				val minZ = minOf(sel1.z, sel2.z)
				val maxZ = maxOf(sel1.z, sel2.z)
				(minX..maxX).forEach { x ->
					(minZ..maxZ).forEach { z ->
						(minY..maxY).forEach { y ->
							callback.invoke(BlockPos(x, y, z))
						}
					}
				}
			}
		}
		sel1 = null
		sel2 = null
	}

	private class MoverBot : Task<String>() {
		override val name
			get() = "Moving a stash, pearled by $pearlBotName, current state: $moverState"
		private var moverState = MoverState.TakingItems
			set(newValue) {
				actionDelayTimer.reset()
				delayingNextAction = true
				field = newValue
			}

		private var actionDelayTimer = TickTimer()
		private var delayingNextAction = false

		private var pearlThrown = false

		private var pullContainer: BlockPos? = null
		private var tickTimer = TickTimer()

		private var putContainer: BlockPos? = null

		private var finished = false
		private var finishedMessage = ""

		init {
			listen<TickEvent.Pre> {
				if (delayingNextAction) {
					actionDelayTimer.tick()
					if (!actionDelayTimer.hasSurpassed(actionDelay)) return@listen
					delayingNextAction = false
				}

				val screenHandler = player.currentScreenHandler

				when (moverState) {
					MoverState.OpeningPullContainer ->
						openClosestContainer(
							pullContainers,
							{
								finished = true
								finishedMessage = "Pull containers exhausted!"
								breakPulledOrPearl()
							}
						) { pos ->
							pullContainer = pos
							moverState = MoverState.TakingItems
						}
					MoverState.TakingItems -> handleTakingItems(screenHandler)
					MoverState.OpeningPutEnderChest ->
						openClosestContainer(
							putEnderChests,
							{ failWithLog("No pull ender chests indexed!") }
						) { moverState = MoverState.PuttingInEnderChest }
					MoverState.PuttingInEnderChest -> handlePuttingInEnderChest(screenHandler)
					MoverState.BreakingEmptyPullContainers -> handleBreakingEmptyPullContainers()
					MoverState.MessagingForPearl -> handleMessagingForPearl()
					MoverState.AwaitingTeleport ->
						checkTimerProgress(
							MoverState.MessagingForPearl,
							pearlMsgTimeout
						)
					MoverState.DispensingPearl -> handleDispensingPearl()
					MoverState.AwaitingPearl ->
						checkTimerProgress(MoverState.DispensingPearl, pearlButtonTimeout) {
							if (player.hotbarAndInventoryStacks.any { it.item === Items.ENDER_PEARL }) {
								pearlThrown = false
								moverState = MoverState.ThrowingPearl
								true
							} else false
						}
					MoverState.ThrowingPearl -> handleThrowingPearl()
					MoverState.DroppingItems -> handleDroppingItems()
					MoverState.OpeningPutContainer ->
						openClosestContainer(
							putContainers,
							{ success("Put containers are full!") }
						) { pos ->
							putContainer = pos
							moverState = MoverState.PuttingItems
						}
					MoverState.PuttingItems -> handlePuttingItems(screenHandler)
					MoverState.OpeningPullEnderChest ->
						openClosestContainer(
							pullEnderChests,
							{ failWithLog("No put ender chests indexed!") }
						) { moverState = MoverState.PullingFromEnderChest }
					MoverState.PullingFromEnderChest -> handlePullingFromEnderChest(screenHandler)
					MoverState.Killing -> handleKilling()
					else -> {}
				}
			}

			listenUnsafe<TickEvent.Pre> {
				when (moverState) {
					MoverState.AwaitingDeath -> checkTimerProgress(MoverState.Killing, killRespawnTimeout)
					MoverState.Respawning -> handleRespawning()
					MoverState.AwaitingRespawn -> checkTimerProgress(MoverState.Respawning, killRespawnTimeout)
					else -> {}
				}
			}

			listenUnsafe<PacketEvent.Receive.Pre> { event ->
				if (moverState != MoverState.AwaitingRespawn) return@listenUnsafe
				if (event.packet !is PlayerRespawnS2CPacket) return@listenUnsafe
				moverState = MoverState.OpeningPullContainer
			}

			listen<PacketEvent.Receive.Pre> { event ->
				if (moverState != MoverState.AwaitingTeleport) return@listen
				val packet = event.packet
				if (packet !is PlayerPositionLookS2CPacket) return@listen
				if (finished && player.hotbarAndInventoryStacks.all { it.isEmpty }) {
					success(finishedMessage)
					return@listen
				}
				moverState = MoverState.DispensingPearl
			}

			listen<GuiEvent.ScreenOpen> { event ->
				if (moverState != MoverState.AwaitingDeath) return@listen
				if (event.screen !is DeathScreen) return@listen
				moverState = MoverState.Respawning
			}
		}

		private fun SafeContext.handleTakingItems(screenHandler: ScreenHandler) {
			if (screenHandler === player.playerScreenHandler) {
				moverState = MoverState.OpeningPullContainer
				return
			}
			if (!moveFromContainerToContainer(screenHandler.containerSlots, player.hotbarAndInventoryStacks)) return
			pullContainer?.let { container ->
				if (screenHandler.containerStacks.all { it.isEmpty }) {
					pullContainers.remove(container)
					pulledContainers.add(container)
					if (player.hotbarAndInventoryStacks.any { it.isEmpty }) {
						moverState = MoverState.OpeningPullContainer
						return
					}
				}
			}
			if (useEnderChest && (EnderChestContainer.stacks.isEmpty() || EnderChestContainer.stacks.any { it.isEmpty })) {
				moverState = MoverState.OpeningPutEnderChest
				return
			}
			breakPulledOrPearl()
		}

		private fun SafeContext.handlePuttingInEnderChest(screenHandler: ScreenHandler) {
			if (screenHandler === player.playerScreenHandler) {
				moverState = MoverState.OpeningPutEnderChest
				return
			}
			if (moveFromContainerToContainer(player.hotbarAndInventorySlots, screenHandler.containerStacks)) {
				moverState = MoverState.OpeningPullContainer
			}
		}

		private fun handleBreakingEmptyPullContainers() {
			pulledContainers
				.associateWith { TargetState.Empty }
				.build()
				.onFailOrNull {
					failWithLog("Failed to break empty pull containers!")
					null
				}
				.finally {
					pulledContainers.clear()
					moverState = MoverState.MessagingForPearl
				}
				.execute(this)
		}

		private fun SafeContext.handleMessagingForPearl() {
			tickTimer.reset()
			connection.sendChatCommand("msg $pearlBotName ${Math.random() * Double.MAX_VALUE}")
			moverState = MoverState.AwaitingTeleport
		}

		private fun SafeContext.handleDroppingItems() {
			val throwPos = itemThrowPos ?: run { failWithLog("No item throw pos set!"); return }
			if (player.blockPos != throwPos) {
				BaritoneManager.setGoalAndPath(GoalBlock(throwPos))
				return
			}
			if (BaritoneManager.isActive) return
			val rotation = itemThrowRotation ?: run { failWithLog("No item throw rotation set!"); return }
			val rotationRequest = rotationRequest {
				rotation(rotation)
			}.submit()
			if (!rotationRequest.done || rotation dist RotationManager.serverRotation > 0.001) return
			val throwSlots = player.hotbarAndInventorySlots.filter { !it.stack.isEmpty }
			if (throwSlots.isNotEmpty()) {
				val inventoryRequest = inventoryRequest(settleForLess = true) {
					throwSlots.forEach { slot ->
						throwStack(slot.id)
					}
				}.submit()
				if (!inventoryRequest.done) return
			}
			pullFromEnderChestOrContinue()
		}

		private fun SafeContext.handlePuttingItems(screenHandler: ScreenHandler) {
			if (screenHandler === player.playerScreenHandler) {
				moverState = MoverState.OpeningPutContainer
				return
			}
			if (!moveFromContainerToContainer(player.hotbarAndInventorySlots, screenHandler.containerStacks)) return
			putContainer?.let { container ->
				if (screenHandler.containerStacks.all { !it.isEmpty }) {
					putContainers.remove(container)
					filledContainers.add(container)
					if (player.hotbarAndInventoryStacks.any { !it.isEmpty }) {
						moverState = MoverState.OpeningPutContainer
						return
					}
				}
			}
			pullFromEnderChestOrContinue()
		}

		private fun SafeContext.handlePullingFromEnderChest(screenHandler: ScreenHandler) {
			if (screenHandler === player.playerScreenHandler) {
				moverState = MoverState.OpeningPullEnderChest
				return
			}
			if (moveFromContainerToContainer(screenHandler.containerSlots, player.hotbarAndInventoryStacks)) {
				putOrThrowItems()
			}
		}

		private fun SafeContext.handleDispensingPearl() {
			val dispensePos = pearlDispensePos ?: run { failWithLog("No pearl button set!"); return }
			if (player.blockPos != dispensePos) {
				BaritoneManager.setGoalAndPath(GoalBlock(dispensePos))
				return
			}
			if (BaritoneManager.isActive) return
			if (player.hotbarStacks.none { it.isEmpty }) {
				val firstSlot = player.hotbarSlots.getOrNull(0) ?: run { failWithLog("No first slot? This shouldn't occur."); return }
				if (player.inventoryStacks.any { it.isEmpty }) {
					inventoryRequest { quickMove(firstSlot.id) }.submit()
					return
				} else if (player.offHandStack.isEmpty) {
					inventoryRequest { swap(firstSlot.id, 40) }.submit()
					return
				}
				failWithLog("No free slots for an ender pearl!")
				return
			}
			getButtonPressTask(dispensePos)
				?.finally {
					tickTimer.reset()
					moverState = MoverState.AwaitingPearl
				}
				?.execute(this@MoverBot)
		}

		private fun SafeContext.handleThrowingPearl() {
			val throwPos = pearlThrowPos ?: run { failWithLog("No pearl throw pos set!"); return }
			if (player.blockPos != throwPos) {
				BaritoneManager.setGoalAndPath(GoalBlock(throwPos))
				return
			}
			if (BaritoneManager.isActive) return
			if (player.velocity.y < -0.08 || player.velocity.x !in -0.001..0.001 || player.velocity.z !in -0.001..0.001) return

			if (pearlThrown) {
				if (!player.offHandStack.isEmpty) {
					if (player.hotbarAndInventoryStacks.none { it.isEmpty }) {
						failWithLog("No free slots to return the offhand stack to!")
						return
					}
					val offhandSlot = player.offHandSlots.firstOrNull() ?: run { failWithLog("No offhand slot? This shouldn't occur."); return }
					inventoryRequest { quickMove(offhandSlot.id) }.submit()
				}
				putOrThrowItems()
				return
			}

			val rotation = pearlRotation ?: run { failWithLog("No pearl rotation set!"); return }
			val rotationRequest = rotationRequest {
				rotation(rotation)
			}.submit()
			if (!rotationRequest.done) return
			if (player.mainHandStack.item != Items.ENDER_PEARL) {
				val hotbarSlot = player.hotbarSlots.firstOrNull { it.stack.item === Items.ENDER_PEARL }
				if (hotbarSlot != null) {
					val hotbarRequest = HotbarRequest(hotbarSlot.index, StashMover, nowOrNothing = false).submit()
					if (!hotbarRequest.done) return
				} else {
					val inventorySlot = player.allSlots.firstOrNull { it.stack.item === Items.ENDER_PEARL }
					if (inventorySlot == null) {
						failWithLog("No pearl in inventory!")
						return
					}
					inventoryRequest { swap(inventorySlot.id, 0) }.submit()
					return
				}
			}
			RotationManager.withoutVanillaOverrides {
				interaction.interactItem(player, Hand.MAIN_HAND)
				pearlThrown = true
			}
		}

		private fun SafeContext.handleKilling() {
			tickTimer.reset()
			connection.sendChatCommand("kill")
			moverState = MoverState.AwaitingDeath
		}

		private fun handleRespawning() {
			tickTimer.reset()
			mc.networkHandler?.sendPacket(ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN))
			moverState = MoverState.AwaitingRespawn
		}

		private fun breakPulledOrPearl() {
			moverState =
				if (breakEmptyPullContainers && pulledContainers.isNotEmpty()) MoverState.BreakingEmptyPullContainers
				else MoverState.MessagingForPearl
		}

		private fun pullFromEnderChestOrContinue() {
			if (useEnderChest && (EnderChestContainer.stacks.isEmpty() || EnderChestContainer.stacks.any { !it.isEmpty })) {
				moverState = MoverState.OpeningPullEnderChest
				return
			}
			if (finished) {
				success(finishedMessage)
				return
			}
			moverState = MoverState.Killing
		}

		private fun putOrThrowItems() {
			moverState =
				when (dropOffMode) {
					DropOffMode.Chests -> MoverState.OpeningPutContainer
					DropOffMode.Drop -> MoverState.DroppingItems
				}
		}

		private fun SafeContext.openClosestContainer(
			positions: Collection<BlockPos>,
			onNoneAvailable: () -> Unit,
			finally: SafeContext.(pos: BlockPos) -> Unit
		) {
			val pos = positions.minByOrNull { it distSq player.blockPos }
				?: run {
					onNoneAvailable()
					return
				}

			OpenContainerTask(
				pos,
				StashMover
			).finally {
				finally(pos)
			}.execute(this@MoverBot)
		}

		private fun SafeContext.moveFromContainerToContainer(
			from: Collection<Slot>,
			to: Collection<ItemStack>
		): Boolean {
			val filteredFrom = from.filter { !it.stack.isEmpty }
			val filteredTo = to.filter { it.isEmpty }
			if (filteredTo.isEmpty() || filteredFrom.isEmpty()) {
				player.closeHandledScreen()
				return true
			}
			val moveSlots = filteredFrom.subList(0, min(filteredTo.size, filteredFrom.size))
			if (moveSlots.isNotEmpty()) {
				val request = inventoryRequest(settleForLess = true) {
					moveSlots.forEach { slot ->
						quickMove(slot.id)
					}
				}.submit()
				if (!request.done) return false
			}
			player.closeHandledScreen()
			return true
		}

		private fun checkTimerProgress(
			fallbackState: MoverState,
			timeout: Int,
			progressionCheck: (() -> Boolean)? = null
		) {
			tickTimer.tick()
			if (progressionCheck?.invoke() == true) return
			if (tickTimer.hasSurpassed(timeout)) moverState = fallbackState
		}

		private enum class MoverState {
			OpeningPullContainer,
			TakingItems,
			OpeningPutEnderChest,
			PuttingInEnderChest,
			BreakingEmptyPullContainers,
			MessagingForPearl,
			AwaitingTeleport,
			DispensingPearl,
			AwaitingPearl,
			ThrowingPearl,
			DroppingItems,
			OpeningPutContainer,
			PuttingItems,
			OpeningPullEnderChest,
			PullingFromEnderChest,
			Killing,
			AwaitingDeath,
			Respawning,
			AwaitingRespawn
		}
	}

	private class PearlBot : Task<Unit>() {
		override val name
			get() = "Pearling $moverBotName for stash moving, current state: $pearlState"
		var pearlState = PearlState.Waiting

		init {
			listen<TickEvent.Pre> {
				when (pearlState) {
					PearlState.Pressing -> {
						val buttonPos = pearlBotButton ?: run { failWithLog("No pearl bot button set!"); return@listen }
						getButtonPressTask(buttonPos)
							?.finally {
								pearlState = PearlState.Waiting
							}
							?.execute(this@PearlBot)
					}
					else -> {}
				}
			}

			listen<ChatEvent.Receive> { event ->
				if (!event.message.string.startsWith("$moverBotName whispers")) return@listen
				pearlState = PearlState.Pressing
			}
		}

		private enum class PearlState {
			Waiting,
			Pressing
		}
	}

	context(safeContext: SafeContext)
	private fun Task<*>.getButtonPressTask(pos: BlockPos): Task<*>? =
		with (safeContext) {
			val buttonState = blockState(pos)
			if (buttonState.block !is ButtonBlock) {
				failure("Pearl button position does not contain a button!")
			}
			return if (!buttonState.get(Properties.POWERED)) {
				StashMover.runSafeAutomated {
					mapOf(pos to TargetState.State(buttonState.with(Properties.POWERED, true)))
						.build()
				}
			} else null
		}

	private fun Task<*>.failWithLog(message: String) {
		failure(message)
		StashMover.logError(message)
	}
}