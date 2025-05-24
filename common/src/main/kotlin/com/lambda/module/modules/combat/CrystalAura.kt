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

package com.lambda.module.modules.combat

import com.lambda.Lambda
import com.lambda.config.groups.RotationSettings
import com.lambda.config.groups.Targeting
import com.lambda.context.SafeContext
import com.lambda.event.events.EntityEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.gl.Matrices.buildWorldProjection
import com.lambda.graphics.gl.Matrices.withVertexTransform
import com.lambda.graphics.renderer.gui.FontRenderer
import com.lambda.graphics.renderer.gui.FontRenderer.drawString
import com.lambda.interaction.request.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.request.rotation.RotationManager
import com.lambda.interaction.request.rotation.visibilty.VisibilityChecker.getVisibleSurfaces
import com.lambda.interaction.request.rotation.visibilty.lookAt
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeGameScheduled
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Communication.info
import com.lambda.util.PacketUtils.sendPacket
import com.lambda.util.Timer
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.combat.CombatUtils.crystalDamage
import com.lambda.util.math.MathUtils.ceilToInt
import com.lambda.util.math.MathUtils.roundToStep
import com.lambda.util.math.Vec2d
import com.lambda.util.math.distSq
import com.lambda.util.math.flooredBlockPos
import com.lambda.util.math.getHitVec
import com.lambda.util.math.minus
import com.lambda.util.math.plus
import com.lambda.util.world.fastEntitySearch
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.concurrent.fixedRateTimer
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

