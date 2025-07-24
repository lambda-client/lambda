package com.lambda.module.modules.render

import com.lambda.Lambda.mc
import com.lambda.event.events.KeyboardEvent
import com.lambda.event.events.MouseEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
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
    private val page by setting("Page", Page.General)

    private val swingMode by setting("Swing Mode", SwingMode.Standard, "Changes which hands swing") { page == Page.General }
    val swingDuration by setting("Swing Duration", 6, 0..20, 1, "Adjusts how fast the player swings", "ticks") { page == Page.General }
    private val noSwingDelay by setting("No Swing Delay", false, "Removes the delay between swings") { page == Page.General }
    val mainSwingProgress by setting("Main Swing Progress", 0.0f, 0.0f..1.0f, 0.025f, "Renders as if the players main hand was this progress through the swing animation") { page == Page.General }
    val offhandSwingProgress by setting("Offhand Swing Progress", 0.0f, 0.0f..1.0f, 0.025f, "Renders as if the players offhand was this progress through the swing animation") { page == Page.General }
    val oldAnimations by setting("Old Animations", false, "Adjusts the animations to look like they did in 1.8") { page == Page.General }
    val swapAnimation by setting("Swap Animation", true, "If disabled, removes the drop down animation when swapping item") { page == Page.General && oldAnimations }
    //ToDo: Implement
