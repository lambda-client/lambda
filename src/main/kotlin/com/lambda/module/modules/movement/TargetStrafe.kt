/*
 * Copyright 2026 Lambda
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

package com.lambda.module.modules.movement

import com.lambda.context.SafeContext
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.lambda.module.Module
import com.lambda.module.modules.combat.KillAura
import com.lambda.module.tag.ModuleTag
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.util.math.Vec3d
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TargetStrafe : Module(
    name = "TargetStrafe",
    description = "Strafes around a target in a circle. The default settings work great for old NCP.",
    tag = ModuleTag.MOVEMENT,
) {
    private val autoJump by setting("AutoJump", true)
    private val distanceSetting by setting("PreferredDistance", 1f, 0f..6f, 0.1f)
    private val maxDistance by setting("MaxDistance", 10f, 1f..32f, 0.5f)
    private val turnAmount by setting("TurnAmount", 5f, 1f..90f, 0.5f)
    private val hSpeed by setting("HSpeed", 0.2873f, 0.001f..10.0f, 0.0001f)

    private val needsAura by setting("NeedsAura", true)
    private val antiStuck by setting("AntiStuck", true)

    // private val renderCircle by setting("RenderCircle", true)
    // private val renderThickness by setting("RenderThickness", 2f, 0.5f..8f, 0.5f, visibility = { renderCircle })

    private var direction = 1

    private var currentDistance = 0.toDouble()
    private var currentTargetVec: Vec3d? = null

    private var strafing = false

    init {
        listen<TickEvent.Post> {

            if (strafing && autoJump && player.isOnGround) {
                player.jump()
            }
        }

        listen<MovementEvent.Player.Pre> { event ->
            if (mc.player!!.horizontalCollision && antiStuck) {
                switchDirection()
            }
            val strafe = canStrafe()

            if (strafe) {
                val rotations = KillAura.target?.let { it1 -> player.eyePos!!.rotationTo(it1.pos) } ?: return@listen
                KillAura.target?.let { it1 -> doStrafeAtSpeed(event, rotations.yawF, it1.pos) }
                val r: Rotation

                strafing = true
            } else {
                strafing = false
            }
        }

        // listen<RenderEvent.RenderWorld> {
        //     if (strafing && renderCircle) {
        //         if (currentTargetVec == null) {
        //             return@listen
        //         }
        //         /*
        //         todo: make rendering work
        //         drawCircle(currentTargetVec!!, distanceSetting.toDouble(), distanceColor, 360)
        //         drawCircle(currentTargetVec!!, currentDistance, playerDistanceColor, 360)
        //         */
        //     }
        // }

    }

    private fun SafeContext.doStrafeAtSpeed(event: MovementEvent.Player.Pre, rotation: Float, target: Vec3d): Boolean {


        var playerSpeed = hSpeed
        var jumpVelocity = 0.405

        var rotationYaw = rotation + (90f * direction)


        val disX = player.pos.x - target.x
        val disZ = player.pos.z - target.z

        val distance = sqrt(disX * disX + disZ * disZ)

        if (distance < maxDistance) {
            if (distance > distanceSetting) {
                rotationYaw -= turnAmount * direction
            } else if (distance < distanceSetting) {
                rotationYaw += turnAmount * direction
            }
        } else {
            rotationYaw = rotation
        }

        currentDistance = distance


        // jump boost

        val jumpboost = player.getStatusEffect(StatusEffects.JUMP_BOOST)
        if (jumpboost != null) {
            jumpVelocity *= jumpboost.amplifier
        }

        // speed

        val speed = player.getStatusEffect(StatusEffects.SPEED)
        if (speed != null) {

            playerSpeed *= 1.0f + 0.2f * (speed.amplifier + 1)
        }

        event.movement = Vec3d(playerSpeed * cos(Math.toRadians((rotationYaw + 90.0f).toDouble())), event.movement.y, playerSpeed * sin(Math.toRadians((rotationYaw + 90.0f).toDouble())))


        return false
    }

    private fun canStrafe(): Boolean {
        if ((KillAura.isEnabled || !needsAura) && KillAura.target != null) {
            return true
        }
        return false
    }

    private fun switchDirection() {
        direction = -direction
    }

    // private fun drawCircle(center: Vec3d, radius: Double, color: Color, precision: Int) {


    //     val linesToDraw = ArrayList<Pair<Vec3d, Vec3d>>()

    //     val magic = precision / 360

    //     var lastPos = center.add(0.0, 0.0, radius)


    //     for (i in 0..precision) {

    //         val yaw = i * magic

    //         val x = radius * cos(Math.toRadians((yaw + 90.0f).toDouble()))
    //         val z = radius * sin(Math.toRadians((yaw + 90.0f).toDouble()))

    //         val newPos = center.add(x, 0.0, z)
    //         linesToDraw.add(Pair(lastPos, newPos))
    //         lastPos = newPos
    //     }

    //     val tessellator: Tessellator?
    //     try {
    //         tessellator = Tessellator.getInstance()
    //     } catch (e: NoSuchFieldError) {
    //         e.printStackTrace()
    //         return
    //     }
    //     val buffer: BufferBuilder = tessellator.buffer

    //     GL11.glLineWidth(renderThickness)
    //     //GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_SMOOTH)
    //     GlStateManager.disableDepth()

    //     buffer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR)
    //     for (pair in linesToDraw) {
    //         try {
    //             buffer.pos(pair.first.x, center.y, pair.first.z).color(color.r, color.g, color.b, color.a).endVertex()
    //             buffer.pos(pair.second.x, center.y, pair.second.z).color(color.r, color.g, color.b, color.a).endVertex()
    //         } catch (e: NullPointerException) {
    //         }
    //     }
    //     tessellator.draw()


    //     GlStateManager.enableDepth()
    //     GL11.glLineWidth(1f)
    // }
}
