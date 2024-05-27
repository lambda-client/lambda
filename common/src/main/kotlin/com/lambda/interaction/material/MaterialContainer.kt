package com.lambda.interaction.material

import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ShulkerBoxContainer
import com.lambda.interaction.material.transfer.TransferResult
import com.lambda.task.Task
import com.lambda.task.Task.Companion.emptyTask
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

    fun update(stacks: List<ItemStack>) {
        this.stacks = stacks
    }

    /**
     * Brings the player into a withdrawal/deposit state. E.g.: move to a chest etc.
     */
    @Task.Ta5kBuilder
    open fun prepare(): Task<*> = emptyTask("EmptyPrepare")

    /**
     * Withdraws items from the container to the player's inventory.
     */
    @Task.Ta5kBuilder
    abstract fun withdraw(selection: StackSelection): Task<*>

    /**
     * Deposits items from the player's inventory into the container.
     */
    @Task.Ta5kBuilder
    abstract fun deposit(selection: StackSelection): Task<*>

    open fun filter(selection: StackSelection) =
        selection.filterStacks(stacks)

    open fun filter(selection: (ItemStack) -> Boolean) =
        filter(selection.select())

    open fun available(selection: StackSelection) =
        filter(selection).count

    open fun spaceLeft(selection: StackSelection) =
        filter(selection).spaceLeft + stacks.empty * selection.stackSize

    fun StackSelection.transfer(destination: MaterialContainer): TransferResult {
        val amount = available(this)
        if (amount < count) {
            return TransferResult.MissingItems(amount - count)
        }

        val space = destination.spaceLeft(this)
        if (space == 0) {
            return TransferResult.NoSpace
        }

        val transferAmount = minOf(amount, space)
        selector = { true }
        count = transferAmount

        return TransferResult.Success(
            prepare().onSuccess { prep, _ ->
                withdraw(this@transfer).onSuccess { with, _ ->
                    destination.prepare().onSuccess { dest, _ ->
                        destination.deposit(this@transfer).start(dest)
                    }.start(with)
                }.start(prep)
            }
        )
    }

    fun List<ItemStack>.doShulkerCheck() =
        filter {
            it.item in ItemUtils.shulkerBoxes
        }.map { stack ->
            ShulkerBoxContainer(
                stack.shulkerBoxContents,
                containedIn = this@MaterialContainer,
                shulkerStack = stack,
            )
        }.toSet()

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