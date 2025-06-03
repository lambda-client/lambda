package com.lambda.module.modules.render

import com.lambda.Lambda.mc
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.util.Arm
import net.minecraft.util.Hand
import net.minecraft.util.math.RotationAxis
import org.joml.Vector3f
import org.joml.Vector3i

object ViewModel : Module(
    name = "View Model",
    description = "Adjusts hand and held item rendering",
    defaultTags = setOf(ModuleTag.RENDER)
) {
    private val page by setting("Page", Page.General)

    //ToDo: implement the rest of the settings and maybe add a couple more

    private val ignoreHand by setting("Ignore Hand", false, "Prevents adjusting the players hand", visibility = { page == Page.General })
    private val swingMode by setting("Swing Mode", SwingMode.Standard, "Changes which hands swing", visibility = { page == Page.General })
    val swingSpeed by setting("Swing Speed", 6, 0..20, 1, "Adjusts how fast the player swings", visibility = { page == Page.General })
    val mainSwingProgress by setting("Main Swing Progress", 0.0f, 0.0f..1.0f, 0.025f, "Renders as if the players main hand was this progress through the swing animation", visibility = { page == Page.General })
    val offhandSwingProgress by setting("Offhand Swing Progress", 0.0f, 0.0f..1.0f, 0.025f, "Renders as if the players offhand was this progress through the swing animation", visibility = { page == Page.General })
    val oldAnimations by setting("Old Animations", false, "Adjusts the animations to look like they did in 1.8", visibility = { page == Page.General })
    val swapAnimation by setting("Swap Animation", true, "If disabled, it removes the drop down animation when swapping item", visibility = { page == Page.General && oldAnimations })
    val shadow by setting("Shadows", true, "If disabled, it removes shadows on the model", visibility = { page == Page.General })

    private val linkedScale by setting("Linked Scale", true, "Links both hands scale settings", visibility = { page == Page.Scale })
    private val leftXScale by setting("Left X Scale", 1.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Scale }).apply { onValueChange { _, to -> if (linkedScale) rightXScale = to } }
    private val leftYScale by setting("Left Y Scale", 1.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Scale }).apply { onValueChange { _, to -> if (linkedScale) rightYScale = to } }
    private val leftZScale by setting("Left Z Scale", 1.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Scale }).apply { onValueChange { _, to -> if (linkedScale) rightZScale = to } }
    private var rightXScale by setting("Right X Scale", 1.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Scale && !linkedScale })
    private var rightYScale by setting("Right Y Scale", 1.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Scale && !linkedScale })
    private var rightZScale by setting("Right Z Scale", 1.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Scale && !linkedScale })

    private val linkedPosition by setting("Linked Position", true, "Links both hands position settings", visibility = { page == Page.Position })
    private val leftXPosition by setting("Left X Position", 0.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Position }).apply { onValueChange { _, to -> if (linkedPosition) rightXPosition = to } }
    private val leftYPosition by setting("Left Y Position", 0.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Position }).apply { onValueChange { _, to -> if (linkedPosition) rightYPosition = to } }
    private val leftZPosition by setting("Left Z Position", 0.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Position }).apply { onValueChange { _, to -> if (linkedPosition) rightZPosition = to } }
    private var rightXPosition by setting("Right X Position", 0.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Position && !linkedPosition })
    private var rightYPosition by setting("Right Y Position", 0.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Position && !linkedPosition })
    private var rightZPosition by setting("Right Z Position", 0.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Position && !linkedPosition })

    private val linkedRotation by setting("Linked Rotation", true, "Links both hands rotation settings", visibility = { page == Page.Rotation })
    private val leftXRotation by setting("Left X Rotation", 0, -180..180, 1, visibility = { page == Page.Rotation }).apply { onValueChange { _, to -> if (linkedRotation) rightXRotation = to } }
    private val leftYRotation by setting("Left Y Rotation", 0, -180..180, 1, visibility = { page == Page.Rotation }).apply { onValueChange { _, to -> if (linkedRotation) rightYRotation = to } }
    private val leftZRotation by setting("Left Z Rotation", 0, -180..180, 1, visibility = { page == Page.Rotation }).apply { onValueChange { _, to -> if (linkedRotation) rightZRotation = to } }
    private var rightXRotation by setting("Right X Rotation", 0, -180..180, 1, visibility = { page == Page.Rotation && !linkedRotation })
    private var rightYRotation by setting("Right Y Rotation", 0, -180..180, 1, visibility = { page == Page.Rotation && !linkedRotation })
    private var rightZRotation by setting("Right Z Rotation", 0, -180..180, 1, visibility = { page == Page.Rotation && !linkedRotation })

    private val linkedFOV by setting("Linked FOV", true, "Links both hands FOV settings", visibility = { page == Page.FOV })
    private val leftFOV by setting ("Left FOV", 80, 10..180, 1, visibility = { page == Page.FOV }).apply { onValueChange { _, to -> if (linkedFOV) rightFOV = to } }
    private var rightFOV by setting ("Right FOV", 80, 10..180, 1, visibility = { page == Page.FOV && !linkedFOV })

    private val handXScale by setting("Hand X Scale", 1.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Hand })
    private val handYScale by setting("Hand Y Scale", 1.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Hand })
    private val handZScale by setting("Hand Z Scale", 1.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Hand })
    private val handXPosition by setting("Hand X Position", 0.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Hand })
    private val handYPosition by setting("Hand Y Position", 0.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Hand })
    private val handZPosition by setting("Hand Z Position", 0.0f, -1.0f..1.0f, 0.025f, visibility = { page == Page.Hand })
    private var handXRotation by setting("Hand X Rotation", 0, -180..180, 1, visibility = { page == Page.Hand })
    private var handYRotation by setting("Hand Y Rotation", 0, -180..180, 1, visibility = { page == Page.Hand })
    private var handZRotation by setting("Hand Z Rotation", 0, -180..180, 1, visibility = { page == Page.Hand })
    private val handFOV by setting("Hand FOV", 80, 10..180, 1, visibility = { page == Page.Hand })

    private enum class Page {
        General, Scale, Position, Rotation, FOV, Hand
    }

    private enum class Side {
        Left, Right
    }

    enum class SwingMode {
        Standard, Opposites, MainHand, OffHand, None
    }

    fun transform(itemStack: ItemStack, hand: Hand, matrices: MatrixStack) {
        val side = if (mc.options.mainArm.value == Arm.LEFT) {
            if (hand == Hand.MAIN_HAND) Side.Left else Side.Right
        } else {
            if (hand == Hand.MAIN_HAND) Side.Right else Side.Left
        }

        val emptyHand = itemStack.isEmpty
        if (ignoreHand && emptyHand) return

        scale(side, matrices, emptyHand)
        position(side, matrices, emptyHand)
        rotate(side, matrices, emptyHand)
    }

    private fun scale(side: Side, matrices: MatrixStack, emptyHand: Boolean) {
        val scaleVec = getScaleVec(side, emptyHand)
        matrices.scale(scaleVec.x, scaleVec.y, scaleVec.z)
    }

    private fun getScaleVec(side: Side, emptyHand: Boolean): Vector3f {
        if (emptyHand) {
            return Vector3f(
                handXScale,
                handYScale,
                handZScale
            )
        }

        when (side) {
            Side.Left -> {
                return Vector3f(
                    leftXScale,
                    leftYScale,
                    leftZScale
                )
            }
            Side.Right -> {
                return Vector3f(
                    rightXScale,
                    rightYScale,
                    rightZScale
                )
            }
        }
    }

    private fun position(side: Side, matrices: MatrixStack, emptyHand: Boolean) {
        val positionVec = getPositionVec(side, emptyHand)
        matrices.translate(positionVec.x, positionVec.y, positionVec.z)
    }

    private fun getPositionVec(side: Side, emptyHand: Boolean): Vector3f {
        return when (side) {
            Side.Left -> {
                if (emptyHand) {
                    Vector3f(
                        -handXPosition,
                        handYPosition,
                        handZPosition
                    )
                } else {
                    Vector3f(
                        -leftXPosition,
                        leftYPosition,
                        leftZPosition
                    )
                }
            }
            Side.Right -> {
                if (emptyHand) {
                    Vector3f(
                        handXPosition,
                        handYPosition,
                        handZPosition
                    )
                } else {
                    Vector3f(
                        rightXPosition,
                        rightYPosition,
                        rightZPosition
                    )
                }
            }
        }
    }

    private fun rotate(side: Side, matrices: MatrixStack, emptyHand: Boolean) {
        val rotationVec = getRotationVec(side, emptyHand)
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotationVec.x.toFloat()))
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationVec.y.toFloat()))
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationVec.z.toFloat()))
    }

    private fun getRotationVec(side: Side, emptyHand: Boolean): Vector3i {
        return when (side) {
            Side.Left -> {
                if (emptyHand) {
                    Vector3i(
                        handXRotation,
                        -handYRotation,
                        -handZRotation
                    )
                } else {
                    Vector3i(
                        leftXRotation,
                        -leftYRotation,
                        -leftZRotation
                    )
                }
            }
            Side.Right -> {
                if (emptyHand) {
                    Vector3i(
                        handXRotation,
                        handYRotation,
                        handZRotation
                    )
                } else {
                    Vector3i(
                        rightXRotation,
                        rightYRotation,
                        rightZRotation
                    )
                }
            }
        }
    }

    fun adjustSwing(hand: Hand, player: AbstractClientPlayerEntity) {
        when (swingMode) {
            SwingMode.Standard -> swingHand(hand, player)
            SwingMode.Opposites -> {
                if (hand == Hand.MAIN_HAND) swingHand(Hand.OFF_HAND, player)
                else swingHand(Hand.MAIN_HAND, player)
            }
            SwingMode.MainHand -> swingHand(Hand.MAIN_HAND, player)
            SwingMode.OffHand -> swingHand(Hand.OFF_HAND, player)
            SwingMode.None -> {}
        }
    }

    private fun swingHand(hand: Hand, player: AbstractClientPlayerEntity) {
        with(player) {
            if ((!handSwinging || handSwingTicks >= handSwingDuration / 2) || handSwingTicks < 0) {
                handSwingTicks = -1
                handSwinging = true
                preferredHand = hand
            }
        }
    }
}