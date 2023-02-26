package com.lambda.client.module.modules.misc

import com.lambda.client.LambdaMod
import com.lambda.client.activity.activities.example.ProbablyFailing
import com.lambda.client.activity.activities.example.SayAnnoyingly
import com.lambda.client.activity.activities.highlevel.*
import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.interaction.Rotate
import com.lambda.client.activity.activities.interaction.UseThrowableOnEntity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.inventory.DumpInventory
import com.lambda.client.activity.activities.storage.ExtractItemFromShulkerBox
import com.lambda.client.activity.activities.storage.PlaceContainer
import com.lambda.client.activity.activities.storage.StoreItemToShulkerBox
import com.lambda.client.activity.activities.travel.PickUpDrops
import com.lambda.client.activity.activities.types.RenderAABBActivity.Companion.checkRender
import com.lambda.client.activity.activities.utils.Wait
import com.lambda.client.commons.extension.next
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.MovementUtils.centerPlayer
import com.lambda.client.util.items.block
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.items.item
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.schematic.LambdaSchematicaHelper
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.math.VectorUtils.multiply
import com.lambda.client.util.threads.runSafe
import net.minecraft.block.BlockDirectional
import net.minecraft.block.BlockHorizontal
import net.minecraft.block.BlockLog
import net.minecraft.block.BlockPistonBase
import net.minecraft.block.BlockPurpurSlab
import net.minecraft.block.BlockQuartz
import net.minecraft.block.BlockRotatedPillar
import net.minecraft.block.BlockShulkerBox
import net.minecraft.block.state.IBlockState
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Blocks
import net.minecraft.init.Enchantments
import net.minecraft.init.Items
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

object TestActivityManager : Module(
    name = "TestActivityManager",
    description = "",
    category = Category.MISC
) {
    private val ctiectie by setting("Auto Obby", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            BreakDownEnderChests()
        )
        false
    })

    private val tie by setting("Store Obby", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            StoreItemToShulkerBox(Blocks.OBSIDIAN.item)
        )
        false
    })

    private val etit by setting("Extract Obby", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            ExtractItemFromShulkerBox(Blocks.OBSIDIAN.item)
        )
        false
    })

    private val ectiecti by setting("Break Place", false, consumer = { _, _->
        runSafe {
            val currentDirection = player.horizontalFacing
            val pos = player.flooredPosition.add(currentDirection.directionVec.multiply(2))

//            val targetState = Blocks.QUARTZ_BLOCK.defaultState
//                .withProperty(BlockQuartz.VARIANT, BlockQuartz.EnumType.LINES_X)

            val targetState = Blocks.ENDER_CHEST.defaultState

            ActivityManager.addSubActivities(
                PlaceBlock(pos, targetState),
                BreakBlock(pos),
                PlaceBlock(pos, targetState),
                BreakBlock(pos),
                PlaceBlock(pos, targetState),
                BreakBlock(pos),
                PlaceBlock(pos, targetState),
                BreakBlock(pos),
                PlaceBlock(pos, targetState),
                BreakBlock(pos),
                PlaceBlock(pos, targetState),
                BreakBlock(pos),
                PlaceBlock(pos, targetState),
                BreakBlock(pos),
                PlaceBlock(pos, targetState),
                BreakBlock(pos),
                PlaceBlock(pos, targetState),
                BreakBlock(pos),
                PlaceBlock(pos, targetState),
                BreakBlock(pos),
                PlaceBlock(pos, targetState),
                BreakBlock(pos),
                PlaceBlock(pos, targetState),
                BreakBlock(pos),
                PlaceBlock(pos, targetState),
                BreakBlock(pos),
                PlaceBlock(pos, targetState),
                BreakBlock(pos)
            )
        }
        false
    })

    private val ectiectctiectiei by setting("World Eater", false, consumer = { _, _->
        runSafe {
            val currentDirection = player.horizontalFacing
            val pos1 = player.flooredPosition.add(currentDirection.directionVec.multiply(2))
            val pos2 = player.flooredPosition.add(currentDirection.directionVec.multiply(10)).down(3)

            ActivityManager.addSubActivities(
                WorldEater(pos1, pos2)
            )
        }
        false
    })

    private val eticiettie by setting("Direction shenanigans", false, consumer = { _, _->
        runSafe {
            var currentDirection = player.horizontalFacing
            var position = player.flooredPosition.add(currentDirection.directionVec.multiply(2))

            repeat(4) {
                val targetState = Blocks.MAGENTA_GLAZED_TERRACOTTA.defaultState.withProperty(BlockHorizontal.FACING, currentDirection)

                ActivityManager.addSubActivities(
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

            ActivityManager.addSubActivities(PlaceBlock(
                player.flooredPosition.add(currentDirection.directionVec.multiply(2)),
                targetState
            ))
        }

        false
    })

    private val po by setting("Pickup Obby", false, consumer = { _, _->
        ActivityManager.addSubActivities(PickUpDrops(Blocks.OBSIDIAN.item))
        false
    })

    private val tiectie by setting("Surround me", false, consumer = { _, _->
        runSafe {
            player.centerPlayer()
            ActivityManager.addSubActivities(
                SurroundWithObsidian(player.flooredPosition)
            )
        }
        false
    })

    private val citectie by setting("Clear out", false, consumer = { _, _->
        runSafe {
            val structure = mutableMapOf<BlockPos, IBlockState>()

            VectorUtils.getBlockPosInSphere(player.positionVector, 3.0f).forEach {
                if (it.up() != player.flooredPosition) structure[it] = Blocks.AIR.defaultState
            }

            ActivityManager.addSubActivities(
                BuildStructure(structure)
            )
        }
        false
    })

    private val sayHelloWorld by setting("Hello World", false, consumer = { _, _->
        ActivityManager.addSubActivities(SayAnnoyingly("Hello World"))
        false
    })

    val raiseXPLevel by setting("Reach level 30", false, consumer = { _, _->
        ActivityManager.addSubActivities(ReachXPLevel(30))
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
                    checkRender()
                }
            }
        }
    }
}
