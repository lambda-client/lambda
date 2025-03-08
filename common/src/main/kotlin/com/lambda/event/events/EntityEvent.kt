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
import net.minecraft.entity.Entity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.data.TrackedData

/**
 * A sealed class representing different types of events involving entities.
 */
sealed class EntityEvent {
    /**
     * Represents an event where damage is inflicted on an entity.
     *
     * This event is cancellable, allowing handlers to prevent the damage from being applied to the entity.
     *
     * @property entity The entity receiving the damage.
     * @property source The source of the damage (e.g., weapon, environment, or magic).
     * @property amount The amount of damage being inflicted.
     */
    data class Damage(
        val entity: Entity,
        val source: DamageSource,
        val amount: Float,
    ) : ICancellable by Cancellable()

    /**
     * Represents an event triggered when an entity is spawned in the game world.
     *
     * This event is cancellable, allowing handlers to prevent the entity from spawning.
     *
     * @property entity The entity that is being spawned.
     */
    data class EntitySpawn(
        val entity: Entity,
    ) : ICancellable by Cancellable()

    /**
     * Represents an event triggered when an entity is removed from the game world.
     *
     * @property entity The entity being removed from the world.
     * @property removalReason The reason for the removal of the entity.
     */
    data class EntityRemoval(
        val entity: Entity,
        val removalReason: Entity.RemovalReason,
    ) : Event

    /**
     * Represents an event triggered when an entity's tracked data is updated.
     *
     * This event is cancellable, allowing handlers to prevent the propagation
     * of the tracked data update if deemed necessary.
     *
     * @property entity The entity whose tracked data is being updated.
     * @property data The tracked data being updated.
     */
    data class EntityUpdate(
        val entity: Entity,
        val data: TrackedData<*>,
    ) : Event
}