package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.context.SafeContext
import com.lambda.interaction.rotation.Rotation.Companion.dist
import com.lambda.interaction.rotation.Rotation.Companion.rotation
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.threading.runSafe
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.world.entitySearch
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.PassiveEntity

/**
 * Abstract class representing a targeting mechanism for entities in the game.
 *
 * This class provides the configuration and validation for targeting different types of entities
 * based on player settings and entity characteristics. It allows for specifying which types of entities
 * are targetable, the range of targeting, and various other conditions for targeting.
 *
 * @property owner The [Configurable] instance used to get and set configuration options for targeting.
 * @property predicate The predicate used to determine whether the targeting settings are visible and active.
 * @property defaultRange The default range within which entities can be targeted.
 * @property maxRange The maximum range within which entities can be targeted.
 */
abstract class Targeting(
    owner: Configurable,
    predicate: () -> Boolean = { true },
    defaultRange: Double,
    maxRange: Double
) : TargetingConfig {

    /**
     * The range within which entities can be targeted. This value is configurable and constrained
     * between 1.0 and [maxRange].
     */
    override val targetingRange by owner.setting("Targeting Range", defaultRange, 1.0..maxRange, 0.05) { predicate() }

    /**
     * Whether players are included in the targeting scope.
     */
    override val players by owner.setting("Players", true) { predicate() }

    /**
     * Whether mobs are included in the targeting scope.
     */
    private val mobs by owner.setting("Mobs", true) { predicate() }

    /**
     * Whether hostile mobs are included in the targeting scope
     */
    private val hostilesSetting by owner.setting("Hostiles", true) { predicate() && mobs }

    /**
     * Whether passive animals are included in the targeting scope
     */
    private val animalsSetting by owner.setting("Animals", true) { predicate() && mobs }

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
    override val invisible by owner.setting("Invisible", true) { predicate() }

    /**
     * Whether dead entities are included in the targeting scope.
     */
    override val dead by owner.setting("Dead", false) { predicate() }

    /**
     * Validates whether a given entity is targetable by the player based on current settings.
     *
     * @param player The [ClientPlayerEntity] performing the targeting.
     * @param entity The [LivingEntity] being evaluated.
     * @return `true` if the entity is valid for targeting, `false` otherwise.
     */
    open fun validate(player: ClientPlayerEntity, entity: LivingEntity) = when {
        !players && entity.isPlayer -> false
        !animals && entity is PassiveEntity -> false
        !hostiles && entity is MobEntity -> false
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
        owner: Configurable,
        predicate: () -> Boolean = { true },
    ) : Targeting(owner, predicate, 5.0, 16.0) {

        /**
         * The field of view limit for targeting entities. Configurable between 5 and 180 degrees.
         */
        val fov by owner.setting("FOV Limit", 180, 5..180, 1) { predicate() }

        /**
         * The priority used to determine which entity is targeted. Configurable with default set to [Priority.DISTANCE].
         */
        val priority by owner.setting("Priority", Priority.DISTANCE) { predicate() }

        /**
         * Validates whether a given entity is targetable for combat based on the field of view limit and other settings.
         *
         * @param player The [ClientPlayerEntity] performing the targeting.
         * @param entity The [LivingEntity] being evaluated.
         * @return `true` if the entity is valid for targeting, `false` otherwise.
         */
        override fun validate(player: ClientPlayerEntity, entity: LivingEntity): Boolean {
            if (fov < 180 && player.rotation dist player.eyePos.rotationTo(entity.pos) > fov) return false
            return super.validate(player, entity)
        }

        /**
         * Gets the best target for combat based on the current settings and priority.
         *
         * @return The best [LivingEntity] target, or `null` if no valid target is found.
         */
        fun getTarget(): LivingEntity? = runSafe {
            val predicate = { entity: LivingEntity ->
                validate(player, entity)
            }

            return@runSafe entitySearch<LivingEntity>(targetingRange) {
                predicate(it)
            }.minBy {
                priority.factor(this, it)
            }
        }
    }

    /**
     * Subclass for targeting entities for ESP (Extrasensory Perception) purposes.
     */
    class ESP(
        owner: Configurable,
        predicate: () -> Boolean = { true },
    ) : Targeting(owner, predicate, 128.0, 1024.0)

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
        DISTANCE({ player.pos distSq it.pos }),

        /**
         * Prioritizes entities based on their health.
         */
        HEALTH({ it.health.toDouble() }),

        /**
         * Prioritizes entities based on their angle relative to the player's field of view.
         */
        FOV({ player.rotation dist player.eyePos.rotationTo(it.pos) })
    }
}
