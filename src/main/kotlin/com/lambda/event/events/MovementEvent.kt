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

package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import net.minecraft.client.input.Input
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.MovementType
import net.minecraft.util.math.Vec3d

sealed class MovementEvent {
    /**
     * Represents player movement update events.
     * This event will even be triggered if the player is not moving.
     */
    sealed class Player {
        abstract val movementType: MovementType
        abstract val movement: Vec3d

        /**
         * Event triggered before player movement.
         *
         * @property movementType The type of movement.
         * @property movement The movement vector.
         */
        data class Pre(
            override val movementType: MovementType,
            override val movement: Vec3d,
        ) : Player(), ICancellable by Cancellable()

        /**
         * Event triggered after player movement.
         *
         * @property movementType The type of movement.
         * @property movement The movement vector.
         */
        data class Post(
            override val movementType: MovementType,
            override val movement: Vec3d,
        ) : Player(), Event
    }

    /**
     * Represents entity movement update events.
     * This event will even be triggered if the entity is not moving.
     */
    sealed class Entity {
        abstract val entity: LivingEntity
        abstract val movementInput: Vec3d

        /**
         * Event triggered before entity movement.
         *
         * @property entity The entity involved in the movement event.
         * @property movementInput The movement input vector for the entity.
         */
        data class Pre(
            override val entity: LivingEntity,
            override val movementInput: Vec3d,
        ) : Entity(), ICancellable by Cancellable()

        /**
         * Event triggered after entity movement.
         *
         * @property entity The entity involved in the movement event.
         * @property movementInput The movement input vector for the entity.
         */
        data class Post(
            override val entity: LivingEntity,
            override val movementInput: Vec3d,
        ) : Entity(), Event
    }

    /**
     * Event triggered when the user's input is updated.
     *
     * @property input The input state of the player.
     */
    data class InputUpdate(val input: Input) : Event

    /**
     * Event triggered when the player starts or stops sprinting.
     *
     * @property sprint Indicates if the player is sprinting. Can be modified!
     */
    data class Sprint(var sprint: Boolean) : Event

    /**
     * Event triggered when the player starts or stops sneaking.
     *
     * @property sneak Indicates if the player is sneaking. Can be modified!
     */
    data class Sneak(var sneak: Boolean) : Event

    /**
     * Event triggered when the player reaches a ledge and may clip.
     *
     * @property clip Indicates if the player should clip at the ledge. Can be modified!
     */
    data class ClipAtLedge(
        var clip: Boolean,
    ) : Event

    /**
     * Event triggered when the player jumps.
     *
     * @property height The height of the jump. Can be modified!
     */
    data class Jump(var height: Double) : ICancellable by Cancellable()
}
