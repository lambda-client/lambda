package com.lambda.module.modules.player

import com.lambda.config.groups.InteractionSettings
import com.lambda.config.groups.RotationSettings
import com.lambda.context.SafeContext
import com.lambda.event.events.*
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.buildSideMesh
import com.lambda.graphics.renderer.esp.builders.build
import com.lambda.interaction.RotationManager.currentRotation
import com.lambda.interaction.RotationManager.requestRotation
import com.lambda.interaction.blockplace.PlaceFinder.Companion.buildPlaceInfo
import com.lambda.interaction.blockplace.PlaceInfo
import com.lambda.interaction.blockplace.PlaceInteraction.placeBlock
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.Rotation.Companion.dist
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.visibilty.VisibilityChecker.scanVisibleSurfaces
import com.lambda.module.Module
import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.VecUtils.dist
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.math.transform
import com.lambda.util.player.MovementUtils.calcMoveYaw
import com.lambda.util.player.MovementUtils.isInputting
import com.lambda.util.player.MovementUtils.octant
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.util.*
import kotlin.math.floor
import kotlin.math.pow

object Scaffold : Module(
    name = "Scaffold",
    description = "Places blocks under the player",
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    private val page by setting("Page", Page.General)

    private val keepY by setting("Keep Y", true) { page == Page.General }
    private val airOnly by setting("In Air Only", true) { page == Page.General }
    private val minAirTicks by setting("Min Air Ticks", 3, 1..7) { page == Page.General && airOnly }
    private val minPlaceDist by setting("Min Place Dist", 0.1, 0.0..0.3, 0.01) { page == Page.General }
    //private val renderPrediction by setting("Render Prediction", false)  { page == Page.General }

    private val rotationConfig = RotationSettings(this) { page == Page.Rotation }
    private val interactionConfig = InteractionSettings(this) { page == Page.Interaction }

    private var placeInfo: PlaceInfo? = null
    private val infoSet = HashSet<Pair<PlaceInfo, Long>>()

    private var keepLevel: Int? = null
    private var airTicks = 0

    private val currentTime get() = System.currentTimeMillis()

    private val builderSideMask = EnumSet.allOf(Direction::class.java).apply {
        remove(Direction.UP) // Don't place above feet
    }

    /*private var prediction: PredictionEntity? = null
    private val predictedRenderBox = DynamicAABB()

    private val SafeContext.predictedEyes get() = prediction?.eyePos ?: player.eyePos
    private val SafeContext.predictedPos get() = prediction?.position ?: player.pos
    private val SafeContext.predictedBox get() = prediction?.boundingBox ?: player.boundingBox*/

    enum class Page {
        General,
        Rotation,
        Interaction
    }

    init {
        requestRotation(
            onUpdate = {
                val info = placeInfo ?: return@requestRotation null

                val pause = getPause(info)
                if (pause.rotatePause) return@requestRotation null

                val rotation = rotate(info) ?: return@requestRotation null
                RotationContext(rotation, rotationConfig)
            },
            onReceive = {
                val info = placeInfo ?: return@requestRotation

                val pause = getPause(info)
                if (pause.placePause) return@requestRotation

                val result = castRotation(currentRotation, info) ?: return@requestRotation
                infoSet.add(info to currentTime)
                placeBlock(result, Hand.MAIN_HAND, interactionConfig.swingHand)
            }
        )

        listener<TickEvent.Pre> {
            updatePlaceInfo()

            /*prediction = predictPlayerMovement(1)
            predictedRenderBox.update(predictedBox)*/
        }

        listener<MovementEvent.Post> {
            airTicks++
            if (player.isOnGround) airTicks = 0
        }

        /*listener<RenderEvent.DynamicESP> { event ->
            if (!mc.gameRenderer.camera.isThirdPerson || !renderPrediction) return@listener

            val c = GuiSettings.primaryColor
            event.renderer.build(predictedRenderBox, c.multAlpha(0.3), c)
        }*/

        // ToDo: optimize
        listener<RenderEvent.StaticESP> { event ->
            val c = GuiSettings.primaryColor

            infoSet.removeIf {
                val (info, time) = it

                val pos = info.placedPos
                val seconds = (currentTime - time) / 1000.0

                val sides = buildSideMesh(pos) { meshPos ->
                    infoSet.any { it.first.placedPos == meshPos }
                }

                val box = Box(info.placedPos)
                val alpha = transform(seconds, 0.0, 0.5, 1.0, 0.0).coerceIn(0.0, 1.0)

                event.renderer.build(
                    box,
                    c.multAlpha(0.3 * alpha),
                    c.multAlpha(alpha),
                    sides,
                    DirectionMask.OutlineMode.AND
                )

                seconds > 1
            }
        }

        onEnable {
            keepLevel = null
            placeInfo = null
            airTicks = 0

            /*prediction = null
            predictedRenderBox.reset()*/
        }
    }

    private fun SafeContext.updatePlaceInfo() {
        placeInfo = null

        val playerPos = player.pos
        var y = (floor(playerPos.y) - 0.00001).floorToInt()

        if (keepY && isInputting) {
            keepLevel?.let {
                if (it <= y) y = it
            }
        }

        val placePos = BlockPos(playerPos.x.floorToInt(), y, playerPos.z.floorToInt())

        placeInfo = buildPlaceInfo(placePos, 4, interactionConfig.reach + 1, player.eyePos, true, builderSideMask)
        keepLevel = placeInfo?.placedPos?.y ?: keepLevel
    }

    private fun SafeContext.rotate(info: PlaceInfo): Rotation? {
        val eye = player.eyePos
        val validHits = mutableSetOf<Vec3d>()

        val reach = interactionConfig.reach
        val reachSq = reach.pow(2)

        scanVisibleSurfaces(eye, Box(info.clickPos), setOf(info.clickSide), interactionConfig.resolution) { _, vec ->
            if (eye distSq vec > reachSq) return@scanVisibleSurfaces

            if (interactionConfig.useRayCast) {
                val newRotation = eye.rotationTo(vec)
                castRotation(newRotation, info) ?: return@scanVisibleSurfaces
            }

            validHits += vec
        }

        val moveYaw = calcMoveYaw(player.yaw).toFloat()
        val isDiagonal = moveYaw.octant.directions.size == 2

        validHits.minByOrNull {
            if (isDiagonal) {
                currentRotation dist eye.rotationTo(it)
            } else {
                distanceToEdge(info.clickPos, it)
            }
        } ?.let { closest ->
            return eye.rotationTo(closest)
        }

        return null
    }

    private fun SafeContext.distanceToEdge(pos: BlockPos, from: Vec3d = player.pos): Double {
        val x = player.pos.x.coerceIn(pos.x.toDouble(), pos.x.toDouble() + 1)
        val z = player.pos.z.coerceIn(pos.z.toDouble(), pos.z.toDouble() + 1)
        val to = Vec3d(x, from.y, z)

        return from dist to
    }

    private fun castRotation(rotation: Rotation, info: PlaceInfo): BlockHitResult? {
        val blockResult = rotation.rayCast(interactionConfig.reach)?.blockResult ?: return null
        if (blockResult.blockPos != info.clickPos || blockResult.side != info.clickSide) return null
        return blockResult
    }

    private fun SafeContext.getPause(info: PlaceInfo) = Pause.build {
        if (airOnly && player.input.jumping && airTicks <= minAirTicks) {
            pausePlacement()
        }

        if (isInputting) {
            val dist = distanceToEdge(info.clickPos)
            if (dist < minPlaceDist) {
                pausePlacement()
                pauseRotation()
            }
        }
    }

    private class Pause {
        var rotatePause = false; private set
        var placePause = false; private set

        fun pauseRotation() {
            rotatePause = true
        }

        fun pausePlacement() {
            placePause = true
        }

        companion object {
            fun build(block: Pause.() -> Unit) = Pause().apply(block)
        }
    }
}
