package com.lambda.pathing.session

import com.lambda.Lambda.mc
import net.minecraft.client.network.ClientPlayerEntity

object FlightPermissionHold {
	@Volatile
	private var holder: ClientPlayerEntity? = null

	@Volatile
	private var heldAge = Int.MIN_VALUE

	internal fun hold() {
		val player = mc.player ?: return
		holder = player
		heldAge = player.age
	}

	@JvmStatic
	fun holds(player: ClientPlayerEntity): Boolean = player === holder && player.age == heldAge
}
