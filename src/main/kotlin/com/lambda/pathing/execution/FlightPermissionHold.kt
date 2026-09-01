package com.lambda.pathing.execution

import com.lambda.Lambda.mc

internal class FlightPermissionHold {
    private var held: Boolean? = null

    fun hold() {
        val abilities = mc.player?.abilities ?: return
        if (abilities.flying || !abilities.allowFlying) return
        held = true
        abilities.allowFlying = false
    }

    fun release() {
        val restore = held ?: return
        held = null
        mc.player?.abilities?.allowFlying = restore
    }
}
