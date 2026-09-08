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

import com.lambda.config.blocks.WorldLineSettings
import com.lambda.config.forEachSetting
import com.lambda.config.hideAllExcept
import com.lambda.config.withEdits
import com.lambda.context.SafeContext
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.interaction.manager.managers.rotating.Rotation.Companion.rotationTo
import com.lambda.module.Module
import com.lambda.module.modules.combat.KillAura
import com.lambda.module.ModuleTag
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.util.math.Vec3d
import java.awt.Color
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

    private val renderCircle by setting("RenderCircle", true)
    private val renderCircleColor by setting("RenderCircleColor", Color(255, 255, 255, 100)) { renderCircle }
    private val renderThickness by configBlock(WorldLineSettings(this))
         .withEdits {
             hideAllExcept(
                 ::distanceScaling,
                 ::worldWidthSetting,
                 ::screenWidthSetting
             )
             forEachSetting {
                 visibility { old -> { old() && renderCircle } }
             }
         }

    private var direction = 1

    private var currentDistance = 0.0
    private var currentTargetVec: Vec3d? = null

    private var strafing = false

    init {
        listen<TickEvent.Post> {
            if (strafing && autoJump && player.isOnGround) {
                player.jump()
            }
        }

        listen<MovementEvent.Player.Pre> { event ->
            if (player.horizontalCollision && antiStuck) {
                switchDirection()
            }
            if (canStrafe()) {
                val rotations = KillAura.target?.let { it1 -> player.eyePos?.rotationTo(it1.pos) } ?: return@listen
                KillAura.target?.let { it1 -> doStrafeAtSpeed(event, rotations.yawF, it1.pos) }
                currentTargetVec = KillAura.target?.pos

                strafing = true
            } else {
                strafing = false
            }
        }

         immediateRenderer("TargetStrafe immediate renderer") {
             if (strafing && renderCircle) {
                 circleLine(
                     currentTargetVec ?: return@immediateRenderer,
                     distanceSetting.toDouble(),
                     renderCircleColor,
                     renderThickness.width,
                     segments = 64
                 )
             }
         }
    }

    private fun SafeContext.doStrafeAtSpeed(event: MovementEvent.Player.Pre, rotation: Float, target: Vec3d): Boolean {
        var playerSpeed = hSpeed
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

        // speed
        val speed = player.getStatusEffect(StatusEffects.SPEED)
        if (speed != null) {

            playerSpeed *= 1.0f + 0.2f * (speed.amplifier + 1)
        }

        event.movement = Vec3d(playerSpeed * cos(Math.toRadians((rotationYaw + 90.0f).toDouble())), event.movement.y, playerSpeed * sin(Math.toRadians((rotationYaw + 90.0f).toDouble())))
        return false
    }

    private fun canStrafe(): Boolean {
        return (KillAura.isEnabled || !needsAura) && KillAura.target != null
    }

    private fun switchDirection() {
        direction = -direction
    }
}
