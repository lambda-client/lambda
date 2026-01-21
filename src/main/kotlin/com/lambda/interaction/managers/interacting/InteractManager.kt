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

package com.lambda.interaction.managers.interacting

import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.construction.simulation.context.InteractContext
import com.lambda.interaction.managers.Manager
import com.lambda.interaction.managers.ManagerUtils.isPosBlocked
import com.lambda.interaction.managers.PositionBlocking
import com.lambda.interaction.managers.breaking.BreakManager
import com.lambda.interaction.managers.interacting.InteractManager.activeRequest
import com.lambda.interaction.managers.interacting.InteractManager.maxPlacementsThisTick
import com.lambda.interaction.managers.interacting.InteractManager.populateFrom
import com.lambda.interaction.managers.interacting.InteractManager.potentialPlacements
import com.lambda.interaction.managers.interacting.InteractManager.processRequest
import com.lambda.interaction.managers.interacting.InteractedBlockHandler.pendingActions
import com.lambda.interaction.managers.interacting.InteractedBlockHandler.setPendingConfigs
import com.lambda.interaction.managers.interacting.InteractedBlockHandler.startPending
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.item.ItemUtils.blockItem
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
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.sound.SoundCategory
import net.minecraft.util.ActionResult
import net.minecraft.util.ActionResult.PassToDefaultBlockAction
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.GameMode

