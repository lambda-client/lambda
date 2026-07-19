
package com.minato.module.modules.world

import com.minato.Minato.mc
import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.Group
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.settings.complex.Bind
import com.minato.config.withEdits
import com.minato.context.SafeContext
import com.minato.event.events.ButtonEvent
import com.minato.event.events.PlayerEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.graphics.mc.renderer.TickedRenderer.Companion.tickedRenderer
import com.minato.interaction.construction.simulation.BuildSimulator.simulate
import com.minato.interaction.construction.simulation.context.BuildContext
import com.minato.interaction.construction.verify.TargetState
import com.minato.interaction.managers.interacting.InteractRequest
import com.minato.interaction.managers.interacting.InteractRequest.Companion.interactRequest
import com.minato.interaction.managers.rotating.Rotation.Companion.rotation
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafeAutomated
import com.minato.util.InputUtils.isSatisfied
import com.minato.util.KeyCode
import com.minato.util.math.setAlpha
import com.minato.util.math.vec3d
import net.minecraft.block.BlockState
import net.minecraft.item.BlockItem
import net.minecraft.item.DebugStickItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.state.property.Properties
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.random.Random
import net.minecraft.world.RaycastContext
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.util.concurrent.ConcurrentLinkedQueue

@Suppress("unused")
object AirPlace : Module(
	name = "AirPlace",
	description = "Allows placing blocks in air",
	tag = ModuleTag.WORLD
) {
	private const val RENDER_GROUP = "Renders"

	private var distance by setting("Distance", 4.0, 1.0..7.0, 0.01)
	private val distanceScrollBind by setting("Distance Scroll Bind", Bind(KeyCode.Unbound.code, GLFW.GLFW_MOD_CONTROL), "Allows you to hold the given key and scroll to adjust distance")
	// Credit to THCFree for the rotation scroll idea
	private val rotationScrollBind by setting("Rotation Scroll Bind", Bind(KeyCode.Unbound.code, GLFW.GLFW_MOD_ALT), "Allows you to hold the given key and scroll to adjust the rotation of the block you're placing")

	@Group(RENDER_GROUP) private val renderState by setting("Render State", true)
	@Group(RENDER_GROUP) private val lineColor by setting("Line Color", Color.WHITE)
	@Group(RENDER_GROUP) private val stateAlpha by setting("State Alpha", 0.5, 0.01..1.0, 0.01)

	private var placementPos: BlockPos? = null
	private var backingState: BlockState? = null
	private var placementState: BlockState? = null
	private val pendingInteractions = ConcurrentLinkedQueue<BuildContext>()

	private var request: InteractRequest? = null
	private var extraPlaceTicks = 0

	private val rotatingProperties = arrayOf(
		Properties.ROTATION,
		Properties.FACING,
		Properties.HORIZONTAL_FACING,
		Properties.HORIZONTAL_AXIS,
		Properties.AXIS,
		Properties.HOPPER_FACING
	)

	init {
		setDefaultAutomationConfig()
			.withEdits {
				hideAllExcept(::interactConfig)
			}

		listen<TickEvent.Pre> {
			if (request?.done == false) {
				airPlace()
				extraPlaceTicks++
				if (extraPlaceTicks > 40) request = null
				return@listen
			} else {
				extraPlaceTicks = 0
				request = null
			}

			val selectedStack = player.inventory.selectedStack
			val blockItem = selectedStack.item as? BlockItem
			if (blockItem != null) {
				val placementContext = ItemPlacementContext(
					world,
					player, Hand.MAIN_HAND,
					selectedStack,
					getHitResult()
				)
				setPosAndState(placementContext.blockPos, blockItem.getPlacementState(placementContext))
				return@listen
			}
			setPosAndState()
			return@listen
		}

		listen<PlayerEvent.Interact.Block> { if (airPlace()) it.cancel() }
		listen<PlayerEvent.Interact.Item> { if (airPlace()) it.cancel() }

		tickedRenderer("Air Place Ticked Renderer") {
			val world = mc.world ?: return@tickedRenderer
			placementPos?.let { pos ->
				placementState?.let { state ->
					val boxes = state.getOutlineShape(world, pos).boundingBoxes.map { it.offset(pos) }
					boxes.forEach { box ->
						box(box) {
							hideFill()
							outlineColor(lineColor)
						}
					}
					if (renderState) {
						val model = mc.bakedModelManager.blockModels.getModel(state)
						val parts = model.getParts(Random.create())
						parts.forEach { part ->
							model(
								part,
								pos = pos.vec3d,
								scale = Vec3d(1.0, 1.0, 1.0),
								pixelPerfect = true,
								color = Color.WHITE.setAlpha(stateAlpha)
							)
						}
					}
				}
			}
		}

		listen<ButtonEvent.Mouse.Scroll> { event ->
			if (distanceScrollBind.isSatisfied()) {
				event.cancel()
				distance += event.delta.y
				return@listen
			} else if (rotationScrollBind.isSatisfied()) {
				event.cancel()
				placementState = placementState?.withSteppedRotation(event.delta.y < 0)
			}
		}
	}

	private fun setPosAndState(pos: BlockPos? = null, state: BlockState? = null) {
		if (state === backingState) {
			placementPos = pos
			return
		}
		backingState = state
		placementState = state
	}

	private fun BlockState.withSteppedRotation(reverse: Boolean): BlockState {
		var rotatedState = this
		rotatingProperties.forEach { property ->
			if (property in rotatedState) {
				rotatedState = DebugStickItem.cycle(rotatedState, property, reverse)
			}
		}
		return rotatedState
	}

	private fun SafeContext.getHitResult(): BlockHitResult {
		val raycastEnd = player.rotation.vector.multiply(distance).add(player.eyePos)
		val raycastContext = RaycastContext(
			player.eyePos,
			raycastEnd,
			RaycastContext.ShapeType.OUTLINE,
			RaycastContext.FluidHandling.NONE,
			player
		)
		return world.raycast(raycastContext)
	}

	private fun SafeContext.airPlace(): Boolean {
		if (player.inventory.selectedStack.item !is BlockItem) return false
		placementPos?.let { pos ->
			placementState?.let { state ->
				runSafeAutomated {
					request = mapOf(pos to TargetState.State(state))
						.simulate()
						.interactRequest(pendingInteractions)
						?.submit()
				}
				return true
			}
		}
		return false
	}
}