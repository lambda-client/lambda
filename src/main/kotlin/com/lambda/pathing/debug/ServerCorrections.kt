package com.lambda.pathing.debug

import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerRotationS2CPacket
import net.minecraft.util.math.Vec3d
import java.util.concurrent.atomic.AtomicInteger

internal object ServerCorrections {
	private class Received(val millis: Long, val text: String)

	@Volatile
	private var received: List<Received> = emptyList()

	private val positionPackets = AtomicInteger()

	/** How many times the server has set the player's position; a change between two ticks is a teleport or setback. */
	val teleports: Int get() = positionPackets.get()

	fun record(packet: Packet<*>, playerId: Int?) {
		if (packet is PlayerPositionLookS2CPacket) positionPackets.incrementAndGet()
		val text = when (packet) {
			is PlayerPositionLookS2CPacket -> with(packet.change()) {
				"teleport to ${position().short()} velocity ${deltaMovement().short()} yaw %.2f pitch %.2f".format(yaw(), pitch()) +
						packet.relatives().takeIf { it.isNotEmpty() }?.joinToString(",", " relative ") { it.name.lowercase() }.orEmpty()
			}
			is EntityVelocityUpdateS2CPacket -> if (packet.entityId == playerId) "velocity ${packet.velocity.short()}" else null
			is PlayerRotationS2CPacket -> "rotation yaw %.2f%s pitch %.2f%s".format(
				packet.yaw(), if (packet.relativeYaw()) " relative" else "", packet.pitch(), if (packet.relativePitch()) " relative" else "",
			)
			is ExplosionS2CPacket -> packet.playerKnockback().map { "explosion knockback ${it.short()}" }.orElse(null)
			else -> null
		} ?: return
		val now = System.currentTimeMillis()
		received = (received.filter { now - it.millis <= RECENT_MILLIS } + Received(now, text)).takeLast(MAX_REPORTED)
	}

	fun recent(): String {
		val now = System.currentTimeMillis()
		return received.filter { now - it.millis <= RECENT_MILLIS }
			.joinToString("") { "; server ${it.text} ${now - it.millis} ms ago" }
	}

	private fun Vec3d.short() = "(%.4f,%.4f,%.4f)".format(x, y, z)

	private const val RECENT_MILLIS = 1_000L

	private const val MAX_REPORTED = 4
}
