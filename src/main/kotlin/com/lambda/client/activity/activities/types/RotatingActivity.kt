package com.lambda.client.activity.activities.types

import com.lambda.client.activity.Activity
import com.lambda.client.manager.managers.PlayerPacketManager
import com.lambda.client.util.math.Vec2f

interface RotatingActivity {
    var rotation: Vec2f

    companion object {
        fun checkRotating(activity: Activity) {
            if (activity !is RotatingActivity) return

            with(activity) {
                PlayerPacketManager.sendPlayerPacket {
                    rotate(rotation)
                }
            }
        }
    }
}