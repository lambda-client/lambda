package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.inventory.ClickType
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.*
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.Vec3d
import net.minecraft.util.text.TextComponentString

object PacketCommand : ClientCommand(
    name = "packet",
    description = "Send any packet you want"
) {
    init {
        literal("Animation") {
            enum<EnumHand>("hand") { hand ->
                executeSafe {
                    connection.sendPacket(CPacketAnimation(hand.value))
                    postSend(this@literal.name,"${hand.value}")
                }
            }
        }

        literal("ChatMessage") {
            string("message") { message ->
                executeSafe {
                    connection.sendPacket(CPacketChatMessage(message.value))
                    postSend(this@literal.name, message.value)
                }
            }
        }

        literal("ClickWindow") {
            int("windowId") { windowId ->
                int("slotId") { slotId ->
                    int("packedClickData") { packedClickData ->
                        enum<ClickType>("mode") { mode ->
                            short("actionNumber") { actionNumber ->
                                executeSafe {
                                    // ToDo: Dynamic ItemStack
                                    connection.sendPacket(CPacketClickWindow(windowId.value,
                                        slotId.value,
                                        packedClickData.value,
                                        mode.value,
                                        ItemStack.EMPTY,
                                        actionNumber.value))
                                    postSend(this@literal.name,"${windowId.value} ${slotId.value} ${packedClickData.value} ${mode.value} ${ItemStack.EMPTY} ${actionNumber.value}")
                                }
                            }
                        }
                    }
                }
            }
        }

        literal("ClientSettings") {
            executeSafe {
                MessageSendHelper.sendChatMessage("To be implemented")
            }
        }

        literal("ClientStatus") {
            executeSafe {
                MessageSendHelper.sendChatMessage("To be implemented")
            }
        }

        literal("CloseWindow") {
            int("windowId") { windowId ->
                executeSafe {
                    connection.sendPacket(CPacketCloseWindow(windowId.value))
                    postSend(this@literal.name,"${windowId.value}")
                }
            }
        }

        literal("ConfirmTeleport") {
            int("teleportId") { teleportId ->
                executeSafe {
                    connection.sendPacket(CPacketConfirmTeleport(teleportId.value))
                    postSend(this@literal.name,"${teleportId.value}")
                }
            }
        }

        literal("ConfirmTransaction") {
            int("windowId") { windowId ->
                short("uid") { uid ->
                    boolean("accepted") { accepted ->
                        executeSafe {
                            connection.sendPacket(CPacketConfirmTransaction(windowId.value, uid.value, accepted.value))
                            postSend(this@literal.name,"${windowId.value} ${uid.value} ${accepted.value}")
                        }
                    }
                }
            }
        }

        literal("CreativeInventoryAction") {
            int("slotId") { slotId ->
                executeSafe {
                    // ToDo: Dynamic ItemStack
                    connection.sendPacket(CPacketCreativeInventoryAction(slotId.value, ItemStack.EMPTY))
                    postSend(this@literal.name,"${slotId.value} ${ItemStack.EMPTY}")
                }
            }
        }

        literal("CustomPayload") {
            executeSafe {
                MessageSendHelper.sendChatMessage("To be implemented")
            }
        }

        literal("EnchantItem") {
            int("windowId") { windowId ->
                int("button") { button ->
                    executeSafe {
                        connection.sendPacket(CPacketEnchantItem(windowId.value, button.value))
                        postSend(this@literal.name,"${windowId.value} ${button.value}")
                    }
                }
            }
        }

        literal("EntityAction") {
            enum<CPacketEntityAction.Action>("action") { action ->
                int("auxData") { auxData ->
                    executeSafe {
                        connection.sendPacket(CPacketEntityAction(player, action.value, auxData.value))
                        postSend(this@literal.name,"${player.entityId} ${action.value} ${auxData.value}")
                    }
                }
            }
        }

        literal("HeldItemChange") {
            int("slotId") { slotId ->
                executeSafe {
                    connection.sendPacket(CPacketHeldItemChange(slotId.value))
                    postSend(this@literal.name,"${slotId.value}")
                }
            }
        }

        literal("Input") {
            float("strafeSpeed") { strafeSpeed ->
                float("forwardSpeed") { forwardSpeed ->
                    boolean("jumping") { jumping ->
                        boolean("sneaking") { sneaking ->
                            executeSafe {
                                connection.sendPacket(CPacketInput(strafeSpeed.value, forwardSpeed.value, jumping.value, sneaking.value))
                                postSend(this@literal.name,"${strafeSpeed.value} ${forwardSpeed.value} ${jumping.value} ${sneaking.value}")
                            }
                        }
                    }
                }
            }
        }

        literal("KeepAlive") {
            long("key") { key ->
                executeSafe {
                    connection.sendPacket(CPacketKeepAlive(key.value))
                    postSend(this@literal.name,"${key.value}")
                }
            }
        }

        literal("PlaceRecipe") {
            executeSafe {
                MessageSendHelper.sendChatMessage("To be implemented")
            }
        }

        literal("PlayerPosition") {
            double("x") { x ->
                double("y") { y ->
                    double("z") { z ->
                        boolean("onGround") { onGround ->
                            executeSafe {
                                connection.sendPacket(CPacketPlayer.Position(x.value, y.value, z.value, onGround.value))
                                postSend(this@literal.name,"${x.value} ${y.value} ${z.value} ${onGround.value}")
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
                                        connection.sendPacket(CPacketPlayer.PositionRotation(x.value, y.value, z.value, yaw.value, pitch.value, onGround.value))
                                        postSend(this@literal.name,"${x.value} ${y.value} ${z.value} ${yaw.value} ${pitch.value} ${onGround.value}")
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
                            connection.sendPacket(CPacketPlayer.Rotation(yaw.value, pitch.value, onGround.value))
                            postSend(this@literal.name,"${yaw.value} ${pitch.value} ${onGround.value}")
                        }
                    }
                }
            }
        }

        literal("PlayerAbilities") {
            executeSafe {
                MessageSendHelper.sendChatMessage("To be implemented")
            }
        }

        literal("PlayerDigging") {
            enum<CPacketPlayerDigging.Action>("action") { action ->
                blockPos("position") { position ->
                    enum<EnumFacing>("facing") { facing ->
                        executeSafe {
                            connection.sendPacket(CPacketPlayerDigging(action.value, position.value, facing.value))
                            postSend(this@literal.name,"${action.value} ${position.value} ${facing.value}")
                        }
                    }
                }
            }
        }

        literal("PlayerTryUseItem") {
            enum<EnumHand>("hand") { hand ->
                executeSafe {
                    connection.sendPacket(CPacketPlayerTryUseItem(hand.value))
                    postSend(this@literal.name,"${hand.value}")
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
                                        connection.sendPacket(CPacketPlayerTryUseItemOnBlock(position.value, placedBlockDirection.value, hand.value, facingX.value, facingY.value, facingZ.value))
                                        postSend(this@literal.name,"${position.value} ${placedBlockDirection.value} ${hand.value} ${facingX.value} ${facingY.value} ${facingZ.value}")
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
                MessageSendHelper.sendChatMessage("To be implemented")
            }
        }

        literal("ResourcePackStatus") {
            enum<CPacketResourcePackStatus.Action>("action") { action ->
                executeSafe {
                    connection.sendPacket(CPacketResourcePackStatus(action.value))
                    postSend(this@literal.name,"${action.value}")
                }
            }
        }

        literal("SeenAdvancements") {
            executeSafe {
                MessageSendHelper.sendChatMessage("To be implemented")
            }
        }

        literal("Spectate") {
            executeSafe {
                MessageSendHelper.sendChatMessage("To be implemented")
            }
        }

        literal("SteerBoat") {
            boolean("left") { left ->
                boolean("right") { right ->
                    executeSafe {
                        connection.sendPacket(CPacketSteerBoat(left.value, right.value))
                        postSend(this@literal.name,"${left.value} ${right.value}")
                    }
                }
            }
        }

        literal("TabComplete") {
            string("message") { message ->
                blockPos("targetBlock") { targetBlock ->
                    boolean("hasTargetBlock") { hasTargetBlock ->
                        executeSafe {
                            connection.sendPacket(CPacketTabComplete(message.value, targetBlock.value, hasTargetBlock.value))
                            postSend(this@literal.name,"${message.value} ${targetBlock.value} ${hasTargetBlock.value}")
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

                                    connection.sendPacket(CPacketUpdateSign(position.value, lines.toTypedArray()))
                                    postSend(this@literal.name,"${line1.value} ${line2.value} ${line3.value} ${line4.value}")
                                }
                            }
                        }
                    }
                }
            }
        }

        literal("UseEntityAttack") {
            executeSafe {
                connection.sendPacket(CPacketUseEntity(player))
                postSend(this@literal.name,"${player.entityId}")
            }
        }

        literal("UseEntityInteract") {
            enum<EnumHand>("hand") { hand ->
                executeSafe {
                    connection.sendPacket(CPacketUseEntity(player, hand.value))
                    postSend(this@literal.name,"${player.entityId} ${hand.value}")
                }
            }
        }

        literal("UseEntityInteractAt") {
            enum<EnumHand>("hand") { hand ->
                double("x") { x ->
                    double("y") { y ->
                        double("z") { z ->
                            executeSafe {
                                val vec = Vec3d(x.value, y.value, z.value)
                                connection.sendPacket(CPacketUseEntity(player, hand.value, vec))
                                postSend(this@literal.name,"${player.entityId} ${hand.value} $vec")
                            }
                        }
                    }
                }
            }
        }

        literal("VehicleMove") {
            executeSafe {
                connection.sendPacket(CPacketVehicleMove(player))
                postSend(this@literal.name,"${player.entityId}")
            }
        }
    }

    private fun postSend(literal: String, info: String) {
        MessageSendHelper.sendChatMessage("Sent CPacket$literal > $info")
    }
}