package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.mixin.extension.useEntityId
import com.lambda.client.util.items.clickSlotUnsynced
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.entity.passive.EntityDonkey
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.ClickType
import net.minecraft.item.ItemStack
import net.minecraft.network.Packet
import net.minecraft.network.play.client.*
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.EnumHandSide
import net.minecraft.util.math.Vec3d
import net.minecraft.util.text.TextComponentString
import net.minecraft.util.text.TextFormatting

object PacketCommand : ClientCommand(
    name = "packet",
    description = "Send any packet you want"
) {
    init {
        literal("Animation") {
            enum<EnumHand>("hand") { hand ->
                executeSafe {
                    deployPacket(
                        CPacketAnimation(hand.value),
                        "${hand.value}"
                    )
                }
            }
        }

        literal("ChatMessage") {
            string("message") { message ->
                executeSafe {
                    deployPacket(
                        CPacketChatMessage(message.value),
                        message.value
                    )
                }
            }
        }

        literal("ClickWindow") {
            int("windowId") { windowId ->
                int("slotId") { slotId ->
                    int("buttonId") { buttonId ->
                        enum<ClickType>("clickType") { clickType ->
                            executeSafe {
                                clickSlotUnsynced(windowId.value, slotId.value, buttonId.value, clickType.value)
                                MessageSendHelper.sendChatMessage("Sent ${TextFormatting.GRAY}CPacketClickWindow${TextFormatting.DARK_RED} > ${TextFormatting.GRAY}windowId: ${windowId.value}, slotId: ${slotId.value}, buttonId: ${buttonId.value}, clickType: ${clickType.value}")
                            }
                        }
                    }
                }
            }
        }

        literal("ClientSettings") {
            string("lang") { lang ->
                int("renderDistanceIn") { renderDistanceIn ->
                    enum<EntityPlayer.EnumChatVisibility>("chatVisibilityIn") { chatVisibilityIn ->
                        boolean("chatColorsIn") { chatColorsIn ->
                            int("modelPartsIn") { modelPartsIn ->
                                enum<EnumHandSide>("mainHandIn") { mainHandIn ->
                                    executeSafe {
                                        deployPacket(
                                            CPacketClientSettings(
                                                lang.value,
                                                renderDistanceIn.value,
                                                chatVisibilityIn.value,
                                                chatColorsIn.value,
                                                modelPartsIn.value,
                                                mainHandIn.value
                                            ),
                                            "${lang.value} ${renderDistanceIn.value} ${chatVisibilityIn.value} ${chatColorsIn.value} ${modelPartsIn.value} ${mainHandIn.value}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        literal("ClientStatus") {
            executeSafe {
                MessageSendHelper.sendChatMessage("Not yet implemented. Consider to make a pull request.")
            }
        }

        literal("CloseWindow") {
            int("windowId") { windowId ->
                executeSafe {
                    deployPacket(
                        CPacketCloseWindow(windowId.value),
                        "${windowId.value}"
                    )
                }
            }
        }

        literal("ConfirmTeleport") {
            int("teleportId") { teleportId ->
                executeSafe {
                    deployPacket(
                        CPacketConfirmTeleport(teleportId.value),
                        "${teleportId.value}"
                    )
                }
            }
        }

        literal("ConfirmTransaction") {
            int("windowId") { windowId ->
                short("uid") { uid ->
                    boolean("accepted") { accepted ->
                        executeSafe {
                            deployPacket(
                                CPacketConfirmTransaction(windowId.value, uid.value, accepted.value),
                                "${windowId.value} ${uid.value} ${accepted.value}"
                            )
                        }
                    }
                }
            }
        }

        literal("CreativeInventoryAction") {
            int("slotId") { slotId ->
                executeSafe {
                    // ToDo: Dynamic ItemStack
                    deployPacket(
                        CPacketCreativeInventoryAction(slotId.value, ItemStack.EMPTY),
                        "${slotId.value} ${ItemStack.EMPTY}"
                    )
                }
            }
        }

        literal("CustomPayload") {
            executeSafe {
                MessageSendHelper.sendChatMessage("Not yet implemented. Consider to make a pull request.")
            }
        }

        literal("EnchantItem") {
            int("windowId") { windowId ->
                int("button") { button ->
                    executeSafe {
                        deployPacket(
                            CPacketEnchantItem(windowId.value, button.value),
                            "${windowId.value} ${button.value}"
                        )
                    }
                }
            }
        }

        literal("EntityAction") {
            enum<CPacketEntityAction.Action>("action") { action ->
                int("auxData") { auxData ->
                    executeSafe {
                        deployPacket(
                            CPacketEntityAction(player, action.value, auxData.value),
                            "${player.entityId} ${action.value} ${auxData.value}"
                        )
                    }
                }
            }
        }

        literal("HeldItemChange") {
            int("slotId") { slotId ->
                executeSafe {
                    deployPacket(
                        CPacketHeldItemChange(slotId.value),
                        "${slotId.value}"
                    )
                }
            }
        }

        literal("Input") {
            float("strafeSpeed") { strafeSpeed ->
                float("forwardSpeed") { forwardSpeed ->
                    boolean("jumping") { jumping ->
                        boolean("sneaking") { sneaking ->
                            executeSafe {
                                deployPacket(
                                    CPacketInput(strafeSpeed.value, forwardSpeed.value, jumping.value, sneaking.value),
                                    "${strafeSpeed.value} ${forwardSpeed.value} ${jumping.value} ${sneaking.value}"
                                )
                            }
                        }
                    }
                }
            }
        }

        literal("KeepAlive") {
            long("key") { key ->
                executeSafe {
                    deployPacket(
                        CPacketKeepAlive(key.value),
                        "${key.value}"
                    )
                }
            }
        }

        literal("PlaceRecipe") {
            executeSafe {
                MessageSendHelper.sendChatMessage("Not yet implemented. Consider to make a pull request.")
            }
        }

        literal("PlayerPosition") {
            double("x") { x ->
                double("y") { y ->
                    double("z") { z ->
                        boolean("onGround") { onGround ->
                            executeSafe {
                                deployPacket(
                                    CPacketPlayer.Position(x.value, y.value, z.value, onGround.value),
                                    "${x.value} ${y.value} ${z.value} ${onGround.value}"
                                )
                            }
                        }
                    }
                }
            }
        }

        literal("PlayerPositionRotation") {
            double("x") { x ->
                double("y") { y ->
                    double("z") { z ->
                        float("yaw") { yaw ->
                            float("pitch") { pitch ->
                                boolean("onGround") { onGround ->
                                    executeSafe {
                                        deployPacket(
                                            CPacketPlayer.PositionRotation(x.value, y.value, z.value, yaw.value, pitch.value, onGround.value),
                                            "${x.value} ${y.value} ${z.value} ${yaw.value} ${pitch.value} ${onGround.value}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        literal("PlayerRotation") {
            float("yaw") { yaw ->
                float("pitch") { pitch ->
                    boolean("onGround") { onGround ->
                        executeSafe {
                            deployPacket(
                                CPacketPlayer.Rotation(yaw.value, pitch.value, onGround.value),
                                "${yaw.value} ${pitch.value} ${onGround.value}"
                            )
                        }
                    }
                }
            }
        }

        literal("PlayerAbilities") {
            executeSafe {
                MessageSendHelper.sendChatMessage("Not yet implemented. Consider to make a pull request.")
            }
        }

        literal("PlayerDigging") {
            enum<CPacketPlayerDigging.Action>("action") { action ->
                blockPos("position") { position ->
                    enum<EnumFacing>("facing") { facing ->
                        executeSafe {
                            deployPacket(
                                CPacketPlayerDigging(action.value, position.value, facing.value),
                                "${action.value} ${position.value} ${facing.value}"
                            )
                        }
                    }
                }
            }
        }

        literal("PlayerTryUseItem") {
            enum<EnumHand>("hand") { hand ->
                executeSafe {
                    deployPacket(
                        CPacketPlayerTryUseItem(hand.value),
                        "${hand.value}"
                    )
                }
            }
        }

        literal("PlayerTryUseItemOnBlock") {
            blockPos("position") { position ->
                enum<EnumFacing>("placedBlockDirection") { placedBlockDirection ->
                    enum<EnumHand>("hand") { hand ->
                        float("facingX") { facingX ->
                            float("facingY") { facingY ->
                                float("facingZ") { facingZ ->
                                    executeSafe {
                                        deployPacket(
                                            CPacketPlayerTryUseItemOnBlock(position.value, placedBlockDirection.value, hand.value, facingX.value, facingY.value, facingZ.value),
                                            "${position.value} ${placedBlockDirection.value} ${hand.value} ${facingX.value} ${facingY.value} ${facingZ.value}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        literal("RecipeInfo") {
            executeSafe {
                MessageSendHelper.sendChatMessage("Not yet implemented. Consider to make a pull request.")
            }
        }

        literal("ResourcePackStatus") {
            enum<CPacketResourcePackStatus.Action>("action") { action ->
                executeSafe {
                    deployPacket(
                        CPacketResourcePackStatus(action.value),
                        "${action.value}"
                    )
                }
            }
        }

        literal("SeenAdvancements") {
            executeSafe {
                MessageSendHelper.sendChatMessage("Not yet implemented. Consider to make a pull request.")
            }
        }

        literal("Spectate") {
            executeSafe {
                MessageSendHelper.sendChatMessage("Not yet implemented. Consider to make a pull request.")
            }
        }

        literal("SteerBoat") {
            boolean("left") { left ->
                boolean("right") { right ->
                    executeSafe {
                        deployPacket(
                            CPacketSteerBoat(left.value, right.value),
                            "${left.value} ${right.value}"
                        )
                    }
                }
            }
        }

        literal("TabComplete") {
            string("message") { message ->
                blockPos("targetBlock") { targetBlock ->
                    boolean("hasTargetBlock") { hasTargetBlock ->
                        executeSafe {
                            deployPacket(
                                CPacketTabComplete(message.value, targetBlock.value, hasTargetBlock.value),
                                "${message.value} ${targetBlock.value} ${hasTargetBlock.value}"
                            )
                        }
                    }
                }
            }
        }

        literal("UpdateSign") {
            blockPos("position") { position ->
                string("line1") { line1 ->
                    string("line2") { line2 ->
                        string("line3") { line3 ->
                            string("line4") { line4 ->
                                executeSafe {
                                    val lines = listOf(TextComponentString(line1.value),
                                        TextComponentString(line2.value),
                                        TextComponentString(line3.value),
                                        TextComponentString(line4.value))

                                    deployPacket(
                                        CPacketUpdateSign(position.value, lines.toTypedArray()),
                                        "${line1.value} ${line2.value} ${line3.value} ${line4.value}"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        literal("UseEntityAttack") {
            int("ID") { id ->
                executeSafe {
                    val packet = CPacketUseEntity()
                    packet.useEntityId = id.value

                    deployPacket(
                        packet,
                        "${id.value}"
                    )
                }
            }
        }

        literal("UseEntityInteract") {
            enum<EnumHand>("hand") { hand ->
                int("ID") { id ->
                    executeSafe {
                        val entity = EntityDonkey(world)
                        entity.entityId = id.value

                        deployPacket(
                            CPacketUseEntity(entity, hand.value),
                            "${id.value} ${hand.value}"
                        )
                    }
                }
            }
        }

        literal("UseEntityInteractAt") {
            enum<EnumHand>("hand") { hand ->
                double("x") { x ->
                    double("y") { y ->
                        double("z") { z ->
                            int("ID") { id ->
                                executeSafe {
                                    val entity = EntityDonkey(world)
                                    entity.entityId = id.value
                                    val vec = Vec3d(x.value, y.value, z.value)

                                    deployPacket(
                                        CPacketUseEntity(entity, hand.value, vec),
                                        "${id.value} ${hand.value} $vec"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        literal("VehicleMove") {
            executeSafe {
                MessageSendHelper.sendChatMessage("Not yet implemented. Consider to make a pull request.")
            }
        }
    }

    private fun SafeClientEvent.deployPacket(packet: Packet<*>, info: String) {
        connection.sendPacket(packet)
        MessageSendHelper.sendChatMessage("Sent ${TextFormatting.GRAY}${packet.javaClass.name.split(".").lastOrNull()}${TextFormatting.DARK_RED} > ${TextFormatting.GRAY}$info")
    }
}