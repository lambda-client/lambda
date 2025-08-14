/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.render

import com.lambda.Lambda.mc
import com.lambda.event.events.KeyboardEvent
import com.lambda.event.events.MouseEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.util.Arm
import net.minecraft.util.Hand
import net.minecraft.util.math.RotationAxis
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3i
import kotlin.math.tan

object ViewModel : Module(
    name = "View Model",
    description = "Adjusts hand and held item rendering",
    tag = ModuleTag.RENDER,
) {
    private val swingMode by setting("Swing Mode", SwingMode.Standard, "Changes which hands swing").group(Group.General)
    val swingDuration by setting("Swing Duration", 6, 0..20, 1, "Adjusts how fast the player swings", "ticks").group(Group.General)
    private val noSwingDelay by setting("No Swing Delay", false, "Removes the delay between swings").group(Group.General)
    val mainSwingProgress by setting("Main Swing Progress", 0.0f, 0.0f..1.0f, 0.025f, "Renders as if the players main hand was this progress through the swing animation").group(Group.General)
    val offhandSwingProgress by setting("Offhand Swing Progress", 0.0f, 0.0f..1.0f, 0.025f, "Renders as if the players offhand was this progress through the swing animation").group(Group.General)
    val oldAnimations by setting("Old Animations", false, "Adjusts the animations to look like they did in 1.8").group(Group.General)
    val swapAnimation by setting("Swap Animation", true, "If disabled, removes the drop down animation when swapping item") { oldAnimations }.group(Group.General)
    //ToDo: Implement
//    val shadow by setting("Shadows", true, "If disabled, removes shadows on the model") { page == Page.General }

    private val splitScale by setting("Split Scale", false, "Splits left and right hand scale settings").group(Group.Scale)
    private val xScale by setting("X Scale", 1.0f, -1.0f..1.0f, 0.025f) { !splitScale }.onValueChange { _, to -> leftXScale = to; rightXScale = to }.group(Group.Scale)
    private val yScale by setting("Y Scale", 1.0f, -1.0f..1.0f, 0.025f) { !splitScale }.onValueChange { _, to -> leftYScale = to; rightYScale = to }.group(Group.Scale)
    private val zScale by setting("Z Scale", 1.0f, -1.0f..1.0f, 0.025f) { !splitScale }.onValueChange { _, to -> leftZScale = to; rightZScale = to }.group(Group.Scale)
    private var leftXScale by setting("Left X Scale", 1.0f, -1.0f..1.0f, 0.025f) { splitScale }.group(Group.Scale)
    private var leftYScale by setting("Left Y Scale", 1.0f, -1.0f..1.0f, 0.025f) { splitScale }.group(Group.Scale)
    private var leftZScale by setting("Left Z Scale", 1.0f, -1.0f..1.0f, 0.025f) { splitScale }.group(Group.Scale)
    private var rightXScale by setting("Right X Scale", 1.0f, -1.0f..1.0f, 0.025f) { splitScale }.group(Group.Scale)
    private var rightYScale by setting("Right Y Scale", 1.0f, -1.0f..1.0f, 0.025f) { splitScale }.group(Group.Scale)
    private var rightZScale by setting("Right Z Scale", 1.0f, -1.0f..1.0f, 0.025f) { splitScale }.group(Group.Scale)

    private val splitPosition by setting("Split Position", false, "Splits left and right position settings").group(Group.Position)
    private val xPosition by setting("X Position", 1.0f, -1.0f..1.0f, 0.025f) { !splitPosition }.onValueChange { _, to -> leftXPosition = to; rightXPosition = to }.group(Group.Position)
    private val yPosition by setting("Y Position", 1.0f, -1.0f..1.0f, 0.025f) { !splitPosition }.onValueChange { _, to -> leftYPosition = to; rightYPosition = to }.group(Group.Position)
    private val zPosition by setting("Z Position", 1.0f, -1.0f..1.0f, 0.025f) { !splitPosition }.onValueChange { _, to -> leftZPosition = to; rightZPosition = to }.group(Group.Position)
    private var leftXPosition by setting("Left X Position", 0.0f, -1.0f..1.0f, 0.025f) { splitPosition }.group(Group.Position)
    private var leftYPosition by setting("Left Y Position", 0.0f, -1.0f..1.0f, 0.025f) { splitPosition }.group(Group.Position)
    private var leftZPosition by setting("Left Z Position", 0.0f, -1.0f..1.0f, 0.025f) { splitPosition }.group(Group.Position)
    private var rightXPosition by setting("Right X Position", 0.0f, -1.0f..1.0f, 0.025f) { splitPosition }.group(Group.Position)
    private var rightYPosition by setting("Right Y Position", 0.0f, -1.0f..1.0f, 0.025f) { splitPosition }.group(Group.Position)
    private var rightZPosition by setting("Right Z Position", 0.0f, -1.0f..1.0f, 0.025f) { splitPosition }.group(Group.Position)

    private val splitRotation by setting("Split Rotation", false, "Splits left and right rotation settings").group(Group.Rotation)
    private val xRotation by setting("X Rotation", 0, -180..180, 1) { !splitRotation }.onValueChange { _, to -> leftXRotation = to; rightXRotation = to }.group(Group.Rotation)
    private val yRotation by setting("Y Rotation", 0, -180..180, 1) { !splitRotation }.onValueChange { _, to -> leftYRotation = to; rightYRotation = to }.group(Group.Rotation)
    private val zRotation by setting("Z Rotation", 0, -180..180, 1) { !splitRotation }.onValueChange { _, to -> leftZRotation = to; rightZRotation = to }.group(Group.Rotation)
    private var leftXRotation by setting("Left X Rotation", 0, -180..180, 1) { splitRotation }.group(Group.Rotation)
    private var leftYRotation by setting("Left Y Rotation", 0, -180..180, 1) { splitRotation }.group(Group.Rotation)
    private var leftZRotation by setting("Left Z Rotation", 0, -180..180, 1) { splitRotation }.group(Group.Rotation)
    private var rightXRotation by setting("Right X Rotation", 0, -180..180, 1) { splitRotation }.group(Group.Rotation)
    private var rightYRotation by setting("Right Y Rotation", 0, -180..180, 1) { splitRotation }.group(Group.Rotation)
    private var rightZRotation by setting("Right Z Rotation", 0, -180..180, 1) { splitRotation }.group(Group.Rotation)

    private val splitFov by setting("Split FOV", false, "Splits left and right Fov settings").group(Group.Fov)
    private val fov by setting("FOV", 70, 10..180, 1) { !splitFov }.onValueChange { _, to -> leftFov = to; rightFov = to }.group(Group.Fov)
    private val fovAnchorDistance by setting("Anchor Distance", 0.5f, 0.0f..1.0f, 0.01f, "The distance to anchor the FOV transformation from") { !splitFov }.onValueChange { _, to -> leftFovAnchorDistance = to; rightFovAnchorDistance = to }.group(Group.Fov)
    private var leftFov by setting("Left FOV", 70, 10..180, 1) { splitFov }.group(Group.Fov)
    private var leftFovAnchorDistance by setting("Left Anchor Distance", 0.5f, 0.0f..1.0f, 0.01f, "The distance to anchor the left FOV transformation from") { splitFov }.group(Group.Fov)
    private var rightFov by setting("Right FOV", 70, 10..180, 1) { splitFov }.group(Group.Fov)
    private var rightFovAnchorDistance by setting("Right Anchor Distance", 0.5f, 0.0f..1.0f, 0.01f, "The distance to anchor the right FOV transformation from") { splitFov }.group(Group.Fov)

    private val enableHand by setting("Hand", false, "Enables settings for the players hand").group(Group.Hand)
    private val handXScale by setting("Hand X Scale", 1.0f, -1.0f..1.0f, 0.025f) { enableHand }.group(Group.Hand)
    private val handYScale by setting("Hand Y Scale", 1.0f, -1.0f..1.0f, 0.025f) { enableHand }.group(Group.Hand)
    private val handZScale by setting("Hand Z Scale", 1.0f, -1.0f..1.0f, 0.025f) { enableHand }.group(Group.Hand)
    private val handXPosition by setting("Hand X Position", 0.0f, -1.0f..1.0f, 0.025f) { enableHand }.group(Group.Hand)
    private val handYPosition by setting("Hand Y Position", 0.0f, -1.0f..1.0f, 0.025f) { enableHand }.group(Group.Hand)
    private val handZPosition by setting("Hand Z Position", 0.0f, -1.0f..1.0f, 0.025f) { enableHand }.group(Group.Hand)
    private val handXRotation by setting("Hand X Rotation", 0, -180..180, 1) { enableHand }.group(Group.Hand)
    private val handYRotation by setting("Hand Y Rotation", 0, -180..180, 1) { enableHand }.group(Group.Hand)
    private val handZRotation by setting("Hand Z Rotation", 0, -180..180, 1) { enableHand }.group(Group.Hand)
    private val handFov by setting("Hand FOV", 70, 10..180, 1) { enableHand }.group(Group.Hand)
    private val handFovAnchorDistance by setting("Hand FOV Anchor Distance", 0.5f, 0.0f..1.0f, 0.01f, "The distance to anchor the hands FOV transformation from") { enableHand }.group(Group.Hand)

    private var attackKeyTicksPressed = -1

    init {
        listen<MouseEvent.Click> { event ->
            if (event.button == mc.options.attackKey.boundKey.code)
                attackKeyTicksPressed = if (event.action == 0) -1 else 0
        }

        listen<KeyboardEvent.Press> { event ->
            if (event.keyCode == mc.options.attackKey.boundKey.code) {
                if (event.isPressed) {
                    attackKeyTicksPressed = 0
                } else if (event.isReleased) {
                    attackKeyTicksPressed = -1
                }
            }
        }

        listen<TickEvent.Pre> {
            if (attackKeyTicksPressed != -1)
                attackKeyTicksPressed++
        }
    }

    fun transform(itemStack: ItemStack, hand: Hand, matrices: MatrixStack) {
        val side = if (mc.options.mainArm.value == Arm.LEFT) {
            if (hand == Hand.MAIN_HAND) Side.Left else Side.Right
        } else {
            if (hand == Hand.MAIN_HAND) Side.Right else Side.Left
        }

        val emptyHand = itemStack.isEmpty
        if (!enableHand && emptyHand) return

        applyItemFov(matrices, side, emptyHand)
        scale(side, matrices, emptyHand)
        position(side, matrices, emptyHand)
        rotate(side, matrices, emptyHand)
    }

    private fun applyItemFov(matrices: MatrixStack, side: Side, emptyHand: Boolean) {
        val fov = when {
            side == Side.Left -> leftFov
            emptyHand -> handFov
            else -> rightFov
        }.toFloat()

        if (fov == 70f) return

        val fovRatio = tan(Math.toRadians(fov.toDouble()/2)).toFloat() / tan(Math.toRadians(70.0/2)).toFloat()

        val matrix = matrices.peek().positionMatrix

        val distance = if (emptyHand) {
            handFovAnchorDistance
        } else {
            when (side) {
                Side.Left -> leftFovAnchorDistance
                Side.Right -> rightFovAnchorDistance
            }
        }

        val warpMatrix = Matrix4f().apply {
            translate(0f, 0f, -distance)
            scale(1f, 1f, fovRatio)
            translate(0f, 0f, distance)
        }

        matrix.mul(warpMatrix)
    }

    private fun scale(side: Side, matrices: MatrixStack, emptyHand: Boolean) {
        val scaleVec = getScaleVec(side, emptyHand)
        matrices.scale(scaleVec.x, scaleVec.y, scaleVec.z)
    }

    private fun getScaleVec(side: Side, emptyHand: Boolean): Vector3f =
        if (emptyHand) Vector3f(handXScale, handYScale, handZScale)
        else when (side) {
            Side.Left -> Vector3f(leftXScale, leftYScale, leftZScale)
            Side.Right -> Vector3f(rightXScale, rightYScale, rightZScale)
        }

    private fun position(side: Side, matrices: MatrixStack, emptyHand: Boolean) {
        val positionVec = getPositionVec(side, emptyHand)
        matrices.translate(positionVec.x, positionVec.y, positionVec.z)
    }

    private fun getPositionVec(side: Side, emptyHand: Boolean) =
        when (side) {
            Side.Left ->
                if (emptyHand) Vector3f(-handXPosition, handYPosition, handZPosition)
                else Vector3f(-leftXPosition, leftYPosition, leftZPosition)
            Side.Right ->
                if (emptyHand) Vector3f(handXPosition, handYPosition, handZPosition)
                else Vector3f(rightXPosition, rightYPosition, rightZPosition)
        }

    private fun rotate(side: Side, matrices: MatrixStack, emptyHand: Boolean) {
        val rotationVec = getRotationVec(side, emptyHand)
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotationVec.x.toFloat()))
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationVec.y.toFloat()))
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationVec.z.toFloat()))
    }

    private fun getRotationVec(side: Side, emptyHand: Boolean) =
        when (side) {
            Side.Left -> {
                if (emptyHand) Vector3i(handXRotation, -handYRotation, -handZRotation)
                else Vector3i(leftXRotation, -leftYRotation, -leftZRotation)
            }
            Side.Right -> {
                if (emptyHand) Vector3i(handXRotation, handYRotation, handZRotation)
                else Vector3i(rightXRotation, rightYRotation, rightZRotation)
            }
        }

    fun adjustSwing(hand: Hand, player: AbstractClientPlayerEntity) =
        when (swingMode) {
            SwingMode.Standard -> swingHand(hand, player)
            SwingMode.Opposites ->
                if (hand == Hand.MAIN_HAND) swingHand(Hand.OFF_HAND, player)
                else swingHand(Hand.MAIN_HAND, player)
            SwingMode.MainHand -> swingHand(Hand.MAIN_HAND, player)
            SwingMode.OffHand -> swingHand(Hand.OFF_HAND, player)
            SwingMode.None -> {}
        }

    private fun swingHand(hand: Hand, player: AbstractClientPlayerEntity) =
        with(player) {
            if (
                (!handSwinging || handSwingTicks >= handSwingDuration / 2) ||
                handSwingTicks < 0 ||
                (noSwingDelay && attackKeyTicksPressed <= 1))
            {
                handSwingTicks = -1
                handSwinging = true
                preferredHand = hand
            }
        }

    private enum class Group(override val displayName: String): NamedEnum {
        General("General"),
        Scale("Scale"),
        Position("Position"),
        Rotation("Rotation"),
        Fov("FOV"),
        Hand("Hand")
    }

    private enum class Side {
        Left, Right
    }

    private enum class SwingMode {
        Standard, Opposites, MainHand, OffHand, None
    }
}
