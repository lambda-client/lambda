
package com.minato.event.events

import com.minato.event.Event
import com.minato.event.callback.Cancellable
import com.minato.event.callback.ICancellable
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
    data class Spawn(
        val entity: Entity,
    ) : ICancellable by Cancellable()

    /**
     * Represents an event triggered when an entity is removed from the game world.
     *
     * @property entity The entity being removed from the world.
     * @property removalReason The reason for the removal of the entity.
     */
    data class Removal(
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
    data class Update(
        val entity: Entity,
        val data: TrackedData<*>,
    ) : Event
}
