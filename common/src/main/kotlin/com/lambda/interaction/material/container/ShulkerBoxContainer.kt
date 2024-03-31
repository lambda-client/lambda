package com.lambda.interaction.material.container

import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.buildChain
import com.lambda.task.tasks.BuildStructure.Companion.breakAndCollectBlock
import com.lambda.task.tasks.InventoryTask.Companion.withdraw
import com.lambda.task.tasks.OpenContainer.Companion.openContainer
import com.lambda.task.tasks.PlaceContainer.Companion.placeContainer
import com.lambda.util.Communication.info
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ShulkerBoxScreenHandler
import net.minecraft.util.math.BlockPos

data class ShulkerBoxContainer(
    override var stacks: List<ItemStack>,
    val containedIn: MaterialContainer,
    val shulkerStack: ItemStack,
) : MaterialContainer(Rank.SHULKER_BOX) {
    private var openScreen: ShulkerBoxScreenHandler? = null
    private var placePosition: BlockPos? = null

    override fun prepare() = buildChain {
        placeContainer(shulkerStack).onSuccess { placePos ->
            placePosition = placePos
            openContainer<ShulkerBoxScreenHandler>(placePos).onSuccess { screen ->
                openScreen = screen
            }
        }
    }

    override fun withdraw(selection: StackSelection) = buildChain {
        openScreen?.let { screen ->
            info("Withdrawing $selection from ${shulkerStack.name.string}")
            withdraw(screen, selection)
        }
        placePosition?.let {
            breakAndCollectBlock(it)
        }
    }

    override fun deposit(selection: StackSelection) = buildChain {
        openScreen?.let { screen ->
            info("Depositing $selection to shulker box ${shulkerStack.name.string}")
            withdraw(screen, selection)
        }
        placePosition?.let {
            breakAndCollectBlock(it)
        }
    }
}