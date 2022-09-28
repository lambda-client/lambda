package com.lambda.client.module.modules.movement

import com.lambda.client.LambdaMod
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.gui.AbstractLambdaGui
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.MovementUtils.isMoving
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.gui.GuiChat
import net.minecraft.client.gui.GuiRepair
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.inventory.GuiEditSign
import net.minecraft.network.play.client.CPacketClickWindow
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.util.MovementInputFromOptions
import net.minecraftforge.client.event.InputUpdateEvent
import org.lwjgl.input.Keyboard

object InventoryMove : Module(
    name = "InventoryMove",
    description = "Allows you to walk around with GUIs opened",
    category = Category.MOVEMENT
) {
    private val rotateSpeed by setting("Rotate Speed", 5, 0..20, 1)
    private val itemMovement by setting("Item Movement Bypass", true)
    val sneak by setting("Sneak", false)

    private var hasSent = false
    private var savedClickWindow = CPacketClickWindow()
    private const val upSpoofDistance = 0.0656

    init {
        safeListener<InputUpdateEvent>(9999) {
            if (it.movementInput !is MovementInputFromOptions || isInvalidGui(mc.currentScreen)) return@safeListener

            if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
                player.rotationYaw = player.rotationYaw - rotateSpeed
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
                player.rotationYaw = player.rotationYaw + rotateSpeed
            }

            // pitch can not exceed 90 degrees nor -90 degrees, otherwise AAC servers will flag this and kick you.
            if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
                player.rotationPitch = (player.rotationPitch - rotateSpeed).coerceAtLeast(-90.0f)
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
                player.rotationPitch = (player.rotationPitch + rotateSpeed).coerceAtMost(90.0f)
            }

            it.movementInput.moveStrafe = 0.0f
            it.movementInput.moveForward = 0.0f

            try {
                if (Keyboard.isKeyDown(mc.gameSettings.keyBindForward.keyCode)) {
                    ++it.movementInput.moveForward
                    it.movementInput.forwardKeyDown = true
                } else {
                    it.movementInput.forwardKeyDown = false
                }

                if (Keyboard.isKeyDown(mc.gameSettings.keyBindBack.keyCode)) {
                    --it.movementInput.moveForward
                    it.movementInput.backKeyDown = true
                } else {
                    it.movementInput.backKeyDown = false
                }

                if (Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.keyCode)) {
                    ++it.movementInput.moveStrafe
                    it.movementInput.leftKeyDown = true
                } else {
                    it.movementInput.leftKeyDown = false
                }

                if (Keyboard.isKeyDown(mc.gameSettings.keyBindRight.keyCode)) {
                    --it.movementInput.moveStrafe
                    it.movementInput.rightKeyDown = true
                } else {
                    it.movementInput.rightKeyDown = false
                }

                if (Keyboard.isKeyDown(mc.gameSettings.keyBindJump.keyCode)) {
                    it.movementInput.jump = true
                }

                if (Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.keyCode) && sneak) {
                    it.movementInput.sneak = true
                }
            } catch (e: IndexOutOfBoundsException) {
                if (!hasSent) {
                    LambdaMod.LOG.error("$chatName Error: Key is bound to a mouse button!", e)
                    hasSent = true
                }
            }
        }

        safeListener<PacketEvent.Send> {
            if (itemMovement
                && player.onGround
                && it.packet is CPacketClickWindow
                && it.packet != savedClickWindow
                && player.isMoving
                && world.getCollisionBoxes(player, player.entityBoundingBox.offset(0.0, upSpoofDistance, 0.0)).isEmpty()
            ) {
                savedClickWindow = it.packet

                it.cancel()

                if (player.isSprinting) {
                    player.connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SPRINTING))
                }

                player.connection.sendPacket(CPacketPlayer.Position(player.posX, player.posY + upSpoofDistance, player.posZ, false))
                player.connection.sendPacket(it.packet)

                if (player.isSprinting) {
                    player.connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SPRINTING))
                }
            }
        }
    }

    private fun isInvalidGui(guiScreen: GuiScreen?) = guiScreen == null
        || guiScreen is GuiChat
        || guiScreen is GuiEditSign
        || guiScreen is GuiRepair
        || guiScreen.let { it is AbstractLambdaGui<*, *> && it.searching }
}