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

package com.lambda.module.modules.player

import com.lambda.config.groups.RotationSettings
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum
import com.lambda.util.math.distSq
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper.wrapDegrees
import kotlin.random.Random

@Suppress("unused")
object AntiAim : Module(
    name = "AntiAim",
    description = "Rotates the player using the given configs",
    tag = ModuleTag.MOVEMENT,
) {
    private enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Rotation("Rotation")
    }

    private val yaw by setting("Yaw Mode", YawMode.Spin, "The mode used when setting the players yaw").group(Group.General)
        .onValueChange { _, to ->
            if (to == YawMode.Custom) {
                // To bypass recursion issue
                setConfigCustomYaw(player.yaw)
            }
        }
    private val spinMode by setting("Spin Mode", LeftRight.Right) { yaw == YawMode.Spin }.group(Group.General)
    private val sideMode by setting("Side Mode", LeftRight.Left) { yaw == YawMode.Sideways }.group(Group.General)
    private var customYaw by setting("Custom Yaw", 0f, -179f..180f, 1f) { yaw == YawMode.Custom }.group(Group.General)
    private val yawPlayerMode by setting("Yaw Player mode", PlayerMode.Closest) { yaw == YawMode.Player }.group(Group.General)

    private val pitch by setting("Pitch Mode", PitchMode.UpAndDown, "The mode used when setting the players pitch").group(Group.General)
        .onValueChange { _, to ->
            if (to == PitchMode.Custom) {
                // To bypass recursion issue
                setConfigCustomPitch(player.pitch)
            }
        }
    private val verticalMode by setting("Vertical Mode", VerticalMode.Up) { pitch == PitchMode.Vertical }.group(Group.General)
    private var customPitch by setting("Custom Pitch", 0f, -90f..90f, 1f) { pitch == PitchMode.Custom }.group(Group.General)
    private val pitchPlayerMode by setting("Pitch Player Mode", PlayerMode.Closest) { pitch == PitchMode.Player }.group(Group.General)

    private val yawSpeed by setting("Yaw Speed", 30, 1..90, 1, "Yaw rotation degrees per tick", "°") { yaw != YawMode.None }.group(Group.General)
    private val pitchSpeed by setting("Pitch Speed", 30, 1..90, 1, "Pitch rotation degrees per tick", "°") { pitch != PitchMode.None }.group(Group.General)

    override val rotationConfig = RotationSettings(this, Group.Rotation)

    private var currentYaw = 0.0f
    private var currentPitch = 0.0f

    private var jitterRight = true
    private var jitterUp = true
    private var pitchingUp = true

    init {
        onEnable {
            currentYaw = player.yaw
            currentPitch = player.pitch
        }

        listen<TickEvent.Pre> {
            currentYaw = wrapDegrees(when (yaw) {
                YawMode.Spin -> when (spinMode) {
                    LeftRight.Left -> currentYaw - yawSpeed
                    LeftRight.Right -> currentYaw + yawSpeed
                }
                YawMode.Jitter -> {
                    val delta = Random.nextFloat() * (yawSpeed)
                    if (jitterRight) {
                        jitterRight = false
                        currentYaw + delta
                    } else {
                        jitterRight = true
                        currentYaw - delta
                    }
                }
                YawMode.Sideways -> when (sideMode) {
                    LeftRight.Left -> player.yaw - 90
                    LeftRight.Right -> player.yaw + 90
                }
                YawMode.Backwards -> player.yaw - 180
                YawMode.Custom -> customYaw
                YawMode.Player -> {
                    val target = getLookAtPlayer(yawPlayerMode)
                    target?.let {
                        player.eyePos.rotationTo(target.eyePos).yawF
                    } ?: player.yaw
                }
                YawMode.None -> player.yaw
            })

            currentPitch = when (pitch) {
                PitchMode.UpAndDown -> {
                    if (pitchingUp) {
                        (currentPitch - pitchSpeed).also {
                            if (currentPitch <= -90) {
                                pitchingUp = false
                            }
                        }
                    } else {
                        (currentPitch + pitchSpeed).also {
                            if (currentPitch >= 90) {
                                pitchingUp = true
                            }
                        }
                    }
                }
                PitchMode.Jitter -> {
                    val delta = Random.nextFloat() * (pitchSpeed)
                    if (jitterUp) {
                        jitterUp = false
                        currentPitch - delta
                    } else {
                        jitterUp = true
                        currentPitch + delta
                    }
                }
                PitchMode.Vertical -> {
                    when (verticalMode) {
                        VerticalMode.Up -> -90f
                        VerticalMode.Down -> 90f
                    }
                }
                PitchMode.Custom -> customPitch
                PitchMode.Player -> {
                    val target = getLookAtPlayer(pitchPlayerMode)
                    target?.let {
                        player.eyePos.rotationTo(target.eyePos).pitchF
                    } ?: player.pitch
                }
                PitchMode.None -> player.pitch
            }.coerceIn(-90f..90f)

            rotationRequest {
                if (yaw != YawMode.None) yaw(currentYaw)
                if (pitch != PitchMode.None) pitch(currentPitch)
            }.submit()
        }
    }

    private fun SafeContext.getLookAtPlayer(playerMode: PlayerMode): Entity? {
        val players = world.entities.filter { it is PlayerEntity && it != player }
        return when (playerMode) {
            PlayerMode.Closest -> players.minByOrNull { it.eyePos distSq player.eyePos }
            PlayerMode.Farthest -> players.maxByOrNull { it.eyePos distSq player.eyePos }
            PlayerMode.Random -> players.randomOrNull()
        }
    }

    private fun setConfigCustomYaw(newYaw: Float) {
        customYaw = newYaw
    }

    private fun setConfigCustomPitch(newPitch: Float) {
        customPitch = newPitch
    }

    private enum class YawMode {
        None,
        Spin,
        Jitter,
        Sideways,
        Backwards,
        Custom,
        Player
    }

    private enum class LeftRight {
        Left,
        Right
    }

    private enum class PlayerMode {
        Closest,
        Farthest,
        Random
    }

    private enum class PitchMode {
        None,
        UpAndDown,
        Jitter,
        Vertical,
        Custom,
        Player
    }

    private enum class VerticalMode {
        Up,
        Down
    }
}
