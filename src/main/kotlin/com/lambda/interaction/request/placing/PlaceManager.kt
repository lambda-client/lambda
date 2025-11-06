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

package com.lambda.interaction.request.placing

import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.EventFlow.post
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.request.Logger
import com.lambda.interaction.request.ManagerUtils.isPosBlocked
import com.lambda.interaction.request.ManagerUtils.newStage
import com.lambda.interaction.request.ManagerUtils.newTick
import com.lambda.interaction.request.PositionBlocking
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.breaking.BreakManager
import com.lambda.interaction.request.interacting.InteractionManager
import com.lambda.interaction.request.placing.PlaceManager.activeRequest
import com.lambda.interaction.request.placing.PlaceManager.maxPlacementsThisTick
import com.lambda.interaction.request.placing.PlaceManager.placeBlock
import com.lambda.interaction.request.placing.PlaceManager.populateFrom
import com.lambda.interaction.request.placing.PlaceManager.potentialPlacements
import com.lambda.interaction.request.placing.PlaceManager.processRequest
import com.lambda.interaction.request.placing.PlacedBlockHandler.pendingActions
import com.lambda.interaction.request.placing.PlacedBlockHandler.setPendingConfigs
import com.lambda.interaction.request.placing.PlacedBlockHandler.startPending
import com.lambda.module.hud.ManagerDebugLoggers.placeManagerLogger
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Communication.warn
import com.lambda.util.player.MovementUtils.sneaking
import com.lambda.util.player.gamemode
import com.lambda.util.player.isItemOnCooldown
import com.lambda.util.player.swingHand
import net.minecraft.block.BlockState
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.sound.SoundCategory
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.GameMode
import kotlin.math.min

