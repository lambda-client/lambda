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

import com.lambda.Lambda
import com.lambda.Lambda.mc
import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.context.SafeContext
import com.lambda.event.events.CameraEvent
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.module.Module
import com.lambda.module.modules.movement.BetterFirework.canOpenElytra
import com.lambda.module.modules.movement.BetterFirework.canTakeoff
import com.lambda.module.modules.movement.BetterFirework.startFirework
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.Timer
import com.lambda.util.extension.isElytraFlying
import com.lambda.util.math.interpolate
import com.lambda.util.player.MovementUtils.addSpeed
import com.lambda.util.player.SlotUtils.hotbarAndInventoryStacks
import com.lambda.util.player.SlotUtils.hotbarStacks
import com.lambda.util.player.hasFirework
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Vec3d
import kotlin.time.Duration.Companion.seconds

object ElytraFly : Module(
    name = "ElytraFly",
    description = "Allows you to fly with an elytra",
    tag = ModuleTag.MOVEMENT,
) {
    @JvmStatic val mode by setting("Mode", FlyMode.Bounce)
    private val inventory by setting("Inventory", true, "Allow using fireworks from the players inventory")

    //ToDo: Implement these commented out settings
    private val takeoff by setting("Takeoff", true, "Automatically jumps and initiates gliding") { mode == FlyMode.Bounce }
    private val autoPitch by setting("Auto Pitch", true, "Automatically pitches the players rotation down to bounce at faster speeds") { mode == FlyMode.Bounce }
    private val pitch by setting("Pitch", 80, 0..90, 1) { autoPitch && mode == FlyMode.Bounce }
    private val jump by setting("Jump", true, "Automatically jumps") { mode == FlyMode.Bounce }
    private val flagPause by setting("Flag Pause", 20, 0..100, 1, "How long to pause if the server flags you for a movement check") { mode == FlyMode.Bounce }
//    private val passObstacles by setting("Pass Obstacles", true, "Automatically paths around obstacles using baritone") { mode == FlyMode.Bounce }

    private val boostSpeed by setting("Boost", 0.00, 0.0..0.5, 0.005, description = "Speed to add when flying")
    private val rocketSpeed by setting("Rocket Speed", 0.0, 0.0..2.0, description = "Speed multiplier that the rocket gives you") { mode == FlyMode.Enhanced }

    private val mute by setting("Mute Elytra", false, "Mutes the elytra sound when gliding")

    private var jumpThisTick = false
    private var previouslyFlying: Boolean? = null
    private var glidePause = 0
    private var flipFlop = false
    private var lastDuration = 1.0
    private val fireworkTimer = Timer()

    private var stabilizeCamera = false
    private var prevPosition: Vec3d = Vec3d.ZERO
    private var position: Vec3d = Vec3d.ZERO
    private val flipFlopLerpPos: Vec3d
        get() {
            var tickProgress = mc.gameRenderer.camera.lastTickProgress / 2
            if (!flipFlop) tickProgress += 0.5f
            return prevPosition.interpolate(tickProgress, position)
        }

    init {
        setDefaultAutomationConfig {
            applyEdits {
                hideAllGroupsExcept(inventoryConfig)
            }
        }

        onEnable {
            position = player.eyePos
            prevPosition = position
        }

        listen<CameraEvent.CameraPosition> {
            if (mode != FlyMode.GrimControl || !stabilizeCamera) return@listen
            Lambda.mc.gameRenderer.apply {
                camera.setPos(flipFlopLerpPos.x, flipFlopLerpPos.y, flipFlopLerpPos.z)
            }
        }

        listen<TickEvent.Pre> {
            if (mode == FlyMode.GrimControl) onTickGrimControl()
            else if (mode == FlyMode.Bounce) onTickBounce()
        }

        listen<TickEvent.Post> {
            if (glidePause > 0) glidePause--
        }

        listen<PacketEvent.Receive.Post> { event ->
            if (event.packet !is PlayerPositionLookS2CPacket) return@listen
            if (mode == FlyMode.Bounce && player.isGliding) {
                glidePause = flagPause
            }
        }

        listen<MovementEvent.InputUpdate> { event ->
            if (mode == FlyMode.Bounce && ((player.isGliding && jump) || jumpThisTick)) {
                event.input.jump()
                jumpThisTick = false
            }
        }

        listen<MovementEvent.Player.Pre> {
            if (player.isElytraFlying && !player.isUsingItem) {
                addSpeed(boostSpeed)
            }
        }

        listen<ClientEvent.Sound> { event ->
            if (!mute) return@listen
            if (event.sound.id != SoundEvents.ITEM_ELYTRA_FLYING.id) return@listen
            event.cancel()
        }
    }

    private fun SafeContext.onTickGrimControl() {
        if (!player.isGliding) return
        if (fireworkTimer.timePassed(lastDuration.seconds)) {
            findFirework()?.let {
                lastDuration = (it.get(DataComponentTypes.FIREWORKS)?.flightDuration ?: 1) * 0.5 + 0.5
                startFirework(inventory)
                fireworkTimer.reset()
            }
        }

        var vec = Vec3d.ZERO
        val yaw = player.yaw
        if (flipFlop) {
            if (mc.options.forwardKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw))
            if (mc.options.backKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw + 180f))
            if (mc.options.leftKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw - 90f))
            if (mc.options.rightKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw + 90f))
            if (mc.options.jumpKey.isPressed) vec = vec.add(Vec3d(0.0, 1.0, 0.0))
            if (mc.options.sneakKey.isPressed) vec = vec.add(Vec3d(0.0, -1.0, 0.0))
        }
        if (vec.lengthSquared() < 1e-2 && player.hasFirework) {
            if (!stabilizeCamera) {
                stabilizeCamera = true
                position = player.eyePos
                prevPosition = position
                flipFlop = true
            }
            if (flipFlop) {
                flipFlop = false
                rotationRequest { rotation(0f, 0f) }
            } else {
                flipFlop = true
                prevPosition = position
                position = player.eyePos
                rotationRequest { rotation(180f, 0f) }
            }
        } else {
            val rot = vec.yawAndPitch
            stabilizeCamera = false
            rotationRequest { rotation(rot.y, rot.x) }
        }.submit()
    }

    private fun SafeContext.findFirework(): ItemStack? {
        val stack = selectStack(count = 1) { isItem(Items.FIREWORK_ROCKET) }
        return stack.bestItemMatch(player.hotbarStacks) ?: if (inventory) stack.bestItemMatch(player.hotbarAndInventoryStacks) else null
    }

    private fun SafeContext.onTickBounce() {
        if (autoPitch) rotationRequest { pitch(pitch.toFloat()) }.submit()

        if (!player.isGliding) {
            if (takeoff && player.canTakeoff) {
                if (player.canOpenElytra) {
                    player.startGliding()
                    startFlyPacket()
                } else jumpThisTick = true
            }
            return
        }

        startFlyPacket()
    }

    private fun SafeContext.startFlyPacket() =
        connection.sendPacket(ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))

    @JvmStatic
    fun isGliding(): Boolean? = runSafe {
        val original: Boolean = player.getFlag(Entity.GLIDING_FLAG_INDEX)
        if (previouslyFlying == null) {
            previouslyFlying = original
            return@runSafe original
        }
        return if (isEnabled && mode == FlyMode.Bounce && previouslyFlying == true && glidePause <= 0) true
        else {
            previouslyFlying = original
            original
        }
    }

    @JvmStatic
    fun boostRocket() = runSafe {
        val vec = player.rotationVector
        val velocity = player.velocity

        val d = 1.5 * if (mode == FlyMode.Enhanced) rocketSpeed else 1.0
        val e = 0.1 * if (mode == FlyMode.Enhanced) rocketSpeed else 1.0

        player.velocity = velocity.add(
            vec.x * e + (vec.x * d - velocity.x) * 0.5,
            vec.y * e + (vec.y * d - velocity.y) * 0.5,
            vec.z * e + (vec.z * d - velocity.z) * 0.5
        )
    }

    enum class FlyMode {
        Bounce,
        Enhanced,
        GrimControl
    }
}
