package com.lambda.client.util

import com.lambda.client.commons.extension.toDegree
import com.lambda.client.commons.extension.toRadian
import com.lambda.client.util.text.MessageSendHelper
import com.mojang.authlib.GameProfile
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.MoverType
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.Vec3d
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * @author Doogie13
 * @since 26/01/2023
 */
class PredictionEntity(val target: EntityLivingBase, val profile: PredictionProfile = PredictionProfile()) : EntityPlayer(target.world, GameProfile(UUID.randomUUID(), "Prediction" + Math.random())) {

    private var lastPosition = target.positionVector
    private var lastLastPosition = target.positionVector

    fun travel() {

        setSize(target.width, target.height)

        val delta = Vec3d(target.posX - target.prevPosX, target.posY - target.prevPosY, target.posZ - target.prevPosZ)
        rotationYaw = (atan2(delta.z, delta.x).toDegree() - 90).toFloat()

        setPosition(target.posX, target.posY, target.posZ)

        val speed = hypot(delta.x, delta.z)

        motionX = -sin(rotationYaw.toRadian()) * speed
        motionY = delta.y
        motionZ = cos(rotationYaw.toRadian()) * speed

        move()

    }

    fun move() {

        stepHeight = profile.stepHeight

        var i = 1

        while (i < profile.ticks) {

            capabilities.isFlying = false
            travel(0f, 0f, if (hypot(target.posX - target.prevPosX, target.posZ - target.prevPosZ) > 0.18) 1f else 0f)

            motionX *= .98F
            motionZ *= .98F

            motionY -= .08
            motionY *= .98F

            if (!onGround)
                stepHeight = -1f

            val lastY = posY
            val lastGround = onGround

            super.move(MoverType.SELF, motionX, motionY, motionZ)

            // delta Y but also remaining onground means stepped
            if (onGround && lastGround && profile.costSteps) {

                val dy = posY - lastY

                // packet step costs
                if (dy > 2.019) 
                    i += profile.stepCost25
                else if (dy > 1.5)
                    i += profile.stepCost2
                else if (dy > 1.015)
                    i += profile.stepCost15
                else if (dy > 0.6)
                    i += profile.stepCost1
                // 0.6 and below are vanilla and hence free
                
            }

            i++

        }

        lastLastPosition = lastPosition
        lastPosition = target.positionVector

    }

    override fun isSpectator(): Boolean {
        return false
    }

    override fun isCreative(): Boolean {
        return false
    }

    data class PredictionProfile(var ticks: Int = 1, var stepHeight: Float = .6f, var costSteps : Boolean = false, var stepCost1: Int = 0, var stepCost15: Int = 0, var stepCost2: Int = 0, var stepCost25: Int = 0)

}