//    val shadow by setting("Shadows", true, "If disabled, removes shadows on the model") { page == Page.General }

    private val splitScale by setting("Split Scale", false, "Splits left and right hand scale settings") { page == Page.Scale }
    private val xScale by setting("X Scale", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Scale && !splitScale }.onValueChange { _, to -> leftXScale = to; rightXScale = to }
    private val yScale by setting("Y Scale", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Scale && !splitScale }.onValueChange { _, to -> leftYScale = to; rightYScale = to }
    private val zScale by setting("Z Scale", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Scale && !splitScale }.onValueChange { _, to -> leftZScale = to; rightZScale = to }
    private var leftXScale by setting("Left X Scale", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Scale && splitScale }
    private var leftYScale by setting("Left Y Scale", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Scale && splitScale }
    private var leftZScale by setting("Left Z Scale", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Scale && splitScale }
    private var rightXScale by setting("Right X Scale", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Scale && splitScale }
    private var rightYScale by setting("Right Y Scale", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Scale && splitScale }
    private var rightZScale by setting("Right Z Scale", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Scale && splitScale }

    private val splitPosition by setting("Split Position", false, "Splits left and right position settings") { page == Page.Position }
    private val xPosition by setting("X Position", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Position && !splitPosition }.onValueChange { _, to -> leftXPosition = to; rightXPosition = to }
    private val yPosition by setting("Y Position", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Position && !splitPosition }.onValueChange { _, to -> leftYPosition = to; rightYPosition = to }
    private val zPosition by setting("Z Position", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Position && !splitPosition }.onValueChange { _, to -> leftZPosition = to; rightZPosition = to }
    private var leftXPosition by setting("Left X Position", 0.0f, -1.0f..1.0f, 0.025f) { page == Page.Position && splitPosition }
    private var leftYPosition by setting("Left Y Position", 0.0f, -1.0f..1.0f, 0.025f) { page == Page.Position && splitPosition }
    private var leftZPosition by setting("Left Z Position", 0.0f, -1.0f..1.0f, 0.025f) { page == Page.Position && splitPosition }
    private var rightXPosition by setting("Right X Position", 0.0f, -1.0f..1.0f, 0.025f) { page == Page.Position && splitPosition }
    private var rightYPosition by setting("Right Y Position", 0.0f, -1.0f..1.0f, 0.025f) { page == Page.Position && splitPosition }
    private var rightZPosition by setting("Right Z Position", 0.0f, -1.0f..1.0f, 0.025f) { page == Page.Position && splitPosition }

    private val splitRotation by setting("Split Rotation", false, "Splits left and right rotation settings") { page == Page.Rotation }
    private val xRotation by setting("X Rotation", 0, -180..180, 1) { page == Page.Rotation && !splitRotation }.onValueChange { _, to -> leftXRotation = to; rightXRotation = to }
    private val yRotation by setting("Y Rotation", 0, -180..180, 1) { page == Page.Rotation && !splitRotation }.onValueChange { _, to -> leftYRotation = to; rightYRotation = to }
    private val zRotation by setting("Z Rotation", 0, -180..180, 1) { page == Page.Rotation && !splitRotation }.onValueChange { _, to -> leftZRotation = to; rightZRotation = to }
    private var leftXRotation by setting("Left X Rotation", 0, -180..180, 1) { page == Page.Rotation && splitRotation }
    private var leftYRotation by setting("Left Y Rotation", 0, -180..180, 1) { page == Page.Rotation && splitRotation }
    private var leftZRotation by setting("Left Z Rotation", 0, -180..180, 1) { page == Page.Rotation && splitRotation }
    private var rightXRotation by setting("Right X Rotation", 0, -180..180, 1) { page == Page.Rotation && splitRotation }
    private var rightYRotation by setting("Right Y Rotation", 0, -180..180, 1) { page == Page.Rotation && splitRotation }
    private var rightZRotation by setting("Right Z Rotation", 0, -180..180, 1) { page == Page.Rotation && splitRotation }

    private val splitFov by setting("Split FOV", false, "Splits left and right Fov settings") { page == Page.Fov }
    private val fov by setting("FOV", 70, 10..180, 1) { page == Page.Fov && !splitFov }.onValueChange { _, to -> leftFov = to; rightFov = to }
    private val fovAnchorDistance by setting("Anchor Distance", 0.5f, 0.0f..1.0f, 0.01f, "The distance to anchor the FOV transformation from") { page == Page.Fov && !splitFov }.onValueChange { _, to -> leftFovAnchorDistance = to; rightFovAnchorDistance = to }
    private var leftFov by setting("Left FOV", 70, 10..180, 1) { page == Page.Fov  && splitFov}
    private var leftFovAnchorDistance by setting("Left Anchor Distance", 0.5f, 0.0f..1.0f, 0.01f, "The distance to anchor the left FOV transformation from") { page == Page.Fov && splitFov }
    private var rightFov by setting("Right FOV", 70, 10..180, 1) { page == Page.Fov && splitFov }
    private var rightFovAnchorDistance by setting("Right Anchor Distance", 0.5f, 0.0f..1.0f, 0.01f, "The distance to anchor the right FOV transformation from") { page == Page.Fov && splitFov }

    private val enableHand by setting("Hand", false, "Enables settings for the players hand") { page == Page.Hand }
    private val handXScale by setting("Hand X Scale", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Hand && enableHand }
    private val handYScale by setting("Hand Y Scale", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Hand && enableHand }
    private val handZScale by setting("Hand Z Scale", 1.0f, -1.0f..1.0f, 0.025f) { page == Page.Hand && enableHand }
    private val handXPosition by setting("Hand X Position", 0.0f, -1.0f..1.0f, 0.025f) { page == Page.Hand && enableHand }
    private val handYPosition by setting("Hand Y Position", 0.0f, -1.0f..1.0f, 0.025f) { page == Page.Hand && enableHand }
    private val handZPosition by setting("Hand Z Position", 0.0f, -1.0f..1.0f, 0.025f) { page == Page.Hand && enableHand }
    private val handXRotation by setting("Hand X Rotation", 0, -180..180, 1) { page == Page.Hand && enableHand }
    private val handYRotation by setting("Hand Y Rotation", 0, -180..180, 1) { page == Page.Hand && enableHand }
    private val handZRotation by setting("Hand Z Rotation", 0, -180..180, 1) { page == Page.Hand && enableHand }
    private val handFov by setting("Hand FOV", 70, 10..180, 1) { page == Page.Hand && enableHand }
    private val handFovAnchorDistance by setting("Hand FOV Anchor Distance", 0.5f, 0.0f..1.0f, 0.01f, "The distance to anchor the hands FOV transformation from") { page == Page.Hand && enableHand }

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

    private enum class Page {
        General, Scale, Position, Rotation, Fov, Hand
    }

    private enum class Side {
        Left, Right
    }

    private enum class SwingMode {
        Standard, Opposites, MainHand, OffHand, None
    }
}
