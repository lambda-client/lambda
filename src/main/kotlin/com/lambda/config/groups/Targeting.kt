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

package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.config.SettingGroup
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
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.PassiveEntity
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
    private val c: Configurable,
    baseGroup: NamedEnum,
    private val defaultRange: Double,
    private val maxRange: Double,
) : SettingGroup(c), TargetingConfig {
    /**
     * The range within which entities can be targeted. This value is configurable and constrained
     * between 1.0 and [maxRange].
     */
    override val targetingRange by c.setting("Targeting Range", defaultRange, 1.0..maxRange, 0.05).group(baseGroup)

    /**
     * Whether players are included in the targeting scope.
     */
    override val players by c.setting("Players", true).group(baseGroup)

    /**
     * Whether friends are included in the targeting scope.
     * Requires [players] to be true.
     */
    override val friends by c.setting("Friends", false) { players }.group(baseGroup)

    /**
     * Whether mobs are included in the targeting scope.
     */
    private val mobs by c.setting("Mobs", true).group(baseGroup)

    /**
     * Whether hostile mobs are included in the targeting scope
     */
    private val hostilesSetting by c.setting("Hostiles", true) { mobs }.group(baseGroup)

    /**
     * Whether passive animals are included in the targeting scope
     */
    private val animalsSetting by c.setting("Animals", true) { mobs }.group(baseGroup)

    /**
     * Indicates whether hostile entities are included in the targeting scope.
     */
    override val hostiles get() = mobs && hostilesSetting

    /**
     * Indicates whether passive animals are included in the targeting scope.
     */
    override val animals get() = mobs && animalsSetting

    /**
     * Whether invisible entities are included in the targeting scope.
     */
    override val invisible by c.setting("Invisible", true).group(baseGroup)

    /**
     * Whether dead entities are included in the targeting scope.
     */
    override val dead by c.setting("Dead", false).group(baseGroup)

    /**
     * Validates whether a given entity is targetable by the player based on current settings.
     *
     * @param player The [ClientPlayerEntity] performing the targeting.
     * @param entity The [LivingEntity] being evaluated.
     * @return `true` if the entity is valid for targeting, `false` otherwise.
     */
    open fun validate(player: ClientPlayerEntity, entity: LivingEntity) = when {
        !players && entity is OtherClientPlayerEntity -> false
        players && entity is OtherClientPlayerEntity && entity.isFriend -> false
        !animals && entity is PassiveEntity -> false
        !hostiles && entity is HostileEntity -> false
        entity is ArmorStandEntity -> false

        !invisible && entity.isInvisibleTo(player) -> false
        !dead && entity.isDead -> false

        else -> true
    }

    /**
     * Subclass for targeting entities specifically for combat purposes.
     *
     * @property fov The field of view limit within which entities are considered for targeting. Configurable.
     * @property priority The priority used to determine which entity is targeted when multiple candidates are available.
     */
    class Combat(
        c: Configurable,
        baseGroup: NamedEnum,
        defaultRange: Double = 5.0,
        maxRange: Double = 16.0,
    ) : Targeting(c, baseGroup, defaultRange, maxRange) {

        /**
         * The field of view limit for targeting entities. Configurable between 5 and 180 degrees.
         */
        val fov by c.setting("FOV Limit", 180, 5..180, 1) { priority == Priority.Fov }.group(baseGroup)

        /**
         * The priority used to determine which entity is targeted. Configurable with default set to [Priority.Distance].
         */
        val priority by c.setting("Priority", Priority.Distance).group(baseGroup)

        /**
         * Validates whether a given entity is targetable for combat based on the field of view limit and other settings.
         *
         * @param player The [ClientPlayerEntity] performing the targeting.
         * @param entity The [LivingEntity] being evaluated.
         * @return `true` if the entity is valid for targeting, `false` otherwise.
         */
        override fun validate(player: ClientPlayerEntity, entity: LivingEntity): Boolean {
            if (fov < 180 && player.rotation dist player.eyePos.rotationTo(entity.pos) > fov) return false
            if (entity.uuid in illegalTargets) return false
            return super.validate(player, entity)
        }

        /**
         * Gets the best target for combat based on the current settings and priority.
         *
         * @return The best [LivingEntity] target, or `null` if no valid target is found.
         */
        fun target(): LivingEntity? = runSafe {
            return@runSafe fastEntitySearch<LivingEntity>(targetingRange) {
                validate(player, it)
            }.minByOrNull {
                priority.factor(this, it)
            }
        }

        private val illegalTargets = setOf(
            UUID(5706954458220675710, -6736729783554821869)
        )
    }

    /**
     * Subclass for targeting entities for ESP (Extrasensory Perception) purposes.
     */
    class ESP(
        c: Configurable,
        baseGroup: NamedEnum,
    ) : Targeting(c, baseGroup, 128.0, 1024.0)

    /**
     * Enum representing the different priority factors used for determining the best target.
     *
     * @property factor A lambda function that calculates the priority factor for a given [LivingEntity].
     */
    @Suppress("Unused")
    enum class Priority(val factor: SafeContext.(LivingEntity) -> Double) {
        /**
         * Prioritizes entities based on their distance from the player.
         */
        Distance({ player.pos distSq it.pos }),

        /**
         * Prioritizes entities based on their health.
         */
        Health({ it.fullHealth }),

        /**
         * Prioritizes entities based on their angle relative to the player's field of view.
         */
        Fov({ player.rotation dist player.eyePos.rotationTo(it.pos) })
    }
}
