/*
 * Copyright 2024 Lambda
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

package com.lambda.module.modules.combat

import com.lambda.config.groups.Targeting
import com.lambda.context.SafeContext
import com.lambda.event.events.EntityEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.RotationManager.rotate
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.RotationRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafeGameScheduled
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.SimpleTimer
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.combat.CombatUtils.explosionDamage
import com.lambda.util.math.MathUtils.ceilToInt
import com.lambda.util.math.MathUtils.sq
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.math.VecUtils.flooredBlockPos
import com.lambda.util.math.VecUtils.getHitVec
import com.lambda.util.math.VecUtils.minus
import com.lambda.util.world.fastEntitySearch
import com.lambda.util.world.raycast.RayCastMask
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.Blocks
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.concurrent.fixedRateTimer
import kotlin.math.ceil
import kotlin.math.max

object CrystalAura : Module(
    name = "CrystalAura",
    description = "Automatically attacks entities with crystals",
    defaultTags = setOf(ModuleTag.COMBAT),
) {
    private val page by setting("Page", Page.General)

    /* General */

    /* Targeting */
    // ToDo: Targeting Range should be reach + crystal range (also based on min damage)
    private val targeting = Targeting.Combat(this, 10.0) { page == Page.Targeting }

    /* Placing */
    private val placeRange by setting("Place Range", 4.6, 1.0..7.0, 0.1, "Range to place crystals", " blocks") { page == Page.General }
    private val explodeRange by setting("Explode Range", 3.0, 1.0..7.0, 0.1, "Range to explode crystals", " blocks") { page == Page.General }
    private val placeDelay by setting("Place Delay", 50L, 0L..1000L, 5L, "Delay between placement attempts", " ms") { page == Page.General }
    private val explodeDelay by setting("Explode Delay", 10L, 0L..1000L, 5L, "Delay between explosion attempts", " ms") { page == Page.General }

    private val minTargetDamage by setting("Min Target Damage", 6.0, 0.0..20.0, 0.5, "Minimum target damage to use crystals") { page == Page.General }
    private val maxSelfDamage by setting("Max Self Damage", 8.0, 0.0..20.0, 0.5, "Maximum self damage to use crystals") { page == Page.General }
    private val minHealth by setting("Min Health", 10.0, 0.0..36.0, 0.5, "Minimum player health to use crystals") { page == Page.General }

    private val oldPlace by setting("1.12 Placement", false) { page == Page.General }
    private val crystalHeight get() = 1.0 + oldPlace.toInt()

    private val updateMode by setting("Update Mode", Update.Async) { page == Page.General }
    private val updateDelaySetting by setting("Update Delay", 25L, 5L..200L, 5L) { page == Page.General && updateMode == Update.Async }
    private val updateDelay get() = if (updateMode == Update.Async) updateDelaySetting else 0L

    /* Rotation */
    //private val rotateToPlace by setting("Rotate To Place", true) { page == Page.Rotation }
    //private val rotateToExplode by setting("Rotate To Explode", true) { page == Page.Rotation }
    //private val rotation = RotationSettings(this) { page == Page.Rotation }

    //private val pendingPlacements = LimitedDecayQueue<BlockPos>(Int.MAX_VALUE, 1000L)
    //private val pendingExplosions = LimitedDecayQueue<BlockPos>(Int.MAX_VALUE, 1000L)

    private var rotationTarget: RotationRequest? = null

    // ToDo: crystal blueprint
    private val damage = mutableMapOf<BlockPos, DamageInfo>()
    private val updateTimer = SimpleTimer()

    private val placeTimer = SimpleTimer()
    private val explodeTimer = SimpleTimer()

    private const val EXPLOSION_STRENGTH = 6.0

    private enum class Page {
        General,
        Targeting
    }

    private enum class Update {
        Async,
        Ticked
    }

    init {
        fixedRateTimer(
            name = "Crystal Aura Thread",
            daemon = true,
            initialDelay = 0L,
            period = 3L
        ) {
            if (CrystalAura.isDisabled || updateMode != Update.Async) return@fixedRateTimer

            runSafeGameScheduled {
                tick()
            }
        }

        listen<TickEvent.Pre> {
            if (updateMode == Update.Ticked) tick()
        }

        rotate {
            request { rotationTarget }
        }

        /*listen<EntityEvent.EntitySpawn> { event ->
            val crystal = (event.entity as? EndCrystalEntity?) ?: return@listen
            pendingPlacements.remove(crystal.baseBlockPos)
        }*/

        /*listen<EntityEvent.EntityRemoval> { event ->
            val crystal = (event.entity as? EndCrystalEntity?) ?: return@listen
            pendingExplosions.remove(crystal.baseBlockPos)
        }*/

        /*listen<PacketEvent.Receive.Post> { event ->
            val packet = (event.packet as? PlaySoundS2CPacket) ?: return@listen
        }*/
    }

    private fun SafeContext.tick() {
        updateDamageMap()

        tickExplosion()
        tickPlacement()
    }

    private fun SafeContext.updateDamageMap() {
        val target = targeting.target() ?: run {
            damage.clear()
            return
        }

        updateTimer.runIfPassed(updateDelay) {
            damage.clear()

            val range = ceil(max(placeRange, explodeRange) + 1)
            val rangeInt = range.toInt()

            // Iterate through possible place positions and calculate damage information for each
            BlockPos.iterateOutwards(player.blockPos.up(), rangeInt, rangeInt, rangeInt).forEach { pos ->
                mapPlaceDamage(pos, target, rangeInt)
            }

            // Same for crystals, that could not be placed here, but exist (e.g. The base block was broken)
            val mutableBlockPos = BlockPos.Mutable()
            fastEntitySearch<EndCrystalEntity>(range).forEach {
                mutableBlockPos.set(it.x, it.y - 0.5, it.z)

                if (damage[mutableBlockPos] == null) {
                    mapDamage(mutableBlockPos, target)
                }
            }
        }
    }

    private fun SafeContext.tickExplosion() =
        explodeTimer.runIfPassed(explodeDelay) {
            val crystal = fastEntitySearch<EndCrystalEntity>(
                explodeRange + 1
            ).mapNotNull { crystal ->
                val damage = damage[crystal] ?: return@mapNotNull null

                crystal to damage
            }.maxByOrNull {
                it.second.target
            }?.first ?: return@runIfPassed

            explodeInternal(crystal.id)
        }

    private fun SafeContext.explodeInternal(id: Int) {
        connection.sendPacket(
            PlayerInteractEntityC2SPacket(
                id, player.isSneaking, PlayerInteractEntityC2SPacket.ATTACK
            )
        )

        player.swingHand(Hand.MAIN_HAND)
    }

    private fun SafeContext.tickPlacement() =
        placeTimer.runIfPassed(placeDelay) {
            val placeBlock = damage.values
                .maxByOrNull { it.target }?.blockPos ?: return@runIfPassed

            placeInternal(placeBlock, Hand.MAIN_HAND)
        }

    private fun SafeContext.placeInternal(blockPos: BlockPos, hand: Hand) {
        val angles = player.eyePos.rotationTo(blockPos.crystalPosition)
        val cast = angles.rayCast(placeRange, mask = RayCastMask.BLOCK)?.blockResult ?: return

        val actionResult = interaction.interactBlock(player, hand, cast)
        if (!actionResult.isAccepted || !actionResult.shouldSwingHand()) return

        player.swingHand(hand)
    }

    private fun SafeContext.mapPlaceDamage(pos: BlockPos, target: LivingEntity, range: Int) {
        if (pos distSq player.eyePos > range.sq) return

        // Check if crystals could be placed on the base block
        val state = pos.blockState(world)
        val isOfBlock = state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK)
        if (!isOfBlock) return

        // Check if the block above is air and other conditions for valid crystal placement
        val above = pos.up()
        if (!world.isAir(above)) return
        if (oldPlace && !world.isAir(above.up())) return

        // Exclude blocks blocked by entities
        val checkBox = Box(above).withMaxY(above.y + crystalHeight)
        val entitiesAbove = world.getOtherEntities(null, checkBox)
        if (entitiesAbove.any { it !is EndCrystalEntity }) return

        mapDamage(pos, target)
    }

    private fun SafeContext.mapDamage(pos: BlockPos, target: LivingEntity) {
        val crystalPos = pos.crystalPosition

        // Calculate the damage to the target from the explosion of the crystal
        val targetDamage = explosionDamage(crystalPos, target, EXPLOSION_STRENGTH)
        if (targetDamage < minTargetDamage) return

        // Calculate the self-damage for the player
        val selfDamage = explosionDamage(crystalPos, player, EXPLOSION_STRENGTH)
        if (selfDamage > maxSelfDamage) return

        val immutablePos = pos.toImmutable()

        // Return the calculated damage info if conditions are met
        damage[immutablePos] = DamageInfo(
            immutablePos,
            targetDamage,
            selfDamage
        )
    }

    private val EndCrystalEntity.baseBlockPos get() =
        (pos - Vec3d(0.0, 0.5, 0.0)).flooredBlockPos

    private val BlockPos.crystalPosition get() =
        this.getHitVec(Direction.UP)

    private operator fun MutableMap<BlockPos, DamageInfo>.get(entity: EndCrystalEntity) =
        get(entity.baseBlockPos)

    /**
     * Represents the damage information resulting from placing an end crystal on a given [blockPos]
     * and causing an explosion that targets current target entity.
     *
     * @property blockPos The position of the base block where the crystal is placed.
     * @property target The amount of damage inflicted on the target.
     * @property self The amount of damage inflicted on the player.
     */
    private open class DamageInfo(
        val blockPos: BlockPos,
        val target: Double,
        val self: Double,
    )
}
