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
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.interaction.BaritoneManager
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.managers.hotbar.HotbarRequest
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.task.tasks.OpenContainerTask
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils.blockEntity
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Communication.warn
import com.lambda.util.NamedEnum
import com.lambda.util.TickTimer
import com.lambda.util.extension.containerSlots
import com.lambda.util.extension.containerStacks
import com.lambda.util.extension.playerSlots
import com.lambda.util.extension.rotation
import com.lambda.util.math.distSq
import com.lambda.util.math.setAlpha
import com.lambda.util.player.SlotUtils.allSlots
import com.lambda.util.player.SlotUtils.hotbarAndInventoryStacks
import com.lambda.util.player.SlotUtils.hotbarSlots
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.ButtonBlock
import net.minecraft.block.entity.LootableContainerBlockEntity
import net.minecraft.client.gui.screen.DeathScreen
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
import net.minecraft.state.property.Properties
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.to

object StashMover : Module(
	name = "StashMover",
	description = "Moves items from one stash location to another",
	tag = ModuleTag.WORLD
) {
	private enum class Role(val createTask: () -> Task<*>) {
		MoverBot({ MoverBot() }),
		PearlBot({ PearlBot() })
	}

	private enum class Group(override val displayName: String) : NamedEnum {
		General("General"),
		CommandBinds("Command Binds"),
		Render("Render")
	}

	private val role: Role by setting("Role", Role.MoverBot).group(Group.General)
		.onValueChange { _, to ->
			if (to == Role.PearlBot) {
				chestPullSelMode = false
				chestPutSelMode = false
				sel1 = null
				sel2 = null
			}
		}
	private val pearlBotName by setting("PearlBot Name", "Steve") { role == Role.MoverBot }.group(Group.General)
	private val moverBotName by setting("MoverBot Name", "Steve") { role == Role.PearlBot }.group(Group.General)
	private var chestPullSelMode: Boolean by setting("Chest Pull Sel Mode", false, "Enables the mode to select the stash containers you want to move items from") { role == Role.MoverBot }.group(Group.General)
		.onValueChange { _, to -> if (to) chestPutSelMode = false }
	private var chestPutSelMode: Boolean by setting("Chest Put Sel Mode", false, "Enables the mod to select the stash containers you want to move items into") { role == Role.MoverBot }.group(Group.General)
		.onValueChange { _, to -> if (to) chestPullSelMode = false }
	private val pearlMsgTimeout by setting("Pearl Msg Timeout", 200, 0..1500, 1, "Ticks before messaging the pearl bot again", "ticks") { role == Role.MoverBot }.group(Group.General)
	private val pearlButtonTimeout by setting("Pearl Button Timeout", 200, 0..1500, 1, "Ticks before pressing the pearl dispenser button again", "ticks") { role == Role.MoverBot }.group(Group.General)
	private val killRespawnTimeout by setting("Kill/Respawn Timeout", 200, 0..1500, 1, "Ticks before sending the kill command or attempting to respawn again", "ticks") { role == Role.MoverBot }.group(Group.General)
	private val actionDelay by setting("Action Delay", 3, 0..20, 1, "The delay after performing one action, before the next") { role == Role.MoverBot }.group(Group.General)
	private val startStop by setting("Start/Stop", Bind.EMPTY, "Starts and stops the selected role").group(Group.General)
		.onPress { event ->
			event.cancel()
			chestPullSelMode = false
			chestPutSelMode = false
			task?.let { runningTask ->
				runningTask.cancel()
				task = null
				return@onPress
			}
			task = role.createTask().run()
		}

	private val indexSelectedContainers by setting("Index Selected Containers", Bind.EMPTY, "Indexes the selected containers to pull/push items from/to") { role == Role.MoverBot }.group(Group.CommandBinds)
		.onPress { event ->
			event.cancel()
			consumeSelection { pos ->
				if (blockEntity(pos) !is LootableContainerBlockEntity) return@consumeSelection
				if (chestPullSelMode) pullContainers.add(pos)
				else if (chestPutSelMode) putContainers.add(pos)
			}
		}
	private val removeSelectedContainers by setting("Remove Selected Containers", Bind.EMPTY, "Removes the selected containers from being pull/pushed from/to") { role == Role.MoverBot }.group(Group.CommandBinds)
		.onPress { event ->
			event.cancel()
			consumeSelection { pos ->
				if (blockEntity(pos) !is LootableContainerBlockEntity) return@consumeSelection
				if (chestPullSelMode) pullContainers.remove(pos)
				else if (chestPutSelMode) putContainers.remove(pos)
			}
		}
	private val setPearlButtonPos by setting("Set Pearl Button Pos", Bind.EMPTY, "Sets the button used to dispense a pearl for the player") { role == Role.MoverBot }.group(Group.CommandBinds)
		.onPress { event ->
			event.cancel()
			val pos = mc.crosshairTarget?.blockResult?.blockPos ?: return@onPress
			if (blockState(pos).block !is ButtonBlock) {
				warn("Given position does not contain a button!")
				return@onPress
			}
			pearlDispensePos = pos
		}
	private val setPearlThrowPosAndRotation by setting("Set Pearl Throw", Bind.EMPTY, "Sets the pearl throw position and rotation. (This is best if you throw somewhat sideways into a line of bubble columns)") { role == Role.MoverBot }.group(Group.CommandBinds)
		.onPress { event ->
			event.cancel()
			pearlThrowPos = player.blockPos
			pearlRotation = player.rotation
		}
	private val setPearlBotButton by setting("Set PearlBot Button", Bind.EMPTY, "Sets the button position for the pearl bot to press to load the mover bot") { role == Role.PearlBot }.group(Group.CommandBinds)
		.onPress { event ->
			event.cancel()
			val pos = mc.crosshairTarget?.blockResult?.blockPos ?: return@onPress
			if (blockState(pos).block !is ButtonBlock) {
				warn("Given position does not contain a button!")
				return@onPress
			}
			pearlBotButton = pos
		}

	private var sel1: BlockPos? = null
	private var sel2: BlockPos? = null

	private val pullContainers = hashSetOf<BlockPos>()
	private val pulledContainers = hashSetOf<BlockPos>()
	private val putContainers = hashSetOf<BlockPos>()
	private val filledContainers = hashSetOf<BlockPos>()

	private var pearlDispensePos: BlockPos? = null
	private var pearlThrowPos: BlockPos? = null
	private var pearlRotation: Rotation? = null

	private var pearlBotButton: BlockPos? = null

	private var task: Task<*>? = null

	init {
		setModulePriority(100)
		setDefaultAutomationConfig {
			applyEdits {
				hotbarConfig::tickStageMask.edit { defaultValue(mutableSetOf(TickEvent.Pre)) }
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

		onDisable {
			task?.cancel()
			task = null
			sel1 = null
			sel2 = null
			pullContainers.clear()
			pulledContainers.clear()
			putContainers.clear()
			filledContainers.clear()
			pearlDispensePos = null
			pearlThrowPos = null
			pearlRotation = null
			pearlBotButton = null
		}

		immediateRenderer("StashMover Immediate Renderer") {
			if (!chestPullSelMode && !chestPutSelMode) return@immediateRenderer
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

		private var pullContainer: BlockPos? = null
		private var tickTimer = TickTimer()

		private var putContainer: BlockPos? = null

		init {
			listen<TickEvent.Pre> {
				if (delayingNextAction) {
					actionDelayTimer.tick()
					if (!actionDelayTimer.hasSurpassed(actionDelay)) return@listen
					delayingNextAction = false
				}

				when (moverState) {
					MoverState.OpeningPullContainer -> {
						val target = pullContainers.minByOrNull { it distSq player.blockPos }
							?: run {
								success("Pull containers exhausted!")
								return@listen
							}

						OpenContainerTask(
							target,
							StashMover
						).finally {
							pullContainer = target
							moverState = MoverState.TakingItems
						}.execute(this@MoverBot)
					}

					MoverState.TakingItems -> {
						val screenHandler = player.currentScreenHandler
						if (screenHandler === player.playerScreenHandler) {
							moverState = MoverState.OpeningPullContainer
							return@listen
						}
						val pullSlots = screenHandler.containerSlots.filter { !it.stack.isEmpty }
						if (player.hotbarAndInventoryStacks.any { it.isEmpty } && pullSlots.isNotEmpty()) {
							val request = inventoryRequest(settleForLess = true) {
								pullSlots.forEach { slot ->
									quickMove(slot.id)
								}
							}.submit()
							if (!request.done) return@listen
						}
						player.closeScreen()
						pullContainer?.let { container ->
							if (screenHandler.containerStacks.all { it.isEmpty }) {
								pullContainers.remove(container)
								pulledContainers.add(container)
								if (player.hotbarAndInventoryStacks.any { it.isEmpty }) {
									moverState = MoverState.OpeningPullContainer
									return@listen
								}
							}
						}
						moverState = MoverState.MessagingForPearl
					}

					MoverState.MessagingForPearl -> {
						tickTimer.reset()
						connection.sendChatCommand("msg $pearlBotName ${Math.random() * Double.MAX_VALUE}")
						moverState = MoverState.AwaitingTeleport
					}

					MoverState.AwaitingTeleport -> {
						tickTimer.tick()
						if (tickTimer.hasSurpassed(pearlMsgTimeout))
							moverState = MoverState.MessagingForPearl
					}

					MoverState.OpeningPutContainer -> {
						val target = putContainers.minByOrNull { it distSq player.blockPos }
							?: run {
								success("Put containers are full!")
								return@listen
							}

						OpenContainerTask(
							target,
							StashMover
						).finally {
							putContainer = target
							moverState = MoverState.PuttingItems
						}.execute(this@MoverBot)
					}

					MoverState.PuttingItems -> {
						val screenHandler = player.currentScreenHandler
						if (screenHandler === player.playerScreenHandler) {
							moverState = MoverState.OpeningPutContainer
							return@listen
						}
						val putSlots = screenHandler.playerSlots.filter { !it.stack.isEmpty }
						if (screenHandler.containerStacks.any { it.isEmpty } && putSlots.isNotEmpty()) {
							val request = inventoryRequest(settleForLess = true) {
								putSlots.forEach { slot ->
									quickMove(slot.id)
								}
							}.submit()
							if (!request.done) return@listen
						}
						player.closeScreen()
						putContainer?.let { container ->
							if (screenHandler.containerStacks.all { !it.isEmpty }) {
								putContainers.remove(container)
								filledContainers.add(container)
								if (player.hotbarAndInventoryStacks.any { !it.isEmpty }) {
									moverState = MoverState.OpeningPutContainer
									return@listen
								}
							}
						}
						moverState = MoverState.DispensingPearl
					}

					MoverState.DispensingPearl -> {
						val dispensePos = pearlDispensePos ?: run { failure("No pearl button set!"); return@listen }
						if (player.blockPos != dispensePos) {
							BaritoneManager.setGoalAndPath(GoalBlock(dispensePos))
							return@listen
						}
						if (BaritoneManager.isActive) return@listen
						getButtonPressTask(dispensePos)
							?.finally {
								tickTimer.reset()
								moverState = MoverState.AwaitingPearl
							}
							?.execute(this@MoverBot)
					}

					MoverState.AwaitingPearl -> {
						tickTimer.tick()
						if (player.hotbarAndInventoryStacks.any { it.item === Items.ENDER_PEARL }) {
							moverState = MoverState.ThrowingPearl
							return@listen
						}
						if (tickTimer.hasSurpassed(pearlButtonTimeout))
							moverState = MoverState.DispensingPearl
					}

					MoverState.ThrowingPearl -> {
						val throwPos = pearlThrowPos ?: run { failure("No pearl throw pos set!"); return@listen }
						if (player.blockPos != throwPos) {
							BaritoneManager.setGoalAndPath(GoalBlock(throwPos))
							return@listen
						}
						if (BaritoneManager.isActive) return@listen
						val rotation = pearlRotation ?: run { failure("No pearl rotation set!"); return@listen }
						val rotationRequest = rotationRequest {
							rotation(rotation)
						}.submit()
						if (!rotationRequest.done) return@listen
						while (player.mainHandStack.item != Items.ENDER_PEARL) {
							val hotbarSlot = player.hotbarSlots.firstOrNull { it.stack.item === Items.ENDER_PEARL }
							if (hotbarSlot != null) {
								val hotbarRequest = HotbarRequest(hotbarSlot.index, StashMover).submit()
								if (!hotbarRequest.done) return@listen
								break
							} else {
								val inventorySlot = player.allSlots.firstOrNull { it.stack.item === Items.ENDER_PEARL }
								if (inventorySlot == null) {
									failure("No pearl in inventory!")
									return@listen
								}
								val inventoryRequest = inventoryRequest {
									swap(inventorySlot.id, 0)
								}.submit()
								if (!inventoryRequest.done) return@listen
							}
						}
						RotationManager.withoutVanillaOverrides {
							interaction.interactItem(player, Hand.MAIN_HAND)
						}
						moverState = MoverState.Killing
					}

					MoverState.Killing -> {
						tickTimer.reset()
						connection.sendChatCommand("kill")
						moverState = MoverState.AwaitingDeath
					}

					else -> {}
				}
			}

			listenUnsafe<TickEvent.Pre> {
				when (moverState) {
					MoverState.AwaitingDeath -> {
						tickTimer.tick()
						if (tickTimer.hasSurpassed(killRespawnTimeout))
							moverState = MoverState.Killing
					}

					MoverState.Respawning -> {
						tickTimer.reset()
						mc.networkHandler?.sendPacket(ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN))
						moverState = MoverState.AwaitingRespawn
					}

					MoverState.AwaitingRespawn -> {
						tickTimer.tick()
						if (tickTimer.hasSurpassed(killRespawnTimeout))
							moverState = MoverState.Respawning
					}

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
				moverState = MoverState.OpeningPutContainer
			}

			listen<GuiEvent.ScreenOpen> { event ->
				if (moverState != MoverState.AwaitingDeath) return@listen
				if (event.screen !is DeathScreen) return@listen
				moverState = MoverState.Respawning
			}
		}

		private enum class MoverState {
			OpeningPullContainer,
			TakingItems,
			MessagingForPearl,
			AwaitingTeleport,
			OpeningPutContainer,
			PuttingItems,
			DispensingPearl,
			AwaitingPearl,
			ThrowingPearl,
			Killing,
			AwaitingDeath,
			Respawning,
			AwaitingRespawn
		}
	}

	private class PearlBot : Task<String>() {
		override val name
			get() = "Pearling $moverBotName for stash moving, current state: $pearlState"
		var pearlState = PearlState.Waiting

		init {
			listen<TickEvent.Pre> {
				when (pearlState) {
					PearlState.Pressing -> {
						val buttonPos = pearlBotButton ?: run { failure("No pearl bot button set!"); return@listen }
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
	private fun Task<String>.getButtonPressTask(pos: BlockPos): Task<*>? =
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
}