object CrystalAura : Module(
    name = "CrystalAura",
    description = "Automatically attacks entities with crystals",
    defaultTags = setOf(ModuleTag.COMBAT),
) {
    private val page by setting("Page", Page.General)

    /* General */
    private val placeRange by setting("Place Range", 4.6, 1.0..7.0, 0.1, "Range to place crystals", " blocks") { page == Page.General }
    private val explodeRange by setting("Explode Range", 3.0, 1.0..7.0, 0.1, "Range to explode crystals", " blocks") { page == Page.General }
    private val placeDelay by setting("Place Delay", 50L, 0L..1000L, 1L, "Delay between placement attempts", " ms") { page == Page.General }
    private val explodeDelay by setting("Explode Delay", 10L, 0L..1000L, 1L, "Delay between explosion attempts", " ms") { page == Page.General }
    private val updateMode by setting("Update Mode", UpdateMode.Async) { page == Page.General }
    private val updateDelaySetting by setting("Update Delay", 25L, 5L..200L, 5L, unit = " ms") { page == Page.General && updateMode == UpdateMode.Async }
    private val maxUpdatesPerFrame by setting("Max Updates Per Frame", 5, 1..20, 1) { page == Page.General && updateMode == UpdateMode.Async }
    private val updateDelay get() = if (updateMode == UpdateMode.Async) updateDelaySetting else 0L
    private val debug by setting("Debug", false) { page == Page.General }

    /* Placement */
    private val priorityMode by setting("Crystal Priority", Priority.Damage) { page == Page.Placement }
    private val minDamageAdvantage by setting("Min Damage Advantage", 4.0, 1.0..10.0, 0.5) { page == Page.Placement && priorityMode == Priority.Advantage }
    private val minTargetDamage by setting("Min Target Damage", 6.0, 0.0..20.0, 0.5, "Minimum target damage to use crystals") { page == Page.Placement }
    private val maxSelfDamage by setting("Max Self Damage", 8.0, 0.0..36.0, 0.5, "Maximum self damage to use crystals") { page == Page.Placement }
    //private val minHealth by setting("Min Health", 10.0, 0.0..36.0, 0.5, "Minimum player health to use crystals") { page == Page.General }
    private val oldPlace by setting("1.12 Placement", false) { page == Page.Placement }

    /* Prediction */
    private val prediction by setting("Prediction", PredictionMode.None) { page == Page.Prediction }

    private val packetPredictions by setting("Packet Predictions", 1, 0..20, 1) { page == Page.Prediction && prediction.onPacket }
    private val placePostPause by setting("Place Post Pause", true) { page == Page.Prediction && prediction.onPacket }

    private val placePredictions by setting("Place Predictions", 4, 1..20, 1) { page == Page.Prediction && prediction.onPlace }
    private val packetLifetime by setting("Packet Lifetime", 500L, 50L..1000L) { page == Page.Prediction && prediction.onPlace }

    /* Targeting */
    private val targeting = Targeting.Combat(this, 10.0) { page == Page.Targeting }

    /* Rotation */
    private val rotation = RotationSettings(this) { page == Page.Rotation }

    private val blueprint = mutableMapOf<BlockPos, Opportunity>()
    private var activeOpportunity: Opportunity? = null
    private var currentTarget: LivingEntity? = null

    private val damage = mutableListOf<Opportunity>()
    private val actionMap = mutableMapOf<ActionType, MutableList<Opportunity>>()
    private var actionType = ActionType.Normal

    private val updateTimer = Timer()
    private var updatesThisFrame = 0

    private val placeTimer = Timer()
    private val explodeTimer = Timer()

    private val predictionTimer = Timer()
    private var lastEntityId = 0

    private val decay = LimitedDecayQueue<Int>(10000, 3000L)

    private val collidingOffsets = mutableListOf<BlockPos>().apply {
        for (x in -1..1) {
            for (z in -1..1) {
                for (y in 0..1) {
                    if (x != 0 && y != 0 && z != 0) add(BlockPos(x, y, z))
                }
            }
        }
    }

    init {
        // Async ticking
        fixedRateTimer(
            name = "Crystal Aura Thread",
            daemon = true,
            initialDelay = 0L,
            period = 1L
        ) {
            if (CrystalAura.isDisabled || updateMode != UpdateMode.Async) return@fixedRateTimer

            runSafe {
                // timer may spam faster than main thread computes (game freezes completely at the beginning of the frame)
                if (updatesThisFrame > maxUpdatesPerFrame) return@runSafe
                updatesThisFrame++

                // run this safely again to ensure that the context will stay safe at the next frame
                runSafeGameScheduled {
                    tick()
                }
            }
        }

        fixedRateTimer(
            name = "CA Counter",
            daemon = true,
            initialDelay = 0L,
            period = 1000L
        ) {
            if (CrystalAura.isDisabled || !debug) return@fixedRateTimer

            runSafeGameScheduled {
                info((decay.size.toDouble() * 0.3333).roundToStep(0.1).toString())
            }
        }

        // Ticking with alignment
        listen<TickEvent.Pre> {
            if (updateMode == UpdateMode.Ticked) tick()
        }

        listen<TickEvent.Render.Post> {
            updatesThisFrame = 0
        }

        listen<RenderEvent.World> {
            if (!debug) return@listen

            Matrices.push {
                val c = Lambda.mc.gameRenderer.camera.pos.negate()
                translate(c.x, c.y, c.z)
                // Build the buffer
                blueprint.values.forEach {
                    it.buildDebug()
                }
            }
        }

        // Update last received entity spawn
        listen<EntityEvent.Spawn>(alwaysListen = true) { event ->
            lastEntityId = event.entity.id
            predictionTimer.reset()
        }

        // Prediction
        listen<EntityEvent.Spawn> { event ->
            val crystal = event.entity as? EndCrystalEntity ?: return@listen
            val pos = crystal.baseBlockPos

            // Update crystal
            val opportunity = blueprint[pos] ?: return@listen
            opportunity.crystal = crystal

            // Run packet prediction
            if (!prediction.isActive || activeOpportunity != opportunity) return@listen

            explodeInternal(lastEntityId)

            if (!prediction.onPacket) return@listen

            repeat(packetPredictions) {
                placeInternal(opportunity, Hand.MAIN_HAND)
                explodeInternal(++lastEntityId)
            }

            if (placePostPause) placeTimer.reset()
        }

        listen<EntityEvent.Removal> { event ->
            val crystal = event.entity as? EndCrystalEntity ?: return@listen
            val pos = crystal.baseBlockPos

            // Invalidate crystal entity
            val opportunity = blueprint[pos] ?: return@listen
            opportunity.crystal = null
            decay += crystal.id
        }

        onEnable {
            currentTarget = null
            resetBlueprint()
        }
    }

    private fun SafeContext.tick() {
        // Update the target
        currentTarget = targeting.target()

        // Update the blueprint
        currentTarget?.let {
            updateBlueprint(it)
        } ?: resetBlueprint()

        // Choosing and running the best opportunity
        activeOpportunity?.let {
            tickInteraction(it)
        }
    }

    private fun tickInteraction(best: Opportunity) {
        if (!best.blocked) {
            best.explode()
            best.place()
            return
        }

        val mutableBlockPos = BlockPos.Mutable()

        // Break crystals nearby if the best crystal placement is blocked by other crystals
        collidingOffsets.mapNotNull {
            mutableBlockPos.set(
                best.blockPos.x + it.x,
                best.blockPos.y + it.y,
                best.blockPos.z + it.z
            )

            blueprint[mutableBlockPos]
        }.filter { it.hasCrystal }.maxByOrNull { it.priority }?.explode()

        best.place()
    }

    private fun SafeContext.placeInternal(opportunity: Opportunity, hand: Hand) {
        connection.sendPacket {
            PlayerInteractBlockC2SPacket(
                hand, BlockHitResult(opportunity.crystalPosition, opportunity.side, opportunity.blockPos, false), 0
            )
        }

        player.swingHand(hand)
    }

    private fun SafeContext.explodeInternal(id: Int) {
        connection.sendPacket {
            PlayerInteractEntityC2SPacket(
                id, player.isSneaking, PlayerInteractEntityC2SPacket.ATTACK
            )
        }

        player.swingHand(Hand.MAIN_HAND)
    }

    private fun SafeContext.updateBlueprint(target: LivingEntity) =
        updateTimer.runIfPassed(updateDelay.milliseconds) {
            resetBlueprint()

        // Build damage info
        fun info(
            pos: BlockPos, target: LivingEntity,
            blocked: Boolean,
            crystal: EndCrystalEntity? = null
        ): Opportunity? {
            val crystalPos = pos.crystalPosition

            // Calculate the damage to the target from the explosion of the crystal
            val targetDamage = crystalDamage(crystalPos, target)
            if (targetDamage < minTargetDamage) return null

            // Calculate the self-damage for the player
            val selfDamage = crystalDamage(crystalPos, player)
            if (selfDamage > maxSelfDamage) return null

            if (priorityMode == Priority.Advantage && priorityMode.factor(targetDamage, selfDamage) < minDamageAdvantage) return null

            // Return the calculated damage info if conditions are met
            return Opportunity(
                pos.toImmutable(),
                targetDamage,
                selfDamage,
                blocked,
                crystal
            )
        }

        // Extra checks for placement, because you may explode but not place in special cases(crystal in the air)
        @Suppress("ConvertArgumentToSet")
        fun placeInfo(
            pos: BlockPos,
            target: LivingEntity
        ): Opportunity? {
            // Check if crystals could be placed on the base block
            val state = blockState(pos)
            val isOfBlock = state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK)
            if (!isOfBlock) return null

            // Check if the block above is air and other conditions for valid crystal placement
            val above = pos.up()
            if (!world.isAir(above)) return null
            if (oldPlace && !world.isAir(above.up())) return null

            // Exclude blocks blocked by entities
            val crystalBox = pos.crystalBox

            val entitiesNearby = fastEntitySearch<Entity>(3.5, pos)
            val crystals = entitiesNearby.filterIsInstance<EndCrystalEntity>() as MutableList
            val otherEntities = entitiesNearby - crystals + player

            if (otherEntities.any {
                    it.boundingBox.intersects(crystalBox)
                }) return null

            // Placement collision checks
            val baseCrystal = crystals.firstOrNull {
                it.baseBlockPos == pos
            }

            val crystalPlaceBox = pos.crystalPlaceHitBox
            val blocked = baseCrystal == null && crystals.any {
                it.boundingBox.intersects(crystalPlaceBox)
            }

            return info(
                pos,
                target,
                blocked,
                baseCrystal
            )
        }

        val range = max(placeRange, explodeRange) + 1
        val rangeInt = range.ceilToInt()

        // Iterate through existing crystals
        val crystalBase = BlockPos.Mutable()
        fastEntitySearch<EndCrystalEntity>(range).forEach { crystal ->
            crystalBase.set(crystal.x, crystal.y - 0.5, crystal.z)
            damage += info(crystalBase, target, false, crystal) ?: return@forEach
        }

        // Iterate through possible place positions and calculate damage information for each
        BlockPos.iterateOutwards(player.blockPos.up(), rangeInt, rangeInt, rangeInt).forEach { pos ->
            if (pos distSq player.pos > range * range) return@forEach
            if (damage.any { info -> info.blockPos == pos }) return@forEach

            damage += placeInfo(pos, target) ?: return@forEach
        }

        // Map opportunities
        damage.forEach {
            blueprint[it.blockPos] = it
        }

        // Associate by actions
        blueprint.values.forEach { opportunity ->
            actionMap.getOrPut(opportunity.actionType, ::mutableListOf) += opportunity

            if (opportunity.actionType.priority > actionType.priority) {
                actionType = opportunity.actionType
            }
        }

        // Select best action
        activeOpportunity = actionMap[actionType]?.maxByOrNull {
            it.priority
        }
    }

    private fun resetBlueprint() {
        blueprint.clear()
        damage.clear()
        actionMap.clear()
        activeOpportunity = null
    }

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
    private class Opportunity(
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
                if (side.axis != Direction.Axis.Y) vec += Vec3d(0.0, 0.45, 0.0)

                player.eyePos.rotationTo(vec)
            } ?: RotationManager.currentRotation
        }

        /**
         * Places the crystal on [blockPos]
         * @return Whether the delay passed, null if the interaction failed
         */
        fun place() {
            if (rotation.rotate && !lookAt(placeRotation).requestBy(rotation).done) return

            placeTimer.runSafeIfPassed(placeDelay.milliseconds) {
                placeInternal(this@Opportunity, Hand.MAIN_HAND)

                if (prediction.onPlace) predictionTimer.runIfNotPassed(packetLifetime.milliseconds, false) {
                    val last = lastEntityId

                    repeat(placePredictions) {
                        explodeInternal(++lastEntityId)
                    }

                    lastEntityId = last + 1
                    crystal = null
                }
            }
        }

        /**
         * Explodes a crystal that is on [blockPos]
         * @return Whether the delay passed, null if the interaction failed or no crystal found
         */
        fun explode() {
            if (rotation.rotate && !lookAt(placeRotation).requestBy(rotation).done) return

            explodeTimer.runSafeIfPassed(explodeDelay.milliseconds) {
                crystal?.let { crystal ->
                    explodeInternal(crystal.id)
                    explodeTimer.reset()
                }
            }
        }

        fun buildDebug() {
            withVertexTransform(buildWorldProjection(blockPos.crystalPosition, 0.4, Matrices.ProjRotationMode.TO_CAMERA)) {
                val lines = arrayOf(
                    "Decision: ${actionType.name} ${priority.roundToStep(0.01)}",
                    "",
                    "Blocked: $blocked",
                    "Crystal: $hasCrystal",
                    "",
                    "Target Damage: ${target.roundToStep(0.01)}",
                    "Self Damage: ${self.roundToStep(0.01)}"
                )

                var height = -0.5 * lines.size * (FontRenderer.getHeight() + 2)

                lines.forEach {
                    drawString(it, Vec2d(-FontRenderer.getWidth(it) * 0.5, height))
                    height += FontRenderer.getHeight() + 2
                }
            }
        }
    }

    private val EndCrystalEntity.baseBlockPos get() =
        (pos - Vec3d(0.0, 0.5, 0.0)).flooredBlockPos

    private val BlockPos.crystalPosition get() =
        this.getHitVec(Direction.UP)

    private val BlockPos.crystalPlaceHitBox get() =
        crystalPosition.let { base ->
            Box(
                base - Vec3d(1.0, 0.0, 1.0),
                base + Vec3d(1.0, 2.0, 1.0),
            )
        }

    private val BlockPos.crystalBox get() =
        crystalPosition.let { base ->
            Box(
                base - Vec3d(0.5, 0.0, 0.5),
                base + Vec3d(0.5, 2.0, 0.5),
            )
        }

    private enum class Page {
        General,
        Placement,
        Prediction,
        Targeting,
        Rotation
    }

    private enum class UpdateMode {
        Async,
        Ticked
    }

    @Suppress("Unused")
    private enum class PredictionMode(val onPacket: Boolean, val onPlace: Boolean) {
        // Prediction disable
        None(false, false),

        // Predict on packet receive
        Packet(true, false),

        // Predict on place
        Deferred(false, true),

        // Predict on both timings
        Mixed(true, true);

        val isActive = onPacket || onPlace
    }

    private enum class Priority(val factor: (targetDamage: Double, selfDamage: Double) -> Double) {
        Damage({ target, _ ->
            target
        }),
        Advantage({ target, self ->
            target - self
        })
    }

    // ToDo: implement actions
    @Suppress("Unused")
    private enum class ActionType(val priority: Int) {
        Normal(0),
        ForcePlace(1),
        SlowBreak(2)
    }
}
