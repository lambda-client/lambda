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

import com.lambda.config.entries.Setting.Companion.onValueChange
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
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
    private const val GENERAL_TAB = "General"
    private const val ROTATION_TAB = "Rotation"

    private val yaw by setting("Yaw Mode", YawMode.Spin, "The mode used when setting the players yaw")
        .onValueChange { _, to -> if (to == YawMode.Custom) customYaw = player.yaw }
    private val spinMode by setting("Spin Mode", LeftRight.Right) { yaw == YawMode.Spin }
    private val sideMode by setting("Side Mode", LeftRight.Left) { yaw == YawMode.Sideways }
    private var customYaw: Float by setting("Custom Yaw", 0f, -179f..180f, 1f) { yaw == YawMode.Custom }
    private val yawPlayerMode by setting("Yaw Player mode", PlayerMode.Closest) { yaw == YawMode.Player }

    private val pitch by setting("Pitch Mode", PitchMode.UpAndDown, "The mode used when setting the players pitch")
        .onValueChange { _, to -> if (to == PitchMode.Custom) customPitch = player.pitch }
    private val upDownMode by setting("Vertical Mode", UpDown.Up) { pitch == PitchMode.Vertical }
    private var customPitch: Float by setting("Custom Pitch", 0f, -90f..90f, 1f) { pitch == PitchMode.Custom }
    private val pitchPlayerMode by setting("Pitch Player Mode", PlayerMode.Closest) { pitch == PitchMode.Player }

    private val yawSpeed by setting("Yaw Speed", 30, 1..90, 1, "Yaw rotation degrees per tick", "°") { yaw != YawMode.None }
    private val pitchSpeed by setting("Pitch Speed", 30, 1..90, 1, "Pitch rotation degrees per tick", "°") { pitch != PitchMode.None }

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
                    when (upDownMode) {
                        UpDown.Up -> -90f
                        UpDown.Down -> 90f
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

    private enum class UpDown {
        Up,
        Down
    }
}
