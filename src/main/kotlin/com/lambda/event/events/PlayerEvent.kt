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

package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

/**
 * Represents various events that can be triggered by the player during gameplay.
 *
 * Each event belongs to a specific category, such as movement, interaction, attacking, or other actions.
 * Many of the events in this sealed class are cancellable, allowing listeners to intercept and prevent the event
 * from proceeding.
 */
sealed class PlayerEvent {
	/**
	 * Represents the player moving the cursor around
	 */
	data class ChangeLookDirection(
		val deltaYaw: Double,
		val deltaPitch: Double,
	) : ICancellable by Cancellable()

	/**
	 * Represents the player swinging its hand
	 */
	data class SwingHand(
		val hand: Hand,
	) : ICancellable by Cancellable()

	/**
	 * Represents a health update for the player.
	 *
	 * @property amount The new player health.
	 */
	data class Health(val amount: Float) : Event

	sealed class Interact {
		/**
		 * Represents the player interacting (right-click) with blocks
		 *
		 * @param hand The hand used to interact with the block
		 * @param blockHitResult Details about the block being interacted with
		 */
		data class Block(
			val hand: Hand,
			val blockHitResult: BlockHitResult,
		) : ICancellable by Cancellable()

		/**
		 * Represents the player interacting (right-click) with entities
		 *
		 * @param hand The hand used to interact with the entity
		 * @param entity The entity being interacted with
		 * @param entityHitResult Details about the entity being interacted with
		 */
		data class Entity(
			val hand: Hand,
			val entity: net.minecraft.entity.Entity,
			val entityHitResult: EntityHitResult,
		) : ICancellable by Cancellable()

		/**
		 * Represents the player interacting (right-click) with an item in the inventory
		 *
		 * @param hand The hand used to interact with the item
		 */
		data class Item(
			val hand: Hand,
		) : ICancellable by Cancellable()
	}

	sealed class Attack {
		/**
		 * Represents the player attacking a block
		 */
		data class Block(
			val pos: BlockPos,
			val side: Direction,
		) : ICancellable by Cancellable()

		/**
		 * Represents the player attacking an entity
		 */
		data class Entity(
			val entity: net.minecraft.entity.Entity,
		) : ICancellable by Cancellable()
	}

	sealed class Breaking {
		/**
		 * Represents events triggered during a block breaking action
		 */
		data class Update(
			val pos: BlockPos,
			val side: Direction,
			var progress: Float,
		) : ICancellable by Cancellable()

		/**
		 * Represents the player canceling a block breaking action
		 */
		data class Cancel(
			var progress: Float,
		) : ICancellable by Cancellable()
	}

	/**
	 * Represents the player clicking on a slot in a screen
	 */
	data class SlotClick(
		val syncId: Int,
		val slot: Int,
		val button: Int,
		val action: SlotActionType,
		val screenHandler: ScreenHandler,
	) : ICancellable by Cancellable()
}
