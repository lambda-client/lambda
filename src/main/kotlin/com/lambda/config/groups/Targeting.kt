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

package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.config.SettingGroup
import com.lambda.config.applyEdits
import com.lambda.context.SafeContext
import com.lambda.friend.FriendManager.isFriend
import com.lambda.interaction.managers.rotating.Rotation.Companion.dist
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotation
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.lambda.threading.runSafe
import com.lambda.util.NamedEnum
import com.lambda.util.extension.fullHealth
import com.lambda.util.math.distSq
import com.lambda.util.world.fastEntitySearch
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import java.util.*

/**
 * Abstract class representing a targeting mechanism for entities in the game.
 *
 * This class provides the configuration and validation for targeting different types of entities
 * based on player settings and entity characteristics. It allows for specifying which types of entities
 * are targetable, the range of targeting, and various other conditions for targeting.
 *
 * @param c The [Configurable] instance used to get and set configuration options for targeting.
 * @param vis The predicate used to determine whether the targeting settings are visible and active.
 * @param defaultRange The default range within which entities can be targeted.
 * @param maxRange The maximum range within which entities can be targeted.
 */
abstract class Targeting(
    prefix: String = "",
	c: Configurable,
    vararg baseGroup: NamedEnum,
    defaultRange: Double,
    maxRange: Double,
    visibility: () -> Boolean = { true },
) : SettingGroup(c), TargetingConfig {
	/**
	 * The range within which entities can be targeted. This value is configurable and constrained
	 * between 1.0 and [maxRange].
	 */
	override val targetingRange by c.setting("${prefix}Targeting Range", defaultRange, 1.0..maxRange, 0.05, visibility = visibility).group(*baseGroup).index()
    override val targets = EntitySelectionSettings(c = c, baseGroup = baseGroup).apply {
		c.applyEdits {
			hide(::self, ::blockEntities)
		}
    }

	/**
	 * Validates whether a given entity is targetable by the player based on current settings.
	 *
	 * @param player The [ClientPlayerEntity] performing the targeting.
	 * @param entity The [LivingEntity] being evaluated.
	 * @return `true` if the entity is valid for targeting, `false` otherwise.
	 */
    open fun validate(player: ClientPlayerEntity, entity: Entity) =
		targets.isSelected(entity) && (entity !is OtherClientPlayerEntity || !entity.isFriend)

    /**
     * Subclass for targeting entities specifically for combat purposes.
     *
     * @property fov The field of view limit within which entities are considered for targeting. Configurable.
     * @property priority The priority used to determine which entity is targeted when multiple candidates are available.
     */
    class Combat(
	    prefix: String = "",
        c: Configurable,
        vararg baseGroup: NamedEnum,
        defaultRange: Double = 5.0,
        maxRange: Double = 16.0,
        override val visibility: () -> Boolean = { true },
    ) : Targeting(prefix, c, *baseGroup, defaultRange = defaultRange, maxRange = maxRange, visibility = visibility) {
        /**
         * The field of view limit for targeting entities. Configurable between 5 and 180 degrees.
         */
        val fov by c.setting("${prefix}FOV Limit", 180, 5..180, 1) { visibility() && priority == Priority.Fov }.group(*baseGroup).index()

        /**
         * The priority used to determine which entity is targeted. Configurable with default set to [Priority.Distance].
         */
        val priority by c.setting("${prefix}Priority", Priority.Distance, visibility = visibility).group(*baseGroup).index()

        /**
         * Validates whether a given entity is targetable for combat based on the field of view limit and other settings.
         *
         * @param player The [ClientPlayerEntity] performing the targeting.
         * @param entity The [LivingEntity] being evaluated.
         * @return `true` if the entity is valid for targeting, `false` otherwise.
         */
        override fun validate(player: ClientPlayerEntity, entity: Entity): Boolean {
            if (fov < 180 && player.rotation dist player.eyePos.rotationTo(entity.pos) > fov) return false
            if (entity.uuid in illegalTargets) return false
	        if ((entity as? LivingEntity)?.isDead == true) return false
            return super.validate(player, entity)
        }

        /**
         * Gets the best target for combat based on the current settings and priority.
         *
         * @return The best [Entity] target, or `null` if no valid target is found.
         */
        inline fun <reified T : Entity> target(): T? = runSafe {
            return@runSafe fastEntitySearch<T>(targetingRange) {
                validate(player, it)
            }.minByOrNull {
                priority.factor(this, it)
            }
        }

        private val illegalTargets = setOf(
            UUID(5706954458220675710, -6736729783554821869),
            UUID(-6076316721184881576, -7147993044363569449),
            UUID(-2932596226593701300, -7553629058088633089)
        )
    }

    /**
     * Enum representing the different priority factors used for determining the best target.
     *
     * @property factor A lambda function that calculates the priority factor for a given [Entity].
     */
    @Suppress("Unused")
    enum class Priority(val factor: SafeContext.(Entity) -> Double) {
        /**
         * Prioritizes entities based on their distance from the player.
         */
        Distance({ player.pos distSq it.pos }),

        /**
         * Prioritizes entities based on their health.
         * Entites that aren't an instanceof LivingEntity will be treated as if they are at 0 health,
         * therefore having least priority
         */
        Health({ (it as? LivingEntity)?.fullHealth ?: 0.0 }),

        /**
         * Prioritizes entities based on their angle relative to the player's field of view.
         */
        Fov({ player.rotation dist player.eyePos.rotationTo(it.pos) })
    }
}
