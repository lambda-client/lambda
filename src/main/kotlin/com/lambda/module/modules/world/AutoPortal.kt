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
import com.lambda.config.Group
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.blocks.WorldLineSettings
import com.lambda.config.editSetting
import com.lambda.config.forEachSetting
import com.lambda.config.hide
import com.lambda.config.hideBlock
import com.lambda.config.settings.complex.Bind
import com.lambda.config.settings.complex.KeybindSetting.Companion.onPress
import com.lambda.config.settings.complex.KeybindSetting.Companion.onRelease
import com.lambda.config.withEdits
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.graphics.util.DirectionMask
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.handler.handlers.BaritoneHandler
import com.lambda.interaction.inventory.StackSelectionBuilder.Companion.selectStack
import com.lambda.interaction.inventory.container.containers.HotbarAndInventoryContainer
import com.lambda.interaction.inventory.container.containers.HotbarContainer
import com.lambda.interaction.manager.managers.hotbar.HotbarRequestBuilder.Companion.hotbarRequest
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder.Companion.inventoryRequest
import com.lambda.module.Module
import com.lambda.module.modules.world.AutoPortal.PosHandler.currAnchorPos
import com.lambda.module.modules.world.AutoPortal.PosHandler.obiPositions
import com.lambda.module.modules.world.AutoPortal.PosHandler.portalPositions
import com.lambda.module.modules.world.AutoPortal.PosHandler.prevAnchorPos
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.build
import com.lambda.task.tasks.wrappers.thenOrNull
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.isEmpty
import com.lambda.util.BlockUtils.isNotEmpty
import com.lambda.util.PacketUtils.sendPacket
import com.lambda.util.extension.blockColor
import com.lambda.util.extension.tickDelta
import com.lambda.util.math.lerp
import com.lambda.util.math.setAlpha
import com.lambda.util.math.vec3d
import net.minecraft.block.Blocks
import net.minecraft.item.FlintAndSteelItem
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

