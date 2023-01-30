package com.lambda.client.module.modules.misc

import com.lambda.client.LambdaMod
import com.lambda.client.activity.activities.example.ProbablyFailing
import com.lambda.client.activity.activities.example.SayAnnoyingly
import com.lambda.client.activity.activities.highlevel.BreakDownEnderChests
import com.lambda.client.activity.activities.highlevel.BuildStructure
import com.lambda.client.activity.activities.highlevel.ReachXPLevel
import com.lambda.client.activity.activities.highlevel.SurroundWithObsidian
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.interaction.UseThrowableOnEntity
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.inventory.DumpInventory
import com.lambda.client.activity.activities.storage.ExtractItemFromShulkerBox
import com.lambda.client.activity.activities.storage.PlaceContainer
import com.lambda.client.activity.activities.storage.StoreItemToShulkerBox
import com.lambda.client.activity.activities.travel.PickUpDrops
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
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.schematic.LambdaSchematicaHelper
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.math.VectorUtils.multiply
import com.lambda.client.util.threads.runSafe
import net.minecraft.block.BlockHorizontal
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
    private val a by setting("Get any Dia Pickaxe", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            AcquireItemInActiveHand(Items.DIAMOND_PICKAXE)
        )
        false
    })

    private val tie by setting("Store Obby", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            StoreItemToShulkerBox(Blocks.OBSIDIAN.item)
        )
        false
    })

    private val ctiectie by setting("Auto Obby", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            BreakDownEnderChests()
        )
        false
    })

    private val etit by setting("Extract Obby", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            ExtractItemFromShulkerBox(Blocks.OBSIDIAN.item)
        )
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

    private val b by setting("Get Dia Pickaxe with silktouch", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            AcquireItemInActiveHand(
                Items.DIAMOND_PICKAXE,
                predicateItem = {
                    EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, it) == 1
                },
                predicateSlot = {
                    val item = it.item
                    item != Items.DIAMOND_PICKAXE && item.block !is BlockShulkerBox
                }
            )
        )
        false
    })

    private val dumpInventoryActivity by setting("Dump Inventory", false, consumer = { _, _->
        ActivityManager.addSubActivities(DumpInventory())
        false
    })

    private val po by setting("Pickup Obby", false, consumer = { _, _->
        ActivityManager.addSubActivities(PickUpDrops(Blocks.OBSIDIAN.item))
        false
    })

    private val ti by setting("count", false, consumer = { _, _->
        runSafe {
            LambdaMod.LOG.info(player.inventorySlots.countEmpty())
        }

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

    private val schematicBuild by setting("Build Schematic", false, consumer = { _, _ ->
        schematicBuildActivity(false)
        false
    })

    private val schematicBuildSkipAir by setting("Build Schematic (Skip Air)", false, consumer = { _, _ ->
        schematicBuildActivity(true)
        false
    })

    private fun schematicBuildActivity(skipAir: Boolean) {
        if (LambdaSchematicaHelper.isSchematicaPresent) {
            LambdaSchematicaHelper.loadedSchematic?.let { schematic ->
                val structure = mutableMapOf<BlockPos, IBlockState>()
                for (y in schematic.getOrigin().y..schematic.getOrigin().y + schematic.heightY()) {
                    for (x in schematic.getOrigin().x..schematic.getOrigin().x + schematic.widthX()) {
                        for (z in schematic.getOrigin().z..schematic.getOrigin().z + schematic.lengthZ()) {
                            val blockPos = BlockPos(x, y, z)
                            if (!schematic.inSchematic(blockPos)) continue // probably not necessary to check
                            val desiredBlockState = schematic.desiredState(blockPos)
                            if (desiredBlockState.block == Blocks.AIR && skipAir) continue
                            structure[blockPos] = desiredBlockState
                        }
                    }
                }
                // potentially beeg log
                LambdaMod.LOG.info("Building structure $structure")
                ActivityManager.addSubActivities(
                    BuildStructure(structure)
                )
            } ?: run {
                MessageSendHelper.sendChatMessage("No schematic is loaded")
            }
        } else {
            MessageSendHelper.sendChatMessage("Schematica is not present")
        }
    }

    private val ctirsgn by setting("Throw", false, consumer = { _, _->
        runSafe {
            ActivityManager.addSubActivities(
                UseThrowableOnEntity(player, amount = 64)
            )
        }
        false
    })

    private val sayHelloWorld by setting("Hello World", false, consumer = { _, _->
        ActivityManager.addSubActivities(SayAnnoyingly("Hello World"))
        false
    })

    private val fail by setting("maybe fail", false, consumer = { _, _->
        ActivityManager.addSubActivities(ProbablyFailing())
        false
    })

    private val pullll by setting("Extract", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            ExtractItemFromShulkerBox(Blocks.OBSIDIAN.item, amount = 1)
        )
        false
    })

    private val pusshhh by setting("Store", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            StoreItemToShulkerBox(Blocks.OBSIDIAN.item, amount = 1)
        )
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
}
