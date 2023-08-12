package com.lambda.client.module.modules.misc

import com.lambda.client.activity.activities.construction.Graffiti
import com.lambda.client.activity.activities.construction.SurroundWithObsidian
import com.lambda.client.activity.activities.construction.core.PlaceBlock
import com.lambda.client.activity.activities.interaction.crafting.ReachXPLevel
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.storage.*
import com.lambda.client.activity.activities.storage.types.*
import com.lambda.client.activity.activities.travel.CollectDrops
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
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.util.EnumFacing

object TestActivityManager : Module(
    name = "TestActivityManager",
    description = "a",
    category = Category.MISC
) {
    private val ctiectie by setting("Auto Obby", false, consumer = { _, _->
        addSubActivities(
            BreakDownEnderChests()
        )
        false
    })

    private val tie by setting("Store one Obby", false, consumer = { _, _->
        addSubActivities(
            ShulkerTransaction(ContainerAction.PUSH, StackSelection().apply {
                selection = isBlock(Blocks.OBSIDIAN)
            })
        )
        false
    })

    private val eictie by setting("Stash request", false, consumer = { _, _->
        WorldEater.stashes.firstOrNull()?.let {
            addSubActivities(
                StashTransaction(setOf(Triple(it, ContainerAction.PULL, StackSelection().apply {
                    selection = isBlock(Blocks.OBSIDIAN)
                })))
            )
        }
        false
    })

    private val ectitie by setting("Stash request multi", false, consumer = { _, _->
        WorldEater.stashes.firstOrNull()?.let {
            addSubActivities(
                StashTransaction(setOf(Triple(it, ContainerAction.PULL, StackSelection(0).apply {
                    selection = isBlock(Blocks.OBSIDIAN)
                })))
            )
        }
        false
    })

    private val ectiectictietie by setting("Stash request multi more", false, consumer = { _, _->
        WorldEater.stashes.firstOrNull()?.let {
            addSubActivities(
                StashTransaction(setOf(Triple(it, ContainerAction.PULL, StackSelection().apply {
                    selection = isBlock(Blocks.OBSIDIAN)
                }))),
                StashTransaction(setOf(Triple(it, ContainerAction.PUSH, StackSelection().apply {
                    selection = isBlock(Blocks.OBSIDIAN)
                })))
            )
        }
        false
    })

    private val etit by setting("Acquire Obby", false, consumer = { _, _->
        addSubActivities(
            AcquireItemInActiveHand(StackSelection().apply {
                selection = isBlock(Blocks.OBSIDIAN)
            })
        )
        false
    })

    private val ectiectietit by setting("Graffiti", false, consumer = { _, _->
        addSubActivities(
            Graffiti(100)
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
        addSubActivities(CollectDrops(Blocks.OBSIDIAN.item))
        false
    })

    private val ctiectiectiectieciec by setting("Pickup Dropped", false, consumer = { _, _->
        runSafe {
            val stack = player.heldItemMainhand.copy()

            addSubActivities(CollectDrops(stack.item, stack))
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