@Suppress("unused")
object AutoPortal : Module(
	name = "AutoPortal",
	description = "Automatically places and lights a nether portal",
	tag = ModuleTag.WORLD
) {
	private const val RENDER_GROUP = "Renders"
	private const val FILL_GROUP = "Fill"
	private const val OUTLINE_GROUP = "Outline"

	private val previewPlace by setting("Preview Place", Bind.EMPTY, "The keybind to preview the portal placement and subsequentially place the portal")
		.onPress { preview = true }
		.onRelease {
			preview = false
			buildTask?.cancel()
			val posStateMap =
				obiPositions.associateWith {
					TargetState.Block(Blocks.OBSIDIAN)
				} + portalPositions.associateWith {
					TargetState.Air
				}
			//ToDo: implement non placement interactions like flint and steel in the build sim, in turn, simulating portal lighting too.
//				+ portalPositions.associateWith {
//					TargetState.Block(Blocks.NETHER_PORTAL)
//				}
			buildTask = posStateMap
				.build()
				.thenOrNull {
					if (light) LightTask(currAnchorPos.up(), walkIn)
					else null
				}.onCompletion {
					buildTask = null
				}.run()
		}
	private val corners by setting("Corners", false)
	private val light by setting("Light", true, "Attempts to automatically light the portal after building")
	private val walkIn by setting("Walk In", true, "Automatically paths into the portal with baritone") { light }
	private val inventory by setting("Inventory", true, "Allows access to the players inventory when retrieving a flint and steel for lighting the portal")
	private val forwardOffset by setting("Forward Offset", 3, 0..10)
	private val sidewaysOffset by setting("Sideways Offset", 0, -5..5)
	private val yOffset by setting("Y Offset", 0, -5..5)
	private val lockToGround by setting("Lock To Ground", true)
	private val allowUpwardShift by setting("Allow Upward Shift", true, "Allows shifting the portal up to find ground when it would be placed inside blocks") { lockToGround }

	@Group(RENDER_GROUP) private val renders by setting("Renders", true)
	@Group(RENDER_GROUP) private val interpolate by setting("Interpolate", true, "Interpolates the portal renders from position to position") { renders }
	@Group(RENDER_GROUP) private val depthTest by setting("Depth Test", false) { renders }
	@Group(RENDER_GROUP, FILL_GROUP) private val fillAlpha by setting("Fill Alpha", 0.3, 0.0..1.0, 0.01) { renders }
	@Group(RENDER_GROUP, OUTLINE_GROUP) private val outlineConfig by configBlock(WorldLineSettings(this))
		.withEdits {
			hide(::startColor, ::endColor)
			forEachSetting {
				visibility { old -> { old() && renders } }
			}
		}

	private var preview = false
	private var buildTask: Task<*>? = null

	init {
		setDefaultAutomationConfig()
			.withEdits {
				hideBlock(::eatConfig)
				hotbarConfig::tickStageMask.editSetting {
					defaultValue(mutableSetOf(TickEvent.Pre, TickEvent.Input.Post))
				}
			}

		listen<TickEvent.Pre> {
			PosHandler.tick()
		}

		onDisable {
			buildTask?.cancel()
			buildTask = null
		}

		immediateRenderer("AutoPortal Immediate Renderer", { depthTest }) {
			if (!renders || !preview) return@immediateRenderer
			runSafe {
				val obiColor = blockColor(Blocks.OBSIDIAN.defaultState, BlockPos.ORIGIN)
				obiPositions
					.map {
						val box = Box(it).let { box ->
							if (interpolate) {
								val offset = lerp(
									1.0 - mc.tickDelta,
									Vec3d.ZERO,
									prevAnchorPos.subtract(currAnchorPos).vec3d
								)
								box.offset(offset)
							} else box
						}
						Pair(it, box)
					}
					.forEach { posAndBox ->
						box(posAndBox.second, outlineConfig) {
							colors(obiColor.setAlpha(fillAlpha), obiColor)
							hideSides(DirectionMask.buildSideMesh(posAndBox.first) { it in obiPositions }.inv())
						}
					}
			}
		}
	}

	private object PosHandler {
		var currAnchorPos: BlockPos = BlockPos.ORIGIN
			private set
		var prevAnchorPos = currAnchorPos
			private set

		var obiPositions = emptyList<BlockPos>()
			private set
		var portalPositions = emptyList<BlockPos>()
			private set

		private val originObiPositions = getOriginObiPositions()
		private val originObiPositionsWithCorners = getOriginObiPositions(true)
		private val originPortalPositions = getOriginPortalPositions()

		context(safeContext: SafeContext)
		fun tick() =
			with(safeContext) {
				if (!preview) return@with
				val offsetDir = player.horizontalFacing

				val baseAnchorPos = player.blockPos
					.offset(offsetDir, forwardOffset)
					.offset(offsetDir.rotateYClockwise(), sidewaysOffset)

				val lockedAnchorPos =
					if (lockToGround) lockToGround(baseAnchorPos)
					else baseAnchorPos

				val yOffsetAnchorPos = lockedAnchorPos?.offset(Direction.UP, yOffset)

				if (yOffsetAnchorPos == currAnchorPos || yOffsetAnchorPos == null) {
					prevAnchorPos = currAnchorPos
					return@with
				}

				prevAnchorPos = currAnchorPos
				currAnchorPos = yOffsetAnchorPos
				val originObi =
					if (corners) originObiPositionsWithCorners
					else originObiPositions
				obiPositions = originObi
					.rotatedTo(offsetDir)
					.map { it.add(yOffsetAnchorPos) }
				portalPositions = originPortalPositions
					.rotatedTo(offsetDir)
					.map { it.add(yOffsetAnchorPos) }
			}

		private fun SafeContext.lockToGround(pos: BlockPos): BlockPos? {
			var scanPos = pos
			val upShifting = blockState(scanPos).isNotEmpty && allowUpwardShift
			if (upShifting) {
				while (blockState(scanPos).isNotEmpty && scanPos.y < 320) {
					scanPos = scanPos.up()
				}
			}
			if (!upShifting || scanPos.y >= 320) {
				scanPos = pos
				while (blockState(scanPos.down()).isEmpty && scanPos.y > -64) {
					scanPos = scanPos.down()
				}
				if (scanPos.y <= -64) return null
			}
			return scanPos
		}

		private fun List<BlockPos>.rotatedTo(direction: Direction): List<BlockPos> =
			map { pos ->
				when (direction) {
					Direction.EAST -> pos
					Direction.SOUTH -> BlockPos(pos.z - 1, pos.y, -pos.x)
					Direction.WEST -> BlockPos(-pos.x, pos.y, -pos.z)
					else -> BlockPos(-pos.z + 1, pos.y, pos.x)
				}
			}

		private fun getOriginObiPositions(corners: Boolean = false) =
			buildList {
				(-1..2).forEach { x ->
					(0..4).forEach { y ->
						if (x > -1 && x < 2 && y > 0 && y < 4) return@forEach
						if (!corners && (x == -1 || x == 2) && (y == 0 || y == 4)) return@forEach
						add(BlockPos(0, y, x))
					}
				}
			}

		private fun getOriginPortalPositions() =
			buildList {
				(0..1).forEach { x ->
					(1..3).forEach { y ->
						add(BlockPos(0, y, x))
					}
				}
			}
	}

	private class LightTask(
		private val pos: BlockPos,
		private val walkIn: Boolean
	) : Task<Unit>() {
		override val name = "Lighting portal at $pos"

		init {
			listen<TickEvent.Pre> {
				withFlintAndSteel {
					swapPacket()
					interaction.sendSequencedPacket(world) { sequence ->
						PlayerInteractBlockC2SPacket(
							Hand.OFF_HAND,
							BlockHitResult(
								pos.down().toCenterPos(),
								Direction.UP,
								pos,
								false,
								false
							),
							sequence
						)
					}
					swapPacket()
					if (walkIn) {
						BaritoneHandler.setGoalAndPath(GoalBlock(currAnchorPos.up()))
					}
					success()
				}
			}
		}

		private fun SafeContext.swapPacket() =
			connection.sendPacket {
				PlayerActionC2SPacket(
					PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
					BlockPos.ORIGIN,
					Direction.DOWN
				)
			}

		private fun SafeContext.withFlintAndSteel(block: SafeContext.() -> Unit) {
			if (player.mainHandStack.item == Items.FLINT_AND_STEEL) {
				block()
				return
			}

			val sel = selectStack(1) { isItem<FlintAndSteelItem>() }

			val hotbarStack = sel.filter(HotbarContainer.slots).firstOrNull()
			if (hotbarStack != null) {
				val request =
					hotbarRequest(hotbarStack.index) {
						keepTicks(0)
					}.submit(false)
				if (request.done) block()
				return
			}

			val invSlot =
				if (inventory) sel.filter(HotbarAndInventoryContainer.slots).firstOrNull()
				else null
			if (invSlot == null) {
				failure("No Flint and Steel!")
				return
			}
			val hotbarSlotToSwapWith =
				HotbarContainer.slots.find { slot ->
					slot.stack.isEmpty
				}?.index ?: 8

			inventoryRequest {
				swapWithHotbar(invSlot.id, hotbarSlotToSwapWith)
				action {
					val request =
						hotbarRequest(hotbarSlotToSwapWith) {
							keepTicks(0)
						}.submit(false)
					if (request.done) block()
				}
				swapWithHotbar(invSlot.id, hotbarSlotToSwapWith)
			}.submit()
		}
	}
}