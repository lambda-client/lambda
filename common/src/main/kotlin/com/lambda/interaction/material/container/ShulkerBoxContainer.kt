package com.lambda.interaction.material.container

import com.lambda.context.SafeContext
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.breakAndCollectBlock
import com.lambda.task.tasks.InventoryTask.Companion.deposit
import com.lambda.task.tasks.InventoryTask.Companion.withdraw
import com.lambda.task.tasks.OpenContainer.Companion.openContainer
import com.lambda.task.tasks.PlaceContainer.Companion.placeContainer
import net.minecraft.item.ItemStack

data class ShulkerBoxContainer(
    override var stacks: List<ItemStack>,
    val containedIn: MaterialContainer,
    val shulkerStack: ItemStack,
) : MaterialContainer(Rank.SHULKER_BOX) {
    override val name = "${shulkerStack.name.string} in slot $slotInContainer in ${containedIn.name}"

    private val slotInContainer: Int get() = containedIn.stacks.indexOf(shulkerStack)

    class Withdraw(
        private val selection: StackSelection,
        private val shulkerStack: ItemStack
    ) : Task<Unit>() {
        override fun SafeContext.onStart() {
            placeContainer(shulkerStack).thenRun(this@Withdraw) { _, placePos ->
                openContainer(placePos).thenRun(this@Withdraw) { _, screen ->
                    withdraw(screen, selection).thenRun(this@Withdraw) { _, _ ->
                        breakAndCollectBlock(placePos).onSuccess { _, _ ->
                            success(Unit)
                        }
                    }
                }
            }.start(this@Withdraw)
        }
    }

    override fun withdraw(selection: StackSelection) = Withdraw(selection, shulkerStack)

    class Deposit(
        private val selection: StackSelection,
        private val shulkerStack: ItemStack
    ) : Task<Unit>() {
        override fun SafeContext.onStart() {
            placeContainer(shulkerStack).thenRun(this@Deposit) { _, placePos ->
                openContainer(placePos).thenRun(this@Deposit) { _, screen ->
                    deposit(screen, selection).thenRun(this@Deposit) { _, _ ->
                        breakAndCollectBlock(placePos).onSuccess { _, _ ->
                            success(Unit)
                        }
                    }
                }
            }.start(this@Deposit)
        }
    }

    override fun deposit(selection: StackSelection) = Deposit(selection, shulkerStack)
}