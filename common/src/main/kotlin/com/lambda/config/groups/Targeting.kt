package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.context.SafeContext
import com.lambda.interaction.rotation.Rotation.Companion.dist
import com.lambda.interaction.rotation.Rotation.Companion.rotation
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.threading.runSafe
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.world.SearchContext
import com.lambda.util.world.WorldUtils.getFastEntities
import com.lambda.util.world.search
import com.lambda.util.world.toFastVec
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.PassiveEntity

abstract class Targeting(
    c: Configurable,
    vis: () -> Boolean = { true },
    defaultRange: Double,
    maxRange: Double
) : TargetingConfig {
    override val targetingRange by c.setting("Targeting Range", defaultRange, 1.0..maxRange, 0.05) { vis() }

    override val players by c.setting("Players", true) { vis() }
    private val mobs by c.setting("Mobs", true) { vis() }
    private val hostilesSetting by c.setting("Hostiles", true) { vis() && mobs }
    private val animalsSetting by c.setting("Animals", true) { vis() && mobs }
    override val hostiles get() = mobs && hostilesSetting
    override val animals get() = mobs && animalsSetting

    override val invisible by c.setting("Invisible", true) { vis() }
    override val dead by c.setting("Dead", false) { vis() }

    open fun validate(player: ClientPlayerEntity, entity: LivingEntity) = when {
        !players && entity.isPlayer -> false
        !animals && entity is PassiveEntity -> false
        !hostiles && entity is MobEntity -> false

        !invisible && entity.isInvisibleTo(player) -> false
        !dead && entity.isDead -> false

        else -> true
    }

    class Combat(
        c: Configurable,
        vis: () -> Boolean = { true },
    ) : Targeting(c, vis, 5.0, 16.0) {
        val fov by c.setting("FOV Limit", 180, 5..180, 1) { vis() }
        val priority by c.setting("Priority", Priority.DISTANCE) { vis() }

        override fun validate(player: ClientPlayerEntity, entity: LivingEntity): Boolean {
            if (fov < 180 && player.rotation dist player.eyePos.rotationTo(entity.pos) > fov) return false
            return super.validate(player, entity)
        }

        fun getTarget(): LivingEntity? = runSafe {
            var best: LivingEntity? = null

            val predicate = { entity: LivingEntity ->
                validate(player, entity)
            }

            search {
                best = minEntityBy<LivingEntity>(targetingRange, predicate, priority.factor)
            }

            return@runSafe best
        }
    }

    class ESP(
        c: Configurable,
        vis: () -> Boolean = { true },
    ) : Targeting(c, vis, 128.0, 1024.0)

    @Suppress("Unused")
    enum class Priority(val factor: SafeContext.(LivingEntity) -> Double) {
        DISTANCE({ player.pos distSq it.pos }),
        HEALTH({ it.health.toDouble() }),
        FOV({ player.rotation dist player.eyePos.rotationTo(it.pos) })
    }
}
