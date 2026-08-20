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

package com.lambda.module.modules.combat

import com.lambda.config.Tab
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.blocks.TargetingSettings
import com.lambda.config.hide
import com.lambda.config.hideAllExcept
import com.lambda.config.blocks.WorldLineSettings
import com.lambda.config.forEachSetting
import com.lambda.config.withEdits
import com.lambda.context.SafeContext
import com.lambda.event.events.EntityEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.interaction.handlers.ContainerHandler.transfer
import com.lambda.interaction.managers.hotbar.HotbarRequest
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.containers.HotbarContainer
import com.lambda.interaction.material.container.containers.OffHandContainer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeAutomated
import com.lambda.threading.runSafeGameScheduled
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.CommunicationUtils.log
import com.lambda.util.PacketUtils.sendPacket
import com.lambda.util.Timer
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.combat.CombatUtils.crystalDamage
import com.lambda.util.extension.fullHealth
import com.lambda.util.item.ItemStackUtils.slotId
import com.lambda.util.math.*
import com.lambda.util.math.MathUtils.ceilToInt
import com.lambda.util.math.MathUtils.roundToStep
import com.lambda.util.player.RotationUtils.getVisibleSurfaces
import com.lambda.util.player.SlotUtils.hotbarSlots
import com.lambda.util.player.SlotUtils.hotbarStacks
import com.lambda.util.world.fastEntitySearch
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.concurrent.fixedRateTimer
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

