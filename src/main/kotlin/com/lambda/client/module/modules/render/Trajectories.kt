package com.lambda.client.module.modules.render

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.player.FastUse
import com.lambda.client.util.EntityUtils
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.GeometryMasks
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.LambdaTessellator
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.material.Material
import net.minecraft.client.renderer.ActiveRenderInfo
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHandSide
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent
import org.lwjgl.opengl.GL11.GL_LINE_STRIP
import org.lwjgl.opengl.GL11.glLineWidth
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object Trajectories : Module(
    name = "Trajectories",
    description = "Draws lines to where trajectories are going to fall",
    category = Category.RENDER
) {
    private val showEntity by setting("Show Entity", true)
    private val showBlock by setting("Show Block", false)
    private val color by setting("Color", ColorHolder(255, 255, 255))
    private val aFilled by setting("Filled Alpha", 127, 0..255, 1)
    private val aOutline by setting("Outline Alpha", 255, 0..255, 1)
    private val thickness by setting("Thickness", 2f, 0.25f..5f, 0.25f)

    private var prevItemUseCount = 0

    init {
        safeListener<LivingEntityUseItemEvent.Tick> {
            prevItemUseCount = player.itemInUseCount
        }

        safeListener<RenderWorldEvent> {
            val type = getThrowingType(player.heldItemMainhand) ?: getThrowingType(player.heldItemOffhand)
            ?: return@safeListener
            val path = ArrayList<Vec3d>()
            val flightPath = FlightPath(type, this)
            path.add(flightPath.position)
            while (flightPath.collision == null && path.size < 500) {
                flightPath.simulateTick()
                path.add(flightPath.position)
            }

            val offset = getPathOffset()
            val buffer = LambdaTessellator.buffer
            glLineWidth(thickness)
            GlStateUtils.depth(false)
            LambdaTessellator.begin(GL_LINE_STRIP)
            for ((index, pos) in path.withIndex()) {
                val scale = ((path.size - 1) - index) * (1.0 / (path.size - 1))
                val offsetPos = pos.add(offset.scale(scale))
                buffer.pos(offsetPos.x, offsetPos.y, offsetPos.z).color(color.r, color.g, color.b, aOutline)
                    .endVertex()
            }
            LambdaTessellator.render()

            flightPath.collision?.let {
                val box = (when (it.sideHit.axis) {
                    EnumFacing.Axis.X -> AxisAlignedBB(0.0, -0.25, -0.25, 0.0, 0.25, 0.25)
                    EnumFacing.Axis.Y -> AxisAlignedBB(-0.25, 0.0, -0.25, 0.25, 0.0, 0.25)
                    else -> AxisAlignedBB(-0.25, -0.25, 0.0, 0.25, 0.25, 0.0)
                }).offset(it.hitVec)

                val quadSide = GeometryMasks.FACEMAP[it.sideHit]!!
                val renderer = ESPRenderer()
                renderer.aFilled = aFilled
                renderer.aOutline = aOutline
                renderer.thickness = thickness
                renderer.add(box, color, quadSide)
                renderer.render(true)

                renderer.aFilled = 0
                if (showEntity && it.entityHit != null) renderer.add(it.entityHit, color)
                else if (showBlock) renderer.add(it.blockPos, color)
                renderer.render(true)
            }

            glLineWidth(1f)
            GlStateUtils.depth(true)
        }
    }

    private fun SafeClientEvent.getPathOffset(): Vec3d {
        if (mc.gameSettings.thirdPersonView != 0) return Vec3d.ZERO
        var multiplier = if (getThrowingType(player.heldItemMainhand) != null) 1.0 else -1.0
        if (mc.gameSettings.mainHand != EnumHandSide.RIGHT) multiplier *= -1.0
        val eyePos = player.getPositionEyes(LambdaTessellator.pTicks())
        val camPos = EntityUtils.getInterpolatedPos(player, LambdaTessellator.pTicks())
            .add(ActiveRenderInfo.getCameraPosition())
        val yawRad = Math.toRadians(player.rotationYaw.toDouble())
        val pitchRad = Math.toRadians(player.rotationPitch.toDouble())
        val offset = Vec3d(
            cos(yawRad) * 0.2 + sin(pitchRad) * -sin(yawRad) * 0.15,
            0.0,
            sin(yawRad) * 0.2 + sin(pitchRad) * cos(yawRad) * 0.15
        )
        return camPos.subtract(offset.scale(multiplier).add(0.0, cos(pitchRad) * 0.1, 0.0)).subtract(eyePos)
    }

    private class FlightPath(val throwingType: ThrowingType, val event: SafeClientEvent) {
        private val halfSize = if (throwingType == ThrowingType.BOW) 0.25 else 0.125

        var position: Vec3d = mc.player?.getPositionEyes(LambdaTessellator.pTicks()) ?: Vec3d.ZERO
            private set
        private var motion = Vec3d.ZERO
        private var boundingBox: AxisAlignedBB = AxisAlignedBB(
            position.x - halfSize,
            position.y - halfSize,
            position.z - halfSize,
            position.x + halfSize,
            position.y + halfSize,
            position.z + halfSize
        )
        var collision: RayTraceResult? = null
            private set

        fun simulateTick() {
            if (position.y <= -9.11) { // Sanity check to see if we've gone below the world (if we have we will never collide)
                collision = RayTraceResult(position, EnumFacing.UP)
                return
            }

            val nextPos = position.add(motion) // Get the next positions in the world
            collision =
                event.world.rayTraceBlocks(position, nextPos, false, true, false) // Check if we've collided with a block

            if (collision == null) {
                val resultList = ArrayList<RayTraceResult>()
                for (entity in event.world.loadedEntityList) {
                    if (!entity.canBeCollidedWith()) continue
                    if (entity == event.player) continue
                    val box = entity.entityBoundingBox.grow(0.30000001192092896) ?: continue
                    val rayTraceResult = box.calculateIntercept(position, nextPos) ?: continue
                    rayTraceResult.entityHit = entity
                    resultList.add(rayTraceResult)
                }
                collision = resultList.minByOrNull { it.hitVec.distanceTo(position) }
            }

            collision?.let {
                setPosition(it.hitVec) // Update position
                return
            }

            val motionModifier = if (mc.player.entityWorld.isMaterialInBB(
                    boundingBox,
                    Material.WATER
                )
            ) if (throwingType == ThrowingType.BOW) 0.6 else 0.8
            else 0.99

            setPosition(nextPos) // Update the position and bounding box

            motion = motion.scale(motionModifier) // Slowly decay the velocity of the path
            motion = motion.subtract(0.0, throwingType.gravity, 0.0) // Drop the motionY by the constant gravity

        }

        private fun setPosition(posIn: Vec3d) {
            boundingBox = boundingBox.offset(posIn.subtract(position))
            position = posIn
        }

        private fun SafeClientEvent.getInterpolatedCharge() =
            prevItemUseCount.toDouble() + (player.itemInUseCount.toDouble() - prevItemUseCount.toDouble()) * LambdaTessellator.pTicks()
                .toDouble()

        init {
            var pitch = event.player.rotationPitch.toDouble()
            if (throwingType == ThrowingType.EXPERIENCE || throwingType == ThrowingType.POTION) pitch -= 20.0
            val yawRad = Math.toRadians(event.player.rotationYaw.toDouble())
            val pitchRad = Math.toRadians(pitch)
            val cosPitch = cos(pitchRad)

            val initVelocity = if (throwingType == ThrowingType.BOW) {
                val itemUseCount = FastUse.bowCharge
                    ?: if (event.player.isHandActive) event.getInterpolatedCharge() else 0.0
                val useDuration = (72000 - itemUseCount) / 20.0
                val velocity = (useDuration.pow(2) + useDuration * 2.0) / 3.0
                min(velocity, 1.0) * throwingType.velocity
            } else throwingType.velocity

            motion = Vec3d(-sin(yawRad) * cosPitch, -sin(pitchRad), cos(yawRad) * cosPitch).scale(initVelocity)
        }
    }

    private fun getThrowingType(itemStack: ItemStack?): ThrowingType? = when (itemStack?.item) {
        Items.BOW -> ThrowingType.BOW
        Items.EXPERIENCE_BOTTLE -> ThrowingType.EXPERIENCE
        Items.SPLASH_POTION, Items.LINGERING_POTION -> ThrowingType.POTION
        Items.SNOWBALL, Items.EGG, Items.ENDER_PEARL -> ThrowingType.OTHER
        else -> null
    }

    enum class ThrowingType(val gravity: Double, val velocity: Double) {
        BOW(0.05000000074505806, 3.0),
        EXPERIENCE(0.07, 0.7),
        POTION(0.05, 0.5),
        OTHER(0.03, 1.5)
    }
}