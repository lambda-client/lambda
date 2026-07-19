
package com.minato.module.modules.movement.elytrafly.modes

import com.minato.config.Config
import com.minato.config.Group
import com.minato.config.entries.Setting.Companion.onValueChange
import com.minato.context.SafeContext
import com.minato.event.events.MovementEvent
import com.minato.event.events.PacketEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.handlers.GlideHandler
import com.minato.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.minato.interaction.managers.rotating.RotationManager
import com.minato.module.hud.Speedometer
import com.minato.module.modules.movement.elytrafly.ElytraFly.FlyMode
import com.minato.module.modules.movement.elytrafly.ElytraFly.fakeFly
import com.minato.module.modules.movement.elytrafly.ObstaclePassingMode
import com.minato.module.modules.movement.elytrafly.PasserSettings
import com.minato.threading.runSafe
import com.minato.util.PacketUtils.handlePacketSilently
import com.minato.util.PacketUtils.sendPacketSilently
import com.minato.util.SpeedUnit
import com.minato.util.TickTimer
import com.minato.util.math.dist
import com.minato.util.math.distSq
import com.minato.util.math.minus
import com.minato.util.player.PlayerUtils.canStartGliding
import com.minato.util.player.PlayerUtils.canTakeoff
import net.minecraft.client.input.KeyboardInput.getMovementMultiplier
import net.minecraft.entity.Entity
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket
import net.minecraft.util.PlayerInput
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class BounceElytraFly(
	override val c: Config
) : ObstaclePassingMode(c, FlyMode.Bounce) {
	companion object {
		private const val Y_MOTION_GROUP = "Y Motion"
		private const val BOUNCE_OBSTACLE_PASSER_GROUP = "Bounce Obstacle Passer"
	}

	private val takeoff by c.setting("Takeoff", true, "Automatically jumps and initiates gliding")
	private val autoPitch by c.setting("Auto Pitch", true, "Automatically pitches the players rotation down to bounce at faster speeds")
	private val pitch by c.setting("Pitch", 72.0, -90.0..90.0, 0.000001) { autoPitch }
	private val jump by c.setting("Jump", true, "Automatically jumps")
	private val flagPause by c.setting("FlagPause Pause", 5, 0..100, 1, "How long to pause if the server flags you for a movement check", "ticks")
	private val minimizePackets by c.setting("Minimize Packets", true, "Shrinks the amount of start fly packets sent to the server as much as possible")
	private val fakeLag by c.setting("Fake Lag", true, "Emulates the player lagging to allow flying in 1x2 tunnels")
		.onValueChange { _, to -> if (!to) flushPackets() }

	@Group(Y_MOTION_GROUP) val yMotionSetting by c.setting("Y Motion", false, "Cancels the players y velocity to aid speed")
	@Group(Y_MOTION_GROUP) val onlyOnDiagonal: Boolean by c.setting("Only On Diagonal", true, "Only use y motion when the player is flying on a non-axial angle") { yMotionSetting }
	@Group(Y_MOTION_GROUP) val minDiagonalAngle by c.setting("Min Diagonal Angle", 15.0, 0.0..180.0, 0.1, "The minimum angle the player must be flying to use y motion") { yMotionSetting && onlyOnDiagonal }
	@Group(Y_MOTION_GROUP) val strictYMotionRange by c.setting("Strict Range", true, "provides an extra range check to sneak until within. Typically used for when you need to get within sub-block distances of walls for collision checks") { yMotionSetting }
	@Group(Y_MOTION_GROUP) val acceptableYMotionRange by c.setting("Acceptable Range", 0.1, 0.01..5.0, 0.01, "The acceptable distance, aside from forward distance, from the start position") { yMotionSetting && strictYMotionRange }
	@Group(Y_MOTION_GROUP) val yMotionStartSpeed by c.setting("Y Motion Start Speed", 30, 0..40, 1, unit = "bps") { yMotionSetting }
	@Group(Y_MOTION_GROUP) val speedLimit by c.setting("Speed Limit", 120, 10..400, 1, unit = "bps") { yMotionSetting }
	private val SafeContext.yMotion
		get() = yMotionSetting &&
				onYMotionAngle &&
				player.isOnGround &&
				player.isGliding &&
				Speedometer.calculateSpeed(true, SpeedUnit.BlocksPerSecond).let { speed ->
					speed > yMotionStartSpeed && speed < speedLimit
				}

	private val onYMotionAngle
		get() = !onlyOnDiagonal || diagonal

	@Group(BOUNCE_OBSTACLE_PASSER_GROUP) override val passerConfig by c.configBlock(PasserSettings(c))

	private var jumpThisTick = false
	private var prevGliding: Boolean? = null
	private val pauseTimer = TickTimer()
	private val pingPackets = ConcurrentLinkedQueue<CommonPingS2CPacket>()
	private val sendPacketQueue = LinkedList<Packet<*>>()
	private var sneakLeft = false
	private var sneakRight = false
	private var interrupting = false

	private val flightPaused
		get() = !pauseTimer.hasSurpassed(flagPause) || interrupting

	private val SafeContext.queuePackets
		get() = fakeLag && player.isGliding && (!yMotionSetting || !onYMotionAngle) &&
				player.y - startPos.y < if (passerConfig.passObstacles) passerConfig.minObstacleHeight + 0.1 else 0.163

	private val diagonal: Boolean
		get() {
			val normalised = abs(RotationManager.activeRotation.yaw % 90)
			return normalised > minDiagonalAngle && normalised < 90 - minDiagonalAngle
		}

	init {
		listen<TickEvent.Pre> {
			pauseTimer.tick()

			if (autoPitch) rotationRequest { pitch(pitch) }.submit()

			if (handlePassingObstacles()) return@listen

			if (yMotionSetting && strictYMotionRange && onYMotionAngle) run yMotionCorrection@ {
				val playerPos = player.pos
				val validDistanceFromStart = Vec3d(playerPos.x, startPos.y, playerPos.z) dist startPos > 0.1
				if (!validDistanceFromStart) return@yMotionCorrection

				val snappedDir = getSnappedDir()
				val closestLinePoint = playerPos.findClosestPointOnLine(snappedDir)
				val xz = Vec3d(player.x, closestLinePoint.y, player.z)
				if (xz distSq closestLinePoint > acceptableYMotionRange.pow(2)) {
					interrupting = true
					val offset = playerPos - startPos
					val cross = snappedDir.x * offset.z - snappedDir.z * offset.x
					val rotationRequest = rotationRequest {
						val yawAndPitch = snappedDir.yawAndPitch
						yaw(yawAndPitch.y)
					}.submit()
					if (!rotationRequest.done) return@listen
					sneakLeft = cross > 0
					sneakRight = !sneakLeft
					return@listen
				}
			}

			if (flightPaused) return@listen

			if (!player.isGliding) {
				if (takeoff && player.canTakeoff) {
					if (player.canStartGliding) GlideHandler.onGlide()
					else {
						val yawRad = Math.toRadians(player.yaw.toDouble())
						val rightX = -cos(yawRad)
						val rightZ = -sin(yawRad)
						val vx = player.velocity.x
						val vz = player.velocity.z
						val sidewaysSpeed = abs(vx * rightX + vz * rightZ)
						if (sidewaysSpeed >= 0.001) return@listen

						jumpThisTick = true
					}
				}
				return@listen
			}

			if (minimizePackets && player.getFlag(Entity.GLIDING_FLAG_INDEX) && !fakeFly && !yMotion) return@listen
			
			flyOrFakeFly()
		}

		listen<TickEvent.Post>({ -100 }) {
			interrupting = false
		}

		listen<MovementEvent.InputUpdate> { event ->
			val input = event.input
			val playerInput = input.playerInput

			if (sneakLeft || sneakRight) {
				val sneak = player.velocity.horizontal.length() > 0.001 || !player.isSneaking
				input.playerInput = PlayerInput(
					playerInput.forward,
					playerInput.backward,
					sneakLeft,
					sneakRight,
					playerInput.jump,
					sneak,
					false
				)
				input.movementVector = Vec2f(
					getMovementMultiplier(sneakLeft, sneakRight),
					0f
				)
				sneakLeft = false
				sneakRight = false
				return@listen
			}
			if ((!player.isGliding || !jump) && !jumpThisTick) return@listen
			jumpThisTick = false
			if (flightPaused) return@listen
			input.jump()
		}

		listen<PacketEvent.Send.Pre>({ 1 }) { event ->
			if (queuePackets) {
				sendPacketQueue.add(event.packet)
				event.cancel()
				return@listen
			}

			flushPackets()
		}
		listen<PacketEvent.Receive.Pre>({ 1 }) { event ->
			if (event.packet is CommonPingS2CPacket && queuePackets) {
				pingPackets.add(event.packet)
				event.cancel()
			}
		}

		onFlag { pauseTimer.reset() }

		onDisable {
			jumpThisTick = false
			prevGliding = false
			flushPackets()
			sneakLeft = false
			sneakRight = false
		}
	}

	private fun SafeContext.flushPackets() {
		while (sendPacketQueue.isNotEmpty()) {
			val packet = sendPacketQueue.poll()
			connection.sendPacketSilently(packet)
		}
		while (pingPackets.isNotEmpty()) {
			val packet = pingPackets.poll()
			connection.handlePacketSilently(packet)
		}
	}

	fun getModifiedVelocity(original: Vec3d) =
		runSafe {
			if (!yMotion) return@runSafe original
			else Vec3d(original.x, 0.0, original.z)
		} ?: original

	override fun isGliding() =
		runSafe {
			val original = player.getFlag(Entity.GLIDING_FLAG_INDEX)
			if (prevGliding == true && !flightPaused) true
			else {
				prevGliding = original
				original
			}
		} == true
}