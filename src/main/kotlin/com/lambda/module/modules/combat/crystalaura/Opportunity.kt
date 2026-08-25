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

package com.lambda.module.modules.combat.crystalaura

import com.lambda.context.Automated
import com.lambda.interaction.handlers.ContainerHandler.transfer
import com.lambda.interaction.managers.hotbar.HotbarRequest
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.containers.HotbarContainer
import com.lambda.interaction.material.container.containers.OffHandContainer
import com.lambda.module.modules.combat.crystalaura.CrystalAura.ActionType
import com.lambda.module.modules.combat.crystalaura.CrystalAura.PredictionMode
import com.lambda.module.modules.combat.crystalaura.CrystalAura.crystalPosition
import com.lambda.module.modules.combat.crystalaura.CrystalAura.explodeDelay
import com.lambda.module.modules.combat.crystalaura.CrystalAuraExploding.explodeInternal
import com.lambda.module.modules.combat.crystalaura.CrystalAura.explodeTimer
import com.lambda.module.modules.combat.crystalaura.CrystalAura.lastEntityId
import com.lambda.module.modules.combat.crystalaura.CrystalAura.packetLifetime
import com.lambda.module.modules.combat.crystalaura.CrystalAura.placeDelay
import com.lambda.module.modules.combat.crystalaura.CrystalAura.placePostPause
import com.lambda.module.modules.combat.crystalaura.CrystalAura.placePredictions
import com.lambda.module.modules.combat.crystalaura.CrystalAura.placeTimer
import com.lambda.module.modules.combat.crystalaura.CrystalAura.postPacketPlace
import com.lambda.module.modules.combat.crystalaura.CrystalAura.prediction
import com.lambda.module.modules.combat.crystalaura.CrystalAura.predictionTimer
import com.lambda.module.modules.combat.crystalaura.CrystalAura.priorityMode
import com.lambda.module.modules.combat.crystalaura.CrystalAura.rotate
import com.lambda.module.modules.combat.crystalaura.CrystalAura.safeToPlaceInstantly
import com.lambda.module.modules.combat.crystalaura.CrystalAura.swap
import com.lambda.module.modules.combat.crystalaura.CrystalAura.swapHand
import com.lambda.module.modules.combat.crystalaura.CrystalAura.waitingForCrystal
import com.lambda.module.modules.combat.crystalaura.CrystalAuraPlacing.placeInternal
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeAutomated
import com.lambda.util.math.distSq
import com.lambda.util.math.getHitVec
import com.lambda.util.math.plus
import com.lambda.util.player.RotationUtils.getVisibleSurfaces
import com.lambda.util.player.SlotUtils.hotbarStacks
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.time.Duration.Companion.milliseconds


/**
 * Represents the damage information resulting from placing an end crystal on a given [blockPos]
 * and causing an explosion that targets current target entity.
 *
 * @property blockPos The position of the base block where the crystal is placed.
 * @property target The amount of damage inflicted on the target.
 * @property self The amount of damage inflicted on the player.
 * @property blocked Whether the placement on [blockPos] is blocked by other crystals.
 * @property crystal A crystal that is placed on [blockPos].
 */
class Opportunity(
	private val automated: Automated,
	val blockPos: BlockPos,
	val target: Double,
	val self: Double,
	var blocked: Boolean,
	var crystal: EndCrystalEntity? // ToDo: packet-based update wisely
) {
	var actionType = ActionType.Normal
	val priority = priorityMode.factor(target, self)
	val hasCrystal get() = crystal != null

	val crystalPosition by lazy {
		blockPos.crystalPosition
	}

	val side by lazy {
		runSafe {
			val visibleSides = Box(blockPos).getVisibleSurfaces(player.eyePos)
			if (visibleSides.contains(Direction.UP)) Direction.UP else visibleSides.minByOrNull {
				blockPos.getHitVec(it) distSq player.eyePos
			}
		} ?: Direction.UP
	}

	val placeRotation by lazy {
		runSafe {
			var vec = blockPos.getHitVec(side)

			// look at the top part of the side
			if (side.axis != Direction.Axis.Y) vec + Vec3d(0.0, 0.45, 0.0)

			player.eyePos.rotationTo(vec)
		} ?: RotationManager.activeRotation
	}

	/**
	 * Places the crystal on [blockPos]
	 */
	fun place() = runSafe {
		if (rotate && !automated.rotationRequest { rotation(placeRotation) }.submit().done)
			return@runSafe
		var crystalHand: Hand? = null
		val selection = StackSelection.selectStack { isItem(Items.END_CRYSTAL) }
		if ((swapHand == Hand.MAIN_HAND && player.mainHandStack.item != Items.END_CRYSTAL) ||
			(swapHand == Hand.OFF_HAND && player.offHandStack.item != Items.END_CRYSTAL)
		) automated.runSafeAutomated {
			if (!swap) return@runSafe
			val itemToUse: ItemStack? = if (player.mainHandStack.item == Items.END_CRYSTAL) {
				crystalHand = Hand.MAIN_HAND
				player.mainHandStack
			} else if (player.offHandStack.item == Items.END_CRYSTAL) {
				crystalHand = Hand.OFF_HAND
				player.offHandStack
			} else null

			val swapTo = when (swapHand) {
				Hand.MAIN_HAND -> HotbarContainer
				Hand.OFF_HAND -> OffHandContainer
			}

			if (itemToUse == null || swapHand == Hand.MAIN_HAND) {
				if (swapHand == Hand.MAIN_HAND) {
					val itemStack = selection.bestItemMatch(player.hotbarStacks)
					if (itemStack == null) { // retrieve to hotbar
						if (!selection.transfer(swapTo)) return@runSafe
					}
					val s = player.hotbarStacks.indexOf(itemStack)
					if ((!HotbarRequest(s, automated, nowOrNothing = false).submit().done) && itemToUse == null) return@runSafe
				} else { // retrieve to offhand
					if (!selection.transfer(swapTo)) return@runSafe
				}
			}
		}

		if (placeTimer.timePassed(placeDelay.milliseconds) || (postPacketPlace && safeToPlaceInstantly && !placePostPause)) {
			CrystalAura.placeInternal(this@Opportunity, crystalHand ?: swapHand) // we should not be here without a crystal in hand but ig better to check than not to
			safeToPlaceInstantly = false
			if (prediction.onPlace)
				predictionTimer.runIfNotPassed(packetLifetime.milliseconds, false) {
					val last = lastEntityId

					repeat(placePredictions) {
						CrystalAura.explodeInternal(++lastEntityId)
					}

					lastEntityId = last + 1
					crystal = null
				}
			placeTimer.reset()
		}
	}

	/**
	 * Explodes a crystal that is on [blockPos]
	 * @return Whether the delay passed, null if the interaction failed or no crystal found
	 */
	fun explode() {
		if (rotate && !automated.rotationRequest { rotation(placeRotation) }.submit().done) return

		if (waitingForCrystal && crystal == null && prediction == PredictionMode.Tick) {
			runSafe {
				CrystalAura.explodeInternal(++lastEntityId)
				waitingForCrystal = false
			}
		}

		explodeTimer.runSafeIfPassed(explodeDelay.milliseconds) {
			crystal?.let { crystal ->
				CrystalAura.explodeInternal(crystal.id)
				explodeTimer.reset()
			}
		}
	}
}