@Suppress("unused")
object CrystalAura : Module(
    name = "CrystalAura",
    description = "Automatically attacks entities with crystals",
    tag = ModuleTag.COMBAT,
) {
    private const val GENERAL_TAB = "General"
    private const val PLACEMENT_TAB = "Placement"
    private const val EXPLODING_TAB = "Exploding"
    private const val PREDICTION_TAB = "Prediction"
    private const val TARGETING_TAB = "Targeting"
    private const val RENDERING_TAB = "Rendering"

    @Tab(GENERAL_TAB) private val rotate by setting("Rotate", true)
    @Tab(GENERAL_TAB) private val updateMode by setting("Update Mode", UpdateMode.Async)
    @Tab(GENERAL_TAB) private val updateDelaySetting by setting("Update Delay", 25L, 5L..200L, 5L, unit = " ms") { updateMode == UpdateMode.Async }
    @Tab(GENERAL_TAB) private val maxUpdatesPerFrame by setting("Max Updates Per Frame", 5, 1..20, 1) { updateMode == UpdateMode.Async }
    @Tab(GENERAL_TAB) private val updateDelay get() = if (updateMode == UpdateMode.Async) updateDelaySetting else 0L
    @Tab(GENERAL_TAB) private val debug by setting("Debug", false)

    @Tab(PLACEMENT_TAB) private val placeRange by setting("Place Range", 4.6, 1.0..7.0, 0.1, "Range to place crystals", " blocks")
    @Tab(PLACEMENT_TAB) private val placeDelay by setting("Place Delay", 50L, 0L..1000L, 1L, "Delay between placement attempts", " ms")
    @Tab(PLACEMENT_TAB) private val swap by setting("Swap", true, "Swaps to crystals")
    @Tab(PLACEMENT_TAB) private val swapHand by setting("Swap Hand", Hand.MAIN_HAND, "Which hand to swap the crystal to") { swap }
    @Tab(PLACEMENT_TAB) private val priorityMode by setting("Crystal Priority", Priority.Damage)
    @Tab(PLACEMENT_TAB) private val minDamageAdvantage by setting("Min Damage Advantage", 4.0, 1.0..10.0, 0.5) { priorityMode == Priority.Advantage }
    @Tab(PLACEMENT_TAB) private val minTargetDamage by setting("Min Target Damage", 8.0, 0.0..20.0, 0.5, "Minimum target damage to use crystals")
    @Tab(PLACEMENT_TAB) private val maxSelfDamage by setting("Max Self Damage", 8.0, 0.0..36.0, 0.5, "Maximum self damage to use crystals")
    @Tab(PLACEMENT_TAB) private val minPlaceHealth by setting("Min Place Health", 5.0, 0.0..36.0, 0.5, "Minimum player health to place crystals")
    @Tab(PLACEMENT_TAB) private val preventDeath by setting("Prevent Death", true, "Prevent death by crystal")
    @Tab(PLACEMENT_TAB) private val oldPlace by setting("1.12 Placement", false)

    @Tab(EXPLODING_TAB) private val explodeRange by setting("Explode Range", 3.0, 1.0..7.0, 0.1, "Range to explode crystals", " blocks")
    @Tab(EXPLODING_TAB) private val explodeDelay by setting("Explode Delay", 10L, 0L..1000L, 1L, "Delay between explosion attempts", " ms")

    @Tab(PREDICTION_TAB) private val prediction by setting("Prediction", PredictionMode.None)
    @Tab(PREDICTION_TAB) private val packetPredictions by setting("Packet Predictions", 1, 0..20, 1) { prediction.onPacket }
    @Tab(PREDICTION_TAB) private val placePostPause by setting("Place Post Pause", true) { prediction.onPacket }
    @Tab(PREDICTION_TAB) private val placePredictions by setting("Place Predictions", 4, 1..20, 1) { prediction.onPlace }
    @Tab(PREDICTION_TAB) private val packetLifetime by setting("Packet Lifetime", 500L, 50L..1000L) { prediction.onPlace }

    @Tab(PREDICTION_TAB) private val targetingSettings by configBlock(TargetingSettings.CombatSettings(this, 10.0))

    @Tab(RENDERING_TAB) private val render by setting("Rendering", true)
    @Tab(RENDERING_TAB) private val renderPlacements by setting("Placement Rendering", true)

    @Tab(RENDERING_TAB) private val renderLineSettings by configBlock(WorldLineSettings(this))
        .withEdits {
            hideAllExcept (
                ::worldWidthSetting
            )
            forEachSetting {
                visibility { old -> { old() && render }}
            }
        }
    @Tab(RENDERING_TAB) val primaryColorLine by setting("Primary Color (outline)", Color(130, 200, 255, 200), visibility = { render })
    @Tab(RENDERING_TAB) val secondaryColorLine by setting("Secondary Color (outline)", Color(130, 130, 255, 200), visibility = { render })
    @Tab(RENDERING_TAB) val primaryColor by setting("Primary Color", Color(225, 130, 225, 100), visibility = { render })
    @Tab(RENDERING_TAB) val secondaryColor by setting("Secondary Color", Color(170, 60, 170, 100), visibility = { render })

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

    private var lastPlace: Pair<BlockPos, Long>? = null
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
		setDefaultAutomationConfig()
            .withEdits {
                hideAllExcept(::buildConfig, ::rotationConfig, ::hotbarConfig, ::inventoryConfig)
                buildConfig.apply {
                    hide(
                        ::pathing, ::collectDrops, ::spleefEntities,
                        ::maxPendingActions, ::actionTimeout, ::maxBuildDependencies, ::breakBlocks, ::interactBlocks, ::placeBlocks
                    )
                }
            }

        // Async ticking
        fixedRateTimer(
            name = "Crystal Aura Thread",
            daemon = true,
            initialDelay = 0L,
            period = 1L
        ) {
            if (isDisabled || updateMode != UpdateMode.Async) return@fixedRateTimer

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
            if (isDisabled || !debug) return@fixedRateTimer

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
                placeInternal(opportunity, swapHand)
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

        immediateRenderer("CrystalAura Immediate Renderer") {
            runSafe {
                if (lastPlace != null) {
                    if (lastPlace!!.second + 100 < System.currentTimeMillis()) {
                        return@runSafe
                    }

                    box(
                        lastPlace!!.first,
                        renderLineSettings
                    ) {
                        outlineGradientY(secondaryColorLine, primaryColorLine)
                        fillGradientY(secondaryColor, primaryColor)
                    }
                }
            }
        }

        onEnable {
            currentTarget = null
            resetBlueprint()
        }
    }

    private fun SafeContext.tick() {
        // Update the target
        currentTarget = targetingSettings.target<LivingEntity>()

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
        runSafe {
            lastPlace = Pair(opportunity.blockPos, System.currentTimeMillis())
        }
		interaction.syncSelectedSlot() // TODO: when server only hotbar swap gets implemented, this will be removed
        mc.interactionManager!!.sendSequencedPacket(world, { sequence ->

            PlayerInteractBlockC2SPacket(
                hand, BlockHitResult(opportunity.crystalPosition, opportunity.side, opportunity.blockPos, false), sequence
            )
        });

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

            fun info(
                pos: BlockPos,
                target: LivingEntity,
                blocked: Boolean,
                crystal: EndCrystalEntity? = null
            ): Opportunity? {
                val crystalPos = pos.crystalPosition

                val targetDamage = crystalDamage(crystalPos, target)
                if (targetDamage < minTargetDamage) return null

                val selfDamage = crystalDamage(crystalPos, player)
                if (selfDamage > maxSelfDamage ||
                    player.fullHealth - selfDamage <= minPlaceHealth ||
                    (preventDeath && player.fullHealth - selfDamage <= 0)
                ) return null

                if (priorityMode == Priority.Advantage && priorityMode.factor(
                        targetDamage,
                        selfDamage
                    ) < minDamageAdvantage
                ) return null

                return Opportunity(
                    pos.toImmutable(),
                    targetDamage,
                    selfDamage,
                    blocked,
                    crystal
                )
            }

            // Extra checks for placement, because you may explode but not place in special cases(crystal in the air)
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
                val crystals = entitiesNearby.filterIsInstance<EndCrystalEntity>()
                val otherEntities = entitiesNearby - crystals.toSet() + player

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
            activeOpportunity = actionMap[actionType]?.filter { !it.blocked }?.maxByOrNull {
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
            } ?: RotationManager.activeRotation
        }

        /**
         * Places the crystal on [blockPos]
         */
        fun place() = runSafe {
            if (rotate && !rotationRequest { rotation(placeRotation) }.submit().done)
                return@runSafe
            var crystalHand: Hand? = null
			val selection = selectStack { isItem(Items.END_CRYSTAL) }
			if ((swapHand == Hand.MAIN_HAND && player.mainHandStack.item != Items.END_CRYSTAL) ||
				(swapHand == Hand.OFF_HAND && player.offHandStack.item != Items.END_CRYSTAL)
			) runSafeAutomated {
				if (!swap) return@runSafe
                var hotbarStack: ItemStack? = null

                fun findInHotbar(): Pair<Boolean, Int> {
                    hotbarStack = selection.bestItemMatch(player.hotbarStacks)?: return (false to -1)
                    val hotbarSlot = hotbarStack.slotId
                    return (hotbarSlot >= 0) to hotbarSlot
                }

                fun findInOffhand(): Pair<Boolean, Int> {
                    return (player.offHandStack.item == selection.item) to player.offHandStack.slotId
                }

                val crystalSearch = when (swapHand) {
                    Hand.MAIN_HAND -> listOf(
                        Hand.MAIN_HAND to ::findInHotbar,
                        Hand.OFF_HAND to ::findInOffhand
                    )
                    Hand.OFF_HAND -> listOf(
                        Hand.OFF_HAND to ::findInOffhand,
                        Hand.MAIN_HAND to ::findInHotbar
                    )
                }

                val searchResult = crystalSearch
                    .asSequence()
                    .map { (hand, finder) ->
                        val (found, slot) = finder()
                        Triple(found, slot, hand)
                    }
                    .firstOrNull { (found) -> found }

                crystalHand = searchResult?.third

				if (crystalHand == null) { // no crystal stack found in main or offhand
					val swapTo = when (swapHand) {
                        //TODO: add support for server side hotbar switching
						Hand.MAIN_HAND -> HotbarContainer
						Hand.OFF_HAND -> OffHandContainer
					}

                    // retrieve from inventory, return if the task is not ready
					if (!selection.transfer(swapTo)) return@runSafe
				}
                if (crystalHand == Hand.MAIN_HAND && swapHand == Hand.MAIN_HAND) {
                    val s = player.hotbarStacks.indexOf(hotbarStack?: return@runSafe)
                    Lambda.LOG.info("$s, $swapHand, $crystalHand, ${player.hotbarStacks.size}")
                    if (!HotbarRequest(s, this@CrystalAura, nowOrNothing = false).submit().done) return@runSafe
                } else if (crystalHand == null) {
                    return@runSafe
                }
			}

            placeTimer.runSafeIfPassed(placeDelay.milliseconds) {
                placeInternal(this@Opportunity, crystalHand?: swapHand) // we should not be here without a crystal in hand but ig better to check than not to

                if (prediction.onPlace)
                    predictionTimer.runIfNotPassed(packetLifetime.milliseconds, false) {
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
            if (rotate && !rotationRequest { rotation(placeRotation) }.submit().done) return

            explodeTimer.runSafeIfPassed(explodeDelay.milliseconds) {
                crystal?.let { crystal ->
                    explodeInternal(crystal.id)
                    explodeTimer.reset()
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
