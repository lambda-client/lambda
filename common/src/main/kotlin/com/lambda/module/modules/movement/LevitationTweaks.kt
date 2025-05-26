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

package com.lambda.module.modules.movement

import com.lambda.context.SafeContext
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info
import com.lambda.util.KeyboardUtils.isKeyPressed
import com.lambda.util.NamedEnum
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.player.MovementUtils.isInputting
import com.lambda.util.player.MovementUtils.motionY
import com.lambda.util.player.MovementUtils.setSpeed
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects.LEVITATION
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL
import java.time.Duration
import java.time.LocalDateTime

object LevitationTweaks : Module(
    name = "LevitationTweaks",
    description = "Abuse the levitation effect",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    private val mode by setting("Mode", Mode.UNCP)

    // REMOVE
    private val restore by setting("Restore", true, description = "Restore levitation on module disable") { mode == Mode.REMOVE }

    // UNCP
    private val constantUpFactor by setting(
        "Idle Up Speed", unit = "%", description = "Permanent upwards motion", defaultValue = 2.0, range = 0.0..100.0
    ) { mode == Mode.UNCP}
    private val control by setting("Control", true, ) { mode == Mode.UNCP}
    private val strafeBoost by setting("Strafe Boost", true, ) { mode == Mode.UNCP && control }
    private val strafeBoostSpeed by setting(
        "Boost Speed", unit = "%", defaultValue = 100, range = 0..120
    ) { mode == Mode.UNCP && control && strafeBoost}
    private val timer by setting(
        "Timer", true
    ) { mode == Mode.UNCP && control && strafeBoost}
    private val timerBoost by setting("Timer Boost", 1.08, 1.0..1.2, 0.01) { mode == Mode.UNCP && control  && timer}

    private val controlDownSpeed by setting(
        "Control Down Speed", unit = "%", defaultValue = 100, range = 0..300
    ) { mode == Mode.UNCP && control}
    private val controlUpSpeed by setting(
        "Control Up Speed", unit = "%", defaultValue = 100, range = 0..140
    ) { mode == Mode.UNCP && control}



    private enum class Mode(override val displayName: String) : NamedEnum {
        REMOVE("Remove"), UNCP("NCP New")
    }

    private var capturedEffect: StatusEffectInstance? = null
    private var wearOffTime: LocalDateTime? = null
    private var canMove = false

    init {
        // Checks if levitation is applied.
        fun SafeContext.checkForLevitationEffect(): StatusEffectInstance? {
            return player.activeStatusEffects.entries.firstOrNull { it.value.effectType == LEVITATION }?.value
        }

        onDisable {
            /* RESTORE EFFECT AFTER EARLY DISABLE */
            val now = LocalDateTime.now()
            if (mode != Mode.UNCP && restore && capturedEffect != null && now.isBefore(wearOffTime)) {
                //                info("Reapplying effect: $capturedEffect")
                val durationTicks = wearOffTime?.let { Duration.between(now, it).seconds.toInt() * 20 } ?: 0
                //TODO: refactor this ugly time difference calculation
                val newEffect = StatusEffectInstance(LEVITATION, durationTicks, capturedEffect!!.amplifier)
                info("Reapplied levitation for ${durationTicks / 20} seconds")
                player.addStatusEffect(newEffect)
            }

            canMove = false
            capturedEffect = null // Reset on disable
        }

        onEnable {
            canMove = false
            capturedEffect = null // Reset when enabled again
        }


        listen<TickEvent.Post> {
            // Capture effect to apply magic on later
            if (capturedEffect == null) {
                capturedEffect = checkForLevitationEffect()
                if (capturedEffect == null) return@listen // Still not available, wait for next tick
                wearOffTime = LocalDateTime.now().plusSeconds(capturedEffect!!.duration.toLong() / 20)
            }

            if (LocalDateTime.now().isAfter(wearOffTime)) {
                info("Effect wore off.")
                capturedEffect == null
                disable()
            }

            when (mode) {
                Mode.REMOVE -> {
                    if (checkForLevitationEffect() != null) player.removeStatusEffect(LEVITATION)
                }
                Mode.UNCP -> {
                    if (checkForLevitationEffect() != null) canMove = true
                }
            }
        }

        listen<MovementEvent.Player.Pre> { event ->
            info(event.toString())
            if (mode != Mode.UNCP || !canMove) return@listen

            player.motionY = when {
                player.isSneaking && control -> { // BOOST DOWN
//                    TODO: find out how to initiate downwards movement without a flag
/*                    player.motionX *= 0.5
                    player.motionZ *= 0.5*/
                    -(0.4 * (controlDownSpeed / 100))
                }
                player.input.jumping && control -> (0.12 * (controlUpSpeed / 100)) // BOOST UP
                else -> 0.06 * (constantUpFactor / 100) // idle motion up
            }

            if (isKeyPressed(GLFW_KEY_LEFT_CONTROL) && strafeBoost && control) {setSpeed(Speed.NCP_BASE_SPEED * isInputting.toInt() * (strafeBoostSpeed/100))}
        }

        listen<ClientEvent.TimerUpdate> {
            if (mode != Mode.UNCP) return@listen
            if (!isKeyPressed(GLFW_KEY_LEFT_CONTROL) || !timer || !canMove || !strafeBoost) return@listen
            it.speed = timerBoost
        }
    }

}