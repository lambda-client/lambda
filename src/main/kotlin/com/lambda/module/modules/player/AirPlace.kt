/*
 * Copyright 2025 Lambda
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

package com.lambda.module.modules.player

import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.config.settings.complex.Bind
import com.lambda.context.SafeContext
import com.lambda.event.events.ButtonEvent
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.onStaticRender
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.simulation.context.BuildContext
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.managers.interacting.InteractConfig
import com.lambda.interaction.managers.interacting.InteractRequest.Companion.interactRequest
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotation
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafeAutomated
import com.lambda.util.InputUtils.isSatisfied
import com.lambda.util.KeyCode
import com.lambda.util.NamedEnum
import net.minecraft.block.BlockState
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.RaycastContext
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.util.concurrent.ConcurrentLinkedQueue

object AirPlace : Module(
	name = "AirPlace",
	description = "Allows placing blocks in air",
	tag = ModuleTag.PLAYER
) {
	enum class Group(override val displayName: String) : NamedEnum {
		General("General"),
		Render("Render")
	}

	private var distance by setting("Distance", 4.0, 1.0..7.0, 1.0).group(Group.General)
	private val scrollBind by setting("Scroll Bind", Bind(KeyCode.Unbound.code, GLFW.GLFW_MOD_CONTROL), "Allows you to hold the ctrl key and scroll to adjust distance").group(Group.General)

	private val outlineColor by setting("Outline Color", Color.WHITE).group(Group.Render)
	private val outlineWidth by setting("Outline Width", 1.5f, 0.5f..10f, 0.1f)

	private var placementPos: BlockPos? = null
	private var placementState: BlockState? = null
	private val pendingInteractions = ConcurrentLinkedQueue<BuildContext>()

	init {
		setDefaultAutomationConfig {
			applyEdits {
				interactConfig.apply {
					::airPlace.edit { defaultValue(InteractConfig.AirPlaceMode.Grim) }
				}
				hideAllGroupsExcept(interactConfig)
			}
		}

		listen<TickEvent.Pre> {
			val selectedStack = player.inventory.selectedStack
			val blockItem = selectedStack.item as? BlockItem ?: run {
				placementPos = null
				placementState = null
				return@listen
			}
			val raycastEnd = player.rotation.vector.multiply(distance).add(player.eyePos)
			val raycastContext = RaycastContext(
				player.eyePos,
				raycastEnd,
				RaycastContext.ShapeType.OUTLINE,
				RaycastContext.FluidHandling.NONE,
				player
			)
			val placementContext = ItemPlacementContext(
				world,
				player, Hand.MAIN_HAND,
				selectedStack,
				world.raycast(raycastContext),
			)
			placementPos = placementContext.blockPos
			placementState = blockItem.getPlacementState(placementContext)
		}

		listen<PlayerEvent.Interact.Block> { if (airPlace()) it.cancel() }
		listen<PlayerEvent.Interact.Item> { if (airPlace()) it.cancel() }

		onStaticRender { esp ->
			placementPos?.let { pos ->
				val boxes = placementState?.getOutlineShape(world, pos)?.boundingBoxes
					?: listOf(Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))
				esp.shapes {
					boxes.forEach { box ->
						box(box, outlineWidth) {
							hideFill()
						}
					}
				}
			}
		}

		listen<ButtonEvent.Mouse.Scroll> { event ->
			if (!scrollBind.isSatisfied()) return@listen
			event.cancel()
			distance += event.delta.y
		}
	}

	private fun SafeContext.airPlace(): Boolean {
		if (player.inventory.selectedStack.item !is BlockItem) return false
		placementPos?.let { pos ->
			placementState?.let { state ->
				runSafeAutomated {
					mapOf(pos to TargetState.State(state))
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