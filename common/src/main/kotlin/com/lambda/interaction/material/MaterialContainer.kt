package com.lambda.interaction.material

import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ShulkerBoxContainer
import com.lambda.interaction.material.transfer.TransferResult
import com.lambda.task.Task
import com.lambda.util.item.ItemStackUtils.count
import com.lambda.util.item.ItemStackUtils.empty
import com.lambda.util.item.ItemStackUtils.shulkerBoxContents
import com.lambda.util.item.ItemStackUtils.spaceLeft
import com.lambda.util.item.ItemUtils
import net.minecraft.item.ItemStack

// ToDo: Make jsonable to persistently store them
abstract class MaterialContainer(
    private val rank: Rank
) : Comparable<MaterialContainer> {
    abstract var stacks: List<ItemStack>

    val shulkerContainer get() =
        stacks.filter {
            it.item in ItemUtils.shulkerBoxes
        }.map { stack ->
            ShulkerBoxContainer(
                stack.shulkerBoxContents,
                containedIn = this@MaterialContainer,
                shulkerStack = stack,
            )
        }.toSet()

    fun update(stacks: List<ItemStack>) {
        this.stacks = stacks
    }

    /**
     * Brings the player into a withdrawal/deposit state. E.g.: move to a chest etc.
     */
    open fun prepare(): Task<*>? = null

    /**
     * Withdraws items from the container to the player's inventory.
     */
    abstract fun withdraw(selection: StackSelection): Task<*>

    @Task.Ta5kBuilder
    fun doWithdrawal(selection: StackSelection) =
        prepare()?.let { prep ->
            prep.onSuccess { _, _ ->
                withdraw(selection).start(prep)
            }
        } ?: withdraw(selection)

    /**
     * Deposits items from the player's inventory into the container.
     */
    abstract fun deposit(selection: StackSelection): Task<*>

    @Task.Ta5kBuilder
    fun doDeposit(selection: StackSelection) =
        prepare()?.let { prep ->
            prep.onSuccess { _, _ ->
                deposit(selection).start(prep)
            }
        } ?: deposit(selection)

    open fun StackSelection.matchingStacks() =
        filterStacks(stacks)

    open fun matchingStacks(selection: (ItemStack) -> Boolean) =
        selection.select().matchingStacks()

    open fun available(selection: StackSelection) =
        selection.matchingStacks().count

    open fun spaceLeft(selection: StackSelection) =
        selection.matchingStacks().spaceLeft + stacks.empty * selection.stackSize

    fun transfer(selection: StackSelection, destination: MaterialContainer): TransferResult {
        val amount = available(selection)
        if (amount < selection.count) {
            return TransferResult.MissingItems(amount - selection.count)
        }

//        val space = destination.spaceLeft(selection)
//        if (space == 0) {
//            return TransferResult.NoSpace
//        }

//        val transferAmount = minOf(amount, space)
//        selection.selector = { true }
//        selection.count = transferAmount

        return TransferResult.Success(
            doWithdrawal(selection).onSuccess { withdraw, _ ->
                destination.doDeposit(selection).start(withdraw)
            }
        )
    }

    enum class Rank {
        CREATIVE,
        MAIN_HAND,
        OFF_HAND,
        HOTBAR,
        INVENTORY,
        SHULKER_BOX,
        ENDER_CHEST,
        CHEST,
        STASH
    }

    override fun compareTo(other: MaterialContainer) =
        compareBy<MaterialContainer> {
            it.rank
        }.compare(this, other)
}