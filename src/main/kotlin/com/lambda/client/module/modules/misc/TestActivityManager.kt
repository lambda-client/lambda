package com.lambda.client.module.modules.misc

import com.lambda.client.activity.activities.construction.SurroundWithObsidian
import com.lambda.client.activity.activities.construction.core.PlaceBlock
import com.lambda.client.activity.activities.interaction.crafting.ReachXPLevel
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.storage.BreakDownEnderChests
import com.lambda.client.activity.activities.storage.StoreItemToShulkerBox
import com.lambda.client.activity.activities.travel.PickUpDrops
import com.lambda.client.activity.types.RenderAABBActivity.Companion.checkAABBRender
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.MovementUtils.centerPlayer
import com.lambda.client.util.items.item
import com.lambda.client.util.math.VectorUtils.multiply
import com.lambda.client.util.threads.runSafe
import net.minecraft.block.BlockDirectional
import net.minecraft.block.BlockHorizontal
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing

object TestActivityManager : Module(
    name = "TestActivityManager",
    description = "",
    category = Category.MISC
) {
    private val ctiectie by setting("Auto Obby", false, consumer = { _, _->
        addSubActivities(
            BreakDownEnderChests()
        )
        false
    })

    private val tie by setting("Store Obby", false, consumer = { _, _->
        addSubActivities(
            StoreItemToShulkerBox(Blocks.OBSIDIAN.item)
        )
        false
    })

    private val etit by setting("Acquire Obby", false, consumer = { _, _->
        addSubActivities(
            AcquireItemInActiveHand(Blocks.OBSIDIAN.item)
        )
        false
    })

    private val eticiettie by setting("Direction shenanigans", false, consumer = { _, _->
        runSafe {
            var currentDirection = player.horizontalFacing
            var position = player.flooredPosition.add(currentDirection.directionVec.multiply(2))

            repeat(4) {
                val targetState = Blocks.MAGENTA_GLAZED_TERRACOTTA.defaultState.withProperty(BlockHorizontal.FACING, currentDirection)

                addSubActivities(
                    PlaceBlock(position, targetState)
                )

                currentDirection = currentDirection.rotateY()
                position = position.add(currentDirection.directionVec)
            }
        }

        false
    })

    private val ctiectictiectie by setting("Button", false, consumer = { _, _->
        runSafe {
            val currentDirection = player.horizontalFacing

//            val targetState = Blocks.QUARTZ_BLOCK.defaultState
//                .withProperty(BlockQuartz.VARIANT, BlockQuartz.EnumType.LINES_X)

            val targetState = Blocks.WOODEN_BUTTON.defaultState
                .withProperty(BlockDirectional.FACING, EnumFacing.NORTH)

            addSubActivities(PlaceBlock(
                player.flooredPosition.add(currentDirection.directionVec.multiply(2)),
                targetState
            ))
        }

        false
    })

    private val po by setting("Pickup Obby", false, consumer = { _, _->
        addSubActivities(PickUpDrops(Blocks.OBSIDIAN.item))
        false
    })

    private val ctiectiectiectieciec by setting("Pickup Dropped", false, consumer = { _, _->
        runSafe {
            val stack = player.heldItemMainhand.copy()

            addSubActivities(PickUpDrops(stack.item, stack))
        }

        false
    })

    private val tiectie by setting("Surround", false, consumer = { _, _->
        runSafe {
            player.centerPlayer()
            addSubActivities(
                SurroundWithObsidian(player.flooredPosition)
            )
        }
        false
    })

    val raiseXPLevel by setting("Reach level 30", false, consumer = { _, _->
        addSubActivities(ReachXPLevel(30))
        false
    })

    private val reset by setting("Reset", false, consumer = { _, _->
        ActivityManager.reset()
        false
    })

    init {
        onToggle {
            runSafe {
                with(ActivityManager.getCurrentActivity()) {
                    updateActivity()
                    checkAABBRender()
                }
            }
        }
    }
}