object InteractManager : Manager<InteractRequest>(
    0,
    onOpen = { activeRequest?.let { it.runSafeAutomated { processRequest(it) } } }
), PositionBlocking {
    private var activeRequest: InteractRequest? = null
    private var potentialPlacements = mutableListOf<InteractContext>()

	private var interactCooldown = 0
    private var placementsThisTick = 0
    private var maxPlacementsThisTick = 0

    private var shouldSneak = false
    private val ClientPlayerEntity.validSneak get() = isSneaking == shouldSneak

    override val blockedPositions
        get() = pendingActions.map { it.context.blockPos }

    override fun load(): String {
        super.load()

        listen<TickEvent.Post>({ Int.MIN_VALUE }) {
            activeRequest = null
            placementsThisTick = 0
            potentialPlacements.clear()
	        if (interactCooldown > 0) {
				interactCooldown--
			}
        }

        listen<MovementEvent.InputUpdate>({ Int.MIN_VALUE }) {
            if (shouldSneak) {
                shouldSneak = false
                it.input.sneaking = true
            }
        }

	    listenUnsafe<ConnectionEvent.Connect.Pre>({ Int.MIN_VALUE }) {
		    interactCooldown = 0
	    }

        return "Loaded Place Manager"
    }

    /**
     * Accepts, and processes the request, as long as the current [activeRequest] is null, and the [BreakManager] has not
     * been active this tick. If nowOrNothing is true, the request is cleared after the first process.
     *
     * @see processRequest
     */
    override fun AutomatedSafeContext.handleRequest(request: InteractRequest) {
        if (!request.buildConfig.interactBlocks || activeRequest != null || request.contexts.isEmpty()) return
	    if (BreakManager.activeThisTick) return

        activeRequest = request
        processRequest(request)
        if (request.nowOrNothing) {
            activeRequest = null
            potentialPlacements = mutableListOf()
        }
        if (placementsThisTick > 0) activeThisTick = true
    }

    /**
     * Returns immediately if [BreakManager] or [InteractManager] have been active this tick.
     * Otherwise, for fresh requests, [populateFrom] is called to fill the [potentialPlacements] collection.
     * It then attempts to perform as many placements as possible from the [potentialPlacements] collection within
     * the [maxPlacementsThisTick] limit.
     *
     * @see populateFrom
     * @see interactBlock
     */
    fun AutomatedSafeContext.processRequest(request: InteractRequest)  {
        if (request.fresh) populateFrom(request)

        val iterator = potentialPlacements.iterator()
        while (iterator.hasNext()) {
			if (interactCooldown > 0) break
            if (placementsThisTick + 1 > maxPlacementsThisTick) break
            val ctx = iterator.next()

            shouldSneak = ctx.sneak
            if (!ctx.requestDependencies(request)) return
            if (!player.validSneak) return
            if (tickStage !in interactConfig.tickStageMask) return

            val actionResult = if (ctx.preProcessingInfo.placing) placeBlock(ctx, request, Hand.MAIN_HAND)
	        else interaction.interactBlock(player, Hand.MAIN_HAND, ctx.hitResult)
            if (actionResult.isAccepted && interactConfig.swing) {
                swingHand(interactConfig.swingType, Hand.MAIN_HAND)

                val stackInHand = player.getStackInHand(Hand.MAIN_HAND)
                val stackCountPre = stackInHand.count
                if (!stackInHand.isEmpty && (stackInHand.count != stackCountPre || player.isInCreativeMode)) {
                    mc.gameRenderer.firstPersonRenderer.resetEquipProgress(Hand.MAIN_HAND)
                }
            }
	        interactCooldown = ctx.interactConfig.interactDelay + 1
            placementsThisTick++
            iterator.remove()
        }
        if (potentialPlacements.isEmpty()) {
            if (activeRequest != null) activeRequest = null
        }
    }

    /**
     * Filters the [request]'s [InteractContext]s, placing them into the [potentialPlacements] collection, and
     * setting other configurations.
     *
     * @see isPosBlocked
     */
    private fun Automated.populateFrom(request: InteractRequest) {
        setPendingConfigs()
        potentialPlacements = request.contexts
            .distinctBy { it.blockPos }
            .filter { !isPosBlocked(it.blockPos) }
            .take(buildConfig.maxPendingActions - request.pendingInteractions.size.coerceAtLeast(0))
            .toMutableList()

        maxPlacementsThisTick = interactConfig.interactionsPerTick
    }

    /**
     * A modified version of the minecraft interactBlock method,
     * renamed to better suit its usage.
     *
     * @see net.minecraft.client.network.ClientPlayerInteractionManager.interactBlock
     */
    private fun AutomatedSafeContext.placeBlock(interactContext: InteractContext, request: InteractRequest, hand: Hand): ActionResult {
        interaction.syncSelectedSlot()
        val hitResult = interactContext.hitResult
        if (!world.worldBorder.contains(hitResult.blockPos)) return ActionResult.FAIL
        if (gamemode == GameMode.SPECTATOR) return ActionResult.PASS
        return interactBlockInternal(interactContext, request, hand, hitResult)
    }

    /**
     * A modified version of the minecraft interactBlockInternal method.
     *
     * @see net.minecraft.client.network.ClientPlayerInteractionManager.interactBlockInternal
     */
    private fun AutomatedSafeContext.interactBlockInternal(
	    interactContext: InteractContext,
	    request: InteractRequest,
	    hand: Hand,
	    hitResult: BlockHitResult
    ): ActionResult {
        val handNotEmpty = player.getStackInHand(hand).isEmpty.not()
        val cantInteract = player.shouldCancelInteraction() && handNotEmpty
        if (!cantInteract) {
            val blockState = blockState(hitResult.blockPos)
            if (!connection.hasFeature(blockState.block.requiredFeatures)) return ActionResult.FAIL

	        val actionResult = blockState.onUseWithItem(player.getStackInHand(hand), world, player, hand, hitResult)
	        if (actionResult.isAccepted) return actionResult

	        if (actionResult is PassToDefaultBlockAction && hand == Hand.MAIN_HAND) {
		        val actionResult2 = blockState.onUse(world, player, hitResult)
		        if (actionResult2.isAccepted) return actionResult2
	        }
        }

        val stack = player.mainHandStack

        if (!stack.isEmpty && !isItemOnCooldown(stack)) {
            val itemUsageContext = ItemUsageContext(player, hand, hitResult)
            return if (gamemode.isCreative) {
                val i = stack.count
                useOnBlock(interactContext, request, hand, hitResult, stack, itemUsageContext)
                    .also { stack.count = i }
            } else useOnBlock(interactContext, request, hand, hitResult, stack, itemUsageContext)
        }
        return ActionResult.PASS
    }

    /**
     * A modified version of the minecraft useOnBlock method.
     *
     * @see net.minecraft.item.Item.useOnBlock
     */
    private fun AutomatedSafeContext.useOnBlock(
	    interactContext: InteractContext,
	    request: InteractRequest,
	    hand: Hand,
	    hitResult: BlockHitResult,
	    itemStack: ItemStack,
	    context: ItemUsageContext
    ): ActionResult {
	    val blockPos = context.blockPos
	    return if (!player.abilities.allowModifyWorld &&
		    !itemStack.canPlaceOn(CachedBlockPosition(world, blockPos, false))
			) {
			ActionResult.PASS
	    } else {
		    val item = itemStack.blockItem ?: return ActionResult.FAIL
		    place(interactContext, request, hand, hitResult, item, ItemPlacementContext(context))
	    }
    }

    /**
     * A modified version of the minecraft place method.
     *
     * @see net.minecraft.item.BlockItem.place
     */
    private fun AutomatedSafeContext.place(
	    interactContext: InteractContext,
	    request: InteractRequest,
	    hand: Hand,
	    hitResult: BlockHitResult,
	    item: BlockItem,
	    context: ItemPlacementContext
    ): ActionResult {
        if (!item.block.isEnabled(world.enabledFeatures)) return ActionResult.FAIL
        if (!context.canPlace()) return ActionResult.FAIL

        val itemPlacementContext = item.getPlacementContext(context) ?: return ActionResult.FAIL
        val blockState = item.getPlacementState(itemPlacementContext) ?: return ActionResult.FAIL

        if (interactConfig.airPlace == InteractConfig.AirPlaceMode.Grim) {
            val placeHand = if (hand == Hand.MAIN_HAND) Hand.OFF_HAND else Hand.MAIN_HAND
            val inventoryRequest = inventoryRequest {
                swapHands()
                action { sendInteractPacket(placeHand, hitResult) }
                swapHands()
            }.submit(queueIfMismatchedStage = false)
            if (!inventoryRequest.done) return ActionResult.FAIL
        } else {
            sendInteractPacket(hand, hitResult)
        }

        if (interactConfig.interactConfirmationMode != InteractConfig.InteractConfirmationMode.None) {
            InteractInfo(interactContext, request.pendingInteractions, request.onPlace, interactConfig).startPending()
        }

        val itemStack = itemPlacementContext.stack
        itemStack.decrementUnlessCreative(1, player)

        if (interactConfig.interactConfirmationMode == InteractConfig.InteractConfirmationMode.AwaitThenPlace)
            return ActionResult.SUCCESS

        // TODO: Implement restriction checks (e.g., world height) to prevent unnecessary server requests when the
        //  "AwaitThenPlace" confirmation setting is enabled, as the block state setting methods that validate these
        //  rules are not called.
        if (!item.place(itemPlacementContext, blockState)) return ActionResult.FAIL

        val blockPos = itemPlacementContext.blockPos
        var state = world.getBlockState(blockPos)
        if (state.isOf(blockState.block)) {
            state = item.placeFromNbt(blockPos, world, itemStack, state)
            item.postPlacement(blockPos, world, player, itemStack, state)
            state.block.onPlaced(world, blockPos, state, player, itemStack)
        }

        if (interactConfig.sounds) placeSound(state, blockPos)

        if (interactConfig.interactConfirmationMode == InteractConfig.InteractConfirmationMode.None) {
            request.onPlace?.invoke(this, interactContext.blockPos)
        }

        return ActionResult.SUCCESS
    }

    /**
     * sends the block placement packet using the given [hand] and [hitResult].
     */
    private fun SafeContext.sendInteractPacket(hand: Hand, hitResult: BlockHitResult) =
        interaction.sendSequencedPacket(world) { sequence: Int ->
            PlayerInteractBlockC2SPacket(hand, hitResult, sequence)
        }

    /**
     * Plays the block placement sound at a given [pos].
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
}
