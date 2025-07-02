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

import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.EventFlow.post
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.request.PositionBlocking
import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.breaking.BreakManager
import com.lambda.interaction.request.interacting.InteractionManager
import com.lambda.interaction.request.placing.PlaceManager.activeRequest
import com.lambda.interaction.request.placing.PlaceManager.processRequest
import com.lambda.interaction.request.placing.PlacedBlockHandler.addPendingPlace
import com.lambda.interaction.request.placing.PlacedBlockHandler.pendingPlacements
import com.lambda.interaction.request.placing.PlacedBlockHandler.setPendingConfigs
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Communication.warn
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
import net.minecraft.registry.RegistryKeys
import net.minecraft.sound.SoundCategory
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.GameMode

object PlaceManager : RequestHandler<PlaceRequest>(
    0,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post,
    onOpen = { activeRequest?.let { processRequest(it) } }
), PositionBlocking {
    private var activeRequest: PlaceRequest? = null
    private var potentialPlacements = mutableListOf<PlaceContext>()

    private var placementsThisTick = 0
    private var maxPlacementsThisTick = 0

    private var shouldSneak = false
    private val validSneak: (player: ClientPlayerEntity) -> Boolean =
        { player -> shouldSneak == player.isSneaking }

    override val blockedPositions
        get() = pendingPlacements.map { it.context.expectedPos }

    fun Any.onPlace(
        alwaysListen: Boolean = false,
        priority: Priority = 0,
        block: SafeContext.() -> Unit
    ) = this.listen<UpdateManagerEvent.Place>(priority, alwaysListen) {
        block()
    }

    override fun load(): String {
        super.load()

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
    override fun SafeContext.handleRequest(request: PlaceRequest) {
        if (activeRequest != null || BreakManager.activeThisTick || InteractionManager.activeThisTick) return

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
    fun SafeContext.processRequest(request: PlaceRequest) {
        pendingPlacements.cleanUp()

        if (request.fresh) populateFrom(request)

        val iterator = potentialPlacements.iterator()
        while (iterator.hasNext()) {
            if (placementsThisTick + 1 > maxPlacementsThisTick) break
            val ctx = iterator.next()

            if (ctx.sneak) shouldSneak = true
            if (!ctx.requestDependencies(request) || !validSneak(player)) return
//            if (tickStage !in request.build.placing.placeStageMask) return

            val actionResult = placeBlock(ctx, request, Hand.MAIN_HAND)
            if (!actionResult.isAccepted) warn("Placement interaction failed with $actionResult")
            placementsThisTick++
            iterator.remove()
        }
        if (potentialPlacements.isEmpty()) activeRequest = null
    }

    /**
     * Filters the [request]'s [PlaceContext]s, placing them into the [potentialPlacements] collection, and
     * setting the maxPlacementsThisTick value.
     *
     * @see canPlace
     */
    private fun populateFrom(request: PlaceRequest) {
        val place = request.build.placing

        setPendingConfigs(request)
        potentialPlacements = request.contexts
            .filter { canPlace(it) }
            .toMutableList()

        val pendingLimit =  (place.maxPendingPlacements - pendingPlacements.size).coerceAtLeast(0)
        maxPlacementsThisTick = (place.placementsPerTick.coerceAtMost(pendingLimit))
    }

    /**
     * @return if none of the [pendingPlacements] match positions with the [placeContext]
     */
    private fun canPlace(placeContext: PlaceContext) =
        pendingPlacements.none { pending ->
            pending.context.expectedPos == placeContext.expectedPos
        }

    /**
     * A modified version of the minecraft interactBlock method, renamed to better suit its usage.
     *
     * @see net.minecraft.client.network.ClientPlayerInteractionManager.interactBlock
     */
    private fun SafeContext.placeBlock(placeContext: PlaceContext, request: PlaceRequest, hand: Hand): ActionResult {
        interaction.syncSelectedSlot()
        val hitResult = placeContext.result
        if (!world.worldBorder.contains(hitResult.blockPos)) return ActionResult.FAIL
        if (gamemode == GameMode.SPECTATOR) return ActionResult.PASS
        return interactBlockInternal(placeContext, request, request.build.placing, hand, hitResult)
    }

    /**
     * A modified version of the minecraft interactBlockInternal method.
     *
     * @see net.minecraft.client.network.ClientPlayerInteractionManager.interactBlockInternal
     */
    private fun SafeContext.interactBlockInternal(
        placeContext: PlaceContext,
        request: PlaceRequest,
        placeConfig: PlaceConfig,
        hand: Hand,
        hitResult: BlockHitResult
    ): ActionResult {
        val handNotEmpty = player.getStackInHand(hand).isEmpty.not()
        val cantInteract = player.shouldCancelInteraction() && handNotEmpty
        if (!cantInteract) {
            val blockState = blockState(hitResult.blockPos)
            if (!connection.hasFeature(blockState.block.requiredFeatures)) {
                return ActionResult.FAIL
            }

            val actionResult = blockState.onUse(world, player, hand, hitResult)
            if (actionResult.isAccepted) {
                return actionResult
            }
        }

        val itemStack = player.getStackInHand(hand)

        if (!itemStack.isEmpty && !isItemOnCooldown(itemStack.item)) {
            val itemUsageContext = ItemUsageContext(player, hand, hitResult)
            return if (gamemode.isCreative) {
                val i = itemStack.count
                useOnBlock(placeContext, request, hand, hitResult, placeConfig, itemStack, itemUsageContext)
                    .also {
                        itemStack.count = i
                    }
            } else
                useOnBlock(placeContext, request, hand, hitResult, placeConfig, itemStack, itemUsageContext)
        }
        return ActionResult.PASS
    }

    /**
     * A modified version of the minecraft useOnBlock method.
     *
     * @see net.minecraft.item.Item.useOnBlock
     */
    private fun SafeContext.useOnBlock(
        placeContext: PlaceContext,
        request: PlaceRequest,
        hand: Hand,
        hitResult: BlockHitResult,
        placeConfig: PlaceConfig,
        itemStack: ItemStack,
        context: ItemUsageContext
    ): ActionResult {
        val cachedBlockPosition = CachedBlockPosition(world, context.blockPos, false)

        val cantModifyWorld = !player.abilities.allowModifyWorld
        val cantPlaceOn = !itemStack.canPlaceOn(context.world.registryManager.get(RegistryKeys.BLOCK), cachedBlockPosition)
        if (cantModifyWorld && cantPlaceOn) return ActionResult.PASS

        val item = (itemStack.item as? BlockItem) ?: return ActionResult.PASS

        return place(placeContext, request, hand, hitResult, placeConfig, item, ItemPlacementContext(context))
    }

    /**
     * A modified version of the minecraft place method.
     *
     * @see net.minecraft.item.BlockItem.place
     */
    private fun SafeContext.place(
        placeContext: PlaceContext,
        request: PlaceRequest,
        hand: Hand,
        hitResult: BlockHitResult,
        placeConfig: PlaceConfig,
        item: BlockItem,
        context: ItemPlacementContext
    ): ActionResult {
        if (!item.block.isEnabled(world.enabledFeatures)) return ActionResult.FAIL
        if (!context.canPlace()) return ActionResult.FAIL

        val itemPlacementContext = item.getPlacementContext(context) ?: return ActionResult.FAIL
        val blockState = item.getPlacementState(itemPlacementContext) ?: return ActionResult.FAIL

        val stackInHand = player.getStackInHand(hand)
        val stackCountPre = stackInHand.count
        if (placeConfig.placeConfirmationMode != PlaceConfig.PlaceConfirmationMode.None) {
            addPendingPlace(
                PlaceInfo(placeContext, request.onPlace, request.pendingInteractions, placeConfig)
            )
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

            if (!stackInHand.isEmpty && (stackInHand.count != stackCountPre || interaction.hasCreativeInventory())) {
                mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand)
            }
        }

        if (placeConfig.placeConfirmationMode == PlaceConfig.PlaceConfirmationMode.AwaitThenPlace)
            return ActionResult.success(world.isClient)

        // TODO: Implement restriction checks (e.g., world height) to prevent unnecessary server requests when the
        //  "AwaitThenPlace" confirmation setting is enabled, as the block state setting methods that validate these
        //  rules are not called.
        if (!item.place(itemPlacementContext, blockState)) return ActionResult.FAIL

        val blockPos = itemPlacementContext.blockPos
        val itemStack = itemPlacementContext.stack
        var hitState = world.getBlockState(blockPos)
        if (hitState.isOf(blockState.block)) {
            hitState = item.placeFromNbt(blockPos, world, itemStack, hitState)
            item.postPlacement(blockPos, world, player, itemStack, hitState)
            hitState.block.onPlaced(world, blockPos, hitState, player, itemStack)
        }

        if (placeConfig.sounds) placeSound(item, hitState, blockPos)
        if (!player.abilities.creativeMode) itemStack.decrement(1)

        if (placeConfig.placeConfirmationMode == PlaceConfig.PlaceConfirmationMode.None) {
            request.onPlace()
        }

        return ActionResult.success(world.isClient)
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
    fun SafeContext.placeSound(item: BlockItem, state: BlockState, pos: BlockPos) {
        val blockSoundGroup = state.soundGroup
        world.playSound(
            player,
            pos,
            item.getPlaceSound(state),
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
