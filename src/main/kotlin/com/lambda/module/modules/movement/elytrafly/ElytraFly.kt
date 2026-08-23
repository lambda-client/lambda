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

package com.lambda.module.modules.movement.elytrafly

import com.lambda.config.Tab
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.editSetting
import com.lambda.config.entries.Setting.Companion.onValueChange
import com.lambda.config.forEachSetting
import com.lambda.config.hideAllExcept
import com.lambda.config.withEdits
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.module.Module
import com.lambda.module.modules.movement.elytrafly.modes.BounceElytraFly
import com.lambda.module.modules.movement.elytrafly.modes.GeneralElytraFly
import com.lambda.module.modules.movement.elytrafly.modes.GrimControlElytraFly
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.NamedEnum
import com.lambda.util.Timer
import com.lambda.util.extension.isElytraFlying
import com.lambda.util.extension.prevPos
import com.lambda.util.player.MovementUtils.addSpeed
import com.lambda.util.player.PlayerUtils.hasFirework
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.MovementType
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

object ElytraFly : Module(
    name = "ElytraFly",
    description = "Modifies elytra functionality to allow for more control",
    tag = ModuleTag.MOVEMENT,
) {
    @JvmStatic val mode by setting("Fly Mode", FlyMode.Bounce)
        .onValueChange { from, to ->
            from.elytraFly.onDisableListeners.forEach { it() }
            to.elytraFly.onEnableListeners.forEach { it() }
        }

    enum class RocketBoostMode(override val displayName: String): NamedEnum {
        Standard("Standard"),
        Grim("Grim")
    }

    private val boostSpeed by setting("Boost", 0.0, 0.0..0.5, 0.005, description = "Speed to add when flying")
    @JvmStatic val rocketBoostMode by setting("Rocket Boost Mode", RocketBoostMode.Grim)
    private val rocketSpeed by setting("Rocket Speed", 1.0, 0.0..2.0, 0.01, description = "Speed multiplier that the rocket gives you") { rocketBoostMode == RocketBoostMode.Standard }
    private val maxGrimBoost by setting("Max Grim Boost", 3.0, 0.0..3.0, 0.01, description = "Maximum additional speed the firework boost can add on top of the base rocket speed") { rocketBoostMode == RocketBoostMode.Grim }
    private val safetyMargin by setting("Safety Margin", 0.2, 0.0..2.0, 0.01, "The time (in seconds) to shorten the firework use delay to account for ping variation", "s")
    private val mute by setting("Mute Elytra", false, "Mutes the elytra sound when gliding")
    @JvmStatic val fakeFly by setting("Fake Fly", false, "Rapidly swaps the chestplate and elytra to give the appearance the player is flying without an elytra. May also reduce durability loss")

    private const val BOUNCE_TAB = "Bounce"
//    private const val CONTROL_TAB = "Control"
    private const val GRIM_CONTROL_TAB = "Grim Control"
//    private const val PACKET_TAB = "Packet"
    private const val GENERAL_TAB = "None"

    private const val GRIM_ROCKET_BOOST_RESCALE = 1.65

    @Tab(GENERAL_TAB) @JvmStatic val generalMode by configBlock(GeneralElytraFly(this))
        .withEdits { forEachSetting { visibility { old -> { old() && mode == FlyMode.General } } } }
    @Tab(BOUNCE_TAB) @JvmStatic val bounceMode by configBlock(BounceElytraFly(this))
        .withEdits { forEachSetting { visibility { old -> { old() && mode == FlyMode.Bounce } } } }
    @Tab(GRIM_CONTROL_TAB) @JvmStatic val grimControlMode by configBlock(GrimControlElytraFly(this))
        .withEdits { forEachSetting { visibility { old -> { old() && mode == FlyMode.GrimControl } } } }
//    @Tab(CONTROL_TAB) @JvmStatic val controlMode by configBlock(ControlElytraFly(this))
//    @Tab(PACKET_TAB) @JvmStatic val packetMode by configBlock(PacketElytraFly(this))

    // Last sent movement packet state, used to rebuild Grim's firework prediction box.
    private var lastMovementIncludedPosition = true
    private var prevVelocity = Vec3d.ZERO
    private var targetVelocity: Vec3d? = null

    // Shared firework tracking. Vanilla can briefly drop the firework entity for a tick,
    // so once a firework is used, we hold this true until its flight duration runs out.
    private val fireworkTimer = Timer()
    private var lastFireworkDuration = -1.0
    @JvmStatic var hasFirework = false

    init {
        setDefaultAutomationConfig()
            .withEdits {
                hotbarConfig::tickStageMask.editSetting { defaultValue(TickEvent.ALL_STAGES.toMutableList()) }
	            hideAllExcept(::inventoryConfig, ::rotationConfig)
            }

        onEnable {
            mode.elytraFly.onEnableListeners.forEach { it() }
            prevVelocity = Vec3d.ZERO
            targetVelocity = null
            hasFirework = false
            fireworkTimer.reset()
            lastFireworkDuration = -1.0
        }
        onDisable { mode.elytraFly.onDisableListeners.forEach { it() } }

        listen<TickEvent.Pre>(priority = { 2 }) {
            if (!player.isGliding) {
                hasFirework = false
                return@listen
            }
            if (!withinFireworkTimeframe()) hasFirework = false
            hasFirework = hasFirework || player.hasFirework
        }

        listen<PacketEvent.Send.Pre> { event ->
            val packet = event.packet
            if (packet !is PlayerInteractItemC2SPacket) return@listen
            val stack = player.getStackInHand(packet.hand)
            if (stack.item != Items.FIREWORK_ROCKET) return@listen
            fireworkTimer.reset()
            lastFireworkDuration = (stack.get(DataComponentTypes.FIREWORKS)?.flightDuration ?: 1) * 0.5 + 0.5
        }

        listen<TickEvent.Pre> {
            if (rocketBoostMode != RocketBoostMode.Grim) return@listen
            if (!player.isGliding || !hasFirework || mode.elytraFly.pausingMovement()) return@listen

            val aiming = Vec3d.fromPolar(
                RotationManager.movementPitch ?: player.pitch,
                RotationManager.movementYaw ?: player.yaw,
            )
            val bounds =
                GrimFireworkBox.getFireworkBounds(
                    prevVelocity,
                    aiming,
                    lastMovementIncludedPosition,
                    GRIM_ROCKET_BOOST_RESCALE
                ) ?: return@listen
            val claimed = farthestPointInBox(bounds, aiming)?.let { limitSpeed(it) } ?: return@listen

            targetVelocity = claimed
            player.velocity = claimed
        }

        listen<PacketEvent.Send.Post> { event ->
            val packet = event.packet as? PlayerMoveC2SPacket ?: return@listen
            if (packet.changesPosition()) {
                val prevPos = player.prevPos
                val next = Vec3d(packet.getX(prevPos.x), packet.getY(prevPos.y), packet.getZ(prevPos.z))
                val delta = next.subtract(prevPos)
                prevVelocity = delta
            }
            lastMovementIncludedPosition = packet.changesPosition()
        }

        listen<MovementEvent.Entity.Pre> { event ->
            if (event.entity != player || !player.isGliding) return@listen
            val velocity = targetVelocity ?: return@listen
            targetVelocity = null
            player.velocity = velocity
            player.move(MovementType.SELF, velocity)
            event.cancel()
        }

        listen<PacketEvent.Receive.Pre> { event ->
            if (event.packet !is PlayerPositionLookS2CPacket) return@listen
            mode.elytraFly.onFlagListeners.forEach { it() }
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

    @JvmStatic
    fun getFireworkTargetVelocity(rotPitch: Float, rotYaw: Float): Vec3d {
        val vec = Vec3d.fromPolar(rotPitch, rotYaw)
        val d = 1.5 * rocketSpeed
        val e = 0.1 * rocketSpeed
        return vec.multiply(d + e * 2)
    }

    @JvmStatic
    fun boostRocket() =
        runSafe {
	        val vec = player.rotationVector
	        val velocity = player.velocity

	        val d = 1.5 * rocketSpeed
	        val e = 0.1 * rocketSpeed

	        player.velocity = velocity.add(
	    	    vec.x * e + (vec.x * d - velocity.x) * 0.5,
	    	    vec.y * e + (vec.y * d - velocity.y) * 0.5,
	    	    vec.z * e + (vec.z * d - velocity.z) * 0.5
	        )
        }

    fun withinFireworkTimeframe() = !fireworkTimer.timePassed((lastFireworkDuration - safetyMargin).seconds)

    private fun farthestPointInBox(bounds: DoubleArray, aim: Vec3d): Vec3d? {
        val center = Vec3d(
            (bounds[0] + bounds[3]) / 2.0,
            (bounds[1] + bounds[4]) / 2.0,
            (bounds[2] + bounds[5]) / 2.0,
        )
        val direction = aim.normalize()
        var near = Double.NEGATIVE_INFINITY
        var far = Double.POSITIVE_INFINITY
	    repeat(3) { axis ->
		    val origin = if (axis == 0) center.x else if (axis == 1) center.y else center.z
		    val min = bounds[axis]
		    val max = bounds[axis + 3]
		    val d = if (axis == 0) direction.x else if (axis == 1) direction.y else direction.z
		    if (abs(d) < 1e-12) {
			    if (origin !in min..max) return null
			    return@repeat
		    }
		    var entry = (min - origin) / d
		    var exit = (max - origin) / d
		    if (entry > exit) {
			    val tmp = entry
			    entry = exit
			    exit = tmp
		    }
		    near = max(near, entry)
		    far = min(far, exit)
	    }
        if (near > far) return null
        return center.add(direction.multiply(far))
    }

    private fun limitSpeed(velocity: Vec3d): Vec3d {
        val maxSpeed = 1.7 * rocketSpeed + maxGrimBoost
        val length = velocity.length()
        if (length <= maxSpeed) return velocity
        return velocity.multiply(maxSpeed / length)
    }

    enum class FlyMode(private val elytraFlyGetter: () -> ElytraFlyMode) {
        Bounce({ bounceMode }),
//        Control({ controlMode }),
        GrimControl({ grimControlMode }),
//        Packet({ packetMode }),
        General({ generalMode });

        val elytraFly get() = elytraFlyGetter()
    }
}