object PlaceManager : RequestHandler<PlaceRequest>(
    0,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post,
    onOpen = {
        if (potentialPlacements.isNotEmpty())
            PlaceManager.logger.newStage(PlaceManager.tickStage)
        activeRequest?.let { it.runSafeAutomated { processRequest(it) } }
    }
), PositionBlocking, Logger {
    private var activeRequest: PlaceRequest? = null
    private var potentialPlacements = mutableListOf<PlaceContext>()

    private var placementsThisTick = 0
    private var maxPlacementsThisTick = 0

    private var shouldSneak = false
    private val validSneak: (player: ClientPlayerEntity) -> Boolean =
        { player -> !shouldSneak || player.isSneaking }

    override val blockedPositions
        get() = pendingActions.map { it.context.blockPos }

    override val logger = placeManagerLogger

    override fun load(): String {
        super.load()

        listen<TickEvent.Pre>(priority = Int.MAX_VALUE) {
            if (potentialPlacements.isNotEmpty())
                logger.newTick()
        }

        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            activeRequest = null
            placementsThisTick = 0
            potentialPlacements.clear()
        }

        listen<MovementEvent.InputUpdate>(priority = Int.MIN_VALUE) {
            if (shouldSneak) {
                shouldSneak = false
                it.input.sneaking = true
            }
        }

        return "Loaded Place Manager"
    }

    /**
     * accepts, and processes the request, as long as the current [activeRequest] is null, and the [BreakManager] has not
     * been active this tick.
     *
     * @see processRequest
     */
    override fun AutomatedSafeContext.handleRequest(request: PlaceRequest) {
        if (activeRequest != null || request.contexts.isEmpty()) return

        activeRequest = request
        processRequest(request)
        if (placementsThisTick > 0) activeThisTick = true
    }

    /**
     * If the request is fresh, local variables are populated through the [processRequest] method.
     * It then attempts to perform as many placements within this tick as possible from the [potentialPlacements] collection.
     *
     * If all the [maxPlacementsThisTick] limit is reached and the user has rotations enabled, it will start rotating to
     * the next predicted placement in the list for optimal speed.
     *
     * @see populateFrom
     * @see placeBlock
     */
    fun AutomatedSafeContext.processRequest(request: PlaceRequest)  {
        if (BreakManager.activeThisTick || InteractionManager.activeThisTick) return

        logger.debug("Processing request", request)

        if (request.fresh) populateFrom(request)

        val iterator = potentialPlacements.iterator()
        while (iterator.hasNext()) {
            if (placementsThisTick + 1 > maxPlacementsThisTick) break
            val ctx = iterator.next()

            if (ctx.sneak) shouldSneak = true
            if (!ctx.requestDependencies(request)) {
                logger.warning("Dependencies failed for context", ctx, request)
                return
            }
            if (!validSneak(player)) return
            if (tickStage !in placeConfig.tickStageMask) return

            val actionResult = placeBlock(ctx, request, Hand.MAIN_HAND)
            if (!actionResult.isAccepted) {
                logger.warning("Placement interaction failed with $actionResult", ctx, request)
                warn("Placement interaction failed with $actionResult")
            }
            placementsThisTick++
            iterator.remove()
        }
        if (potentialPlacements.isEmpty()) {
            if (activeRequest != null) {
                logger.debug("Clearing active request", activeRequest)
                activeRequest = null
            }
        }
    }

    /**
     * Filters the [request]'s [PlaceContext]s, placing them into the [potentialPlacements] collection, and
     * setting the maxPlacementsThisTick value.
     *
     * @see isPosBlocked
     */
    private fun Automated.populateFrom(request: PlaceRequest) {
        logger.debug("Populating from request", request)
        setPendingConfigs()
        potentialPlacements = request.contexts
            .distinctBy { it.blockPos }
            .filter { !isPosBlocked(it.blockPos) }
            .take(
                min(
                    placeConfig.maxPendingPlacements - pendingActions.size,
                    buildConfig.maxPendingInteractions - request.pendingInteractions.size
                ).coerceAtLeast(0)
            )
            .toMutableList()
        logger.debug("${potentialPlacements.size} potential placements")

        maxPlacementsThisTick = placeConfig.placementsPerTick
    }

    /**
     * A modified version of the minecraft interactBlock method, renamed to better suit its usage.
     *
     * @see net.minecraft.client.network.ClientPlayerInteractionManager.interactBlock
     */
    private fun AutomatedSafeContext.placeBlock(placeContext: PlaceContext, request: PlaceRequest, hand: Hand): ActionResult {
        interaction.syncSelectedSlot()
        val hitResult = placeContext.hitResult
        if (!world.worldBorder.contains(hitResult.blockPos)) {
            logger.error("Placement position outside the world border", placeContext, request)
            return ActionResult.FAIL
        }
        if (gamemode == GameMode.SPECTATOR) {
            logger.error("Player is in spectator mode", placeContext, request)
            return ActionResult.PASS
        }
        return interactBlockInternal(placeContext, request, hand, hitResult)
    }

    /**
     * A modified version of the minecraft interactBlockInternal method.
     *
     * @see net.minecraft.client.network.ClientPlayerInteractionManager.interactBlockInternal
     */
    private fun AutomatedSafeContext.interactBlockInternal(
        placeContext: PlaceContext,
        request: PlaceRequest,
        hand: Hand,
        hitResult: BlockHitResult
    ): ActionResult {
        val handNotEmpty = player.getStackInHand(hand).isEmpty.not()
        val cantInteract = player.shouldCancelInteraction() && handNotEmpty
        if (!cantInteract) {
            val blockState = blockState(hitResult.blockPos)
            if (!connection.hasFeature(blockState.block.requiredFeatures)) {
                logger.error("Required features not met for $blockState", placeContext, request)
                return ActionResult.FAIL
            }

            val actionResult = blockState.onUse(world, player, hitResult)
            if (actionResult.isAccepted) {
                logger.error("Block state ($blockState) onUse not accepted", placeContext, request)
                return actionResult
            }
        }

        val stack = player.mainHandStack

        if (!stack.isEmpty && !isItemOnCooldown(stack)) {
            val itemUsageContext = ItemUsageContext(player, hand, hitResult)
            return if (gamemode.isCreative) {
                val i = stack.count
                useOnBlock(placeContext, request, hand, hitResult, stack, itemUsageContext)
                    .also {
                        stack.count = i
                    }
            } else
                useOnBlock(placeContext, request, hand, hitResult, stack, itemUsageContext)
        }
        return ActionResult.PASS
    }

    /**
     * A modified version of the minecraft useOnBlock method.
     *
     * @see net.minecraft.item.Item.useOnBlock
     */
    private fun AutomatedSafeContext.useOnBlock(
        placeContext: PlaceContext,
        request: PlaceRequest,
        hand: Hand,
        hitResult: BlockHitResult,
        itemStack: ItemStack,
        context: ItemUsageContext
    ): ActionResult {
        val cachedBlockPosition = CachedBlockPosition(world, context.blockPos, false)

        val cantModifyWorld = !player.abilities.allowModifyWorld
        val cantPlaceOn = !itemStack.canPlaceOn(cachedBlockPosition)
        if (cantModifyWorld && cantPlaceOn) {
            logger.error("Cannot modify world", placeContext, request)
            return ActionResult.PASS
        }

        val item = (itemStack.item as? BlockItem) ?: run {
            logger.error("Item ${itemStack.item.name} is not a block item", placeContext, request)
            return ActionResult.PASS
        }

        return place(placeContext, request, hand, hitResult, item, ItemPlacementContext(context))
    }

    /**
     * A modified version of the minecraft place method.
     *
     * @see net.minecraft.item.BlockItem.place
     */
    private fun AutomatedSafeContext.place(
        placeContext: PlaceContext,
        request: PlaceRequest,
        hand: Hand,
        hitResult: BlockHitResult,
        item: BlockItem,
        context: ItemPlacementContext
    ): ActionResult {
        if (!item.block.isEnabled(world.enabledFeatures)) {
            logger.error("Block ${item.block.name} is not enabled", placeContext, request)
            return ActionResult.FAIL
        }
        if (!context.canPlace()) {
            logger.error("Cannot place at ${placeContext.blockPos} with current state ${placeContext.cachedState}", placeContext, request)
            return ActionResult.FAIL
        }

        val itemPlacementContext = item.getPlacementContext(context) ?: run {
            logger.error("Could not retrieve item placement context", placeContext, request)
            return ActionResult.FAIL
        }
        val blockState = item.getPlacementState(itemPlacementContext) ?: run {
            logger.error("Could not retrieve placement state", placeContext, request)
            return ActionResult.FAIL
        }

        val stackInHand = player.getStackInHand(hand)
        val stackCountPre = stackInHand.count
        if (placeConfig.placeConfirmationMode != PlaceConfig.PlaceConfirmationMode.None) {
            PlaceInfo(placeContext, request.pendingInteractions, request.onPlace, placeConfig).startPending()
        }

        if (placeConfig.airPlace == PlaceConfig.AirPlaceMode.Grim) {
            val placeHand = if (hand == Hand.MAIN_HAND) Hand.OFF_HAND else Hand.MAIN_HAND
            airPlaceOffhandSwap()
            sendPlacePacket(placeHand, hitResult)
            airPlaceOffhandSwap()
        } else {
            sendPlacePacket(hand, hitResult)
        }

        if (placeConfig.swing) {
            swingHand(placeConfig.swingType, hand)

            if (!stackInHand.isEmpty && (stackInHand.count != stackCountPre || player.isInCreativeMode)) {
                mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand)
            }
        }

        val itemStack = itemPlacementContext.stack
        if (!player.abilities.creativeMode) itemStack.decrement(1)

        if (placeConfig.placeConfirmationMode == PlaceConfig.PlaceConfirmationMode.AwaitThenPlace)
            return ActionResult.SUCCESS

        // TODO: Implement restriction checks (e.g., world height) to prevent unnecessary server requests when the
        //  "AwaitThenPlace" confirmation setting is enabled, as the block state setting methods that validate these
        //  rules are not called.
        if (!item.place(itemPlacementContext, blockState)) {
            logger.error("Could not place block client side at ${placeContext.blockPos} with placement state ${placeContext.expectedState}", placeContext, request)
            return ActionResult.FAIL
        }

        val blockPos = itemPlacementContext.blockPos
        var state = world.getBlockState(blockPos)
        if (state.isOf(blockState.block)) {
            state = item.placeFromNbt(blockPos, world, itemStack, state)
            item.postPlacement(blockPos, world, player, itemStack, state)
            state.block.onPlaced(world, blockPos, state, player, itemStack)
        }

        if (placeConfig.sounds) placeSound(state, blockPos)

        if (placeConfig.placeConfirmationMode == PlaceConfig.PlaceConfirmationMode.None) {
            request.onPlace?.invoke(placeContext.blockPos)
        }

        logger.success("Placed ${placeContext.expectedState} at ${placeContext.blockPos}", placeContext, request)

        return ActionResult.SUCCESS
    }

    /**
     * sends the block placement packet using the given [hand] and [hitResult].
     */
    private fun SafeContext.sendPlacePacket(hand: Hand, hitResult: BlockHitResult) =
        interaction.sendSequencedPacket(world) { sequence: Int ->
            PlayerInteractBlockC2SPacket(hand, hitResult, sequence)
        }

    /**
     * Plays the block placement sound at a given position.
     */
    fun SafeContext.placeSound(state: BlockState, pos: BlockPos) {
        val blockSoundGroup = state.soundGroup
        world.playSound(
            player,
            pos,
            state.soundGroup.placeSound,
            SoundCategory.BLOCKS,
            (blockSoundGroup.getVolume() + 1.0f) / 2.0f,
            blockSoundGroup.getPitch() * 0.8f
        )
    }

    /**
     * Must be called before and after placing a block to bypass grim's air place checks.
     */
    private fun SafeContext.airPlaceOffhandSwap() {
        connection.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN,
                Direction.DOWN
            )
        )
    }

    override fun preEvent(): Event = UpdateManagerEvent.Place.post()
}
