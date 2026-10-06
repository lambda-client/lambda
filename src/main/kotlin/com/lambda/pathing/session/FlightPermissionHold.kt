package com.lambda.pathing.session

import com.lambda.Lambda.mc

internal class FlightPermissionHold {
	private var held = false

	fun hold() {
		val abilities = mc.player?.abilities ?: return
		if (abilities.flying || !abilities.allowFlying) return
		held = true
		abilities.allowFlying = false
	}

	fun release() {
		if (!held) return
		held = false
		mc.player?.abilities?.allowFlying = true
	}
}
