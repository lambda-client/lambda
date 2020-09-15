package me.zeroeightsix.kami.util.math

import me.zeroeightsix.kami.util.math.RotationUtils.normalizeAngle
import net.minecraft.client.Minecraft
import net.minecraft.util.math.BlockPos
import kotlin.math.*

/**
 * Created by Dewy on the 17th of April, 2020
 * Updated by Xiaro on 18/08/20
 * Cleaned up by Avanatiker on 14/09/20
 */
object MathUtils {
    @JvmStatic
    fun mcPlayerPosFloored(mc: Minecraft): BlockPos {
        return BlockPos(floor(mc.player.posX), floor(mc.player.posY), floor(mc.player.posZ))
    }

    @JvmStatic
    fun round(value: Float, places: Int): Double {
        val scale = 10.0.pow(places.toDouble())
        return round(value * scale) / scale
    }

    @JvmStatic
    fun round(value: Double, places: Int): Double {
        val scale = 10.0.pow(places.toDouble())
        return round(value * scale) / scale
    }

    @JvmStatic
    fun isNumberEven(i: Int): Boolean {
        return i and 1 == 0
    }

    @JvmStatic
    fun reverseNumber(num: Int, min: Int, max: Int): Int {
        return max + min - num
    }

    @JvmStatic
    fun isBetween(min: Int, max: Int, value: Int): Boolean {
        return value in min..max
    }

    @JvmStatic
    fun isBetween(min: Double, max: Double, value: Double): Boolean {
        return value in min..max
    }

    @JvmStatic
    fun getPlayerCardinal(mc: Minecraft): Cardinal {
        val angle = normalizeAngle(mc.player.rotationYaw.toDouble())
        return when {
            (angle >= 157.6 || angle <= -157.5) -> Cardinal.NEG_Z //NORTH
            isBetween(-157.6, -112.5, angle) -> Cardinal.POS_X_NEG_Z //NORTH-EAST
            isBetween(-112.5, -67.5, angle) -> Cardinal.POS_X //EAST
            isBetween(-67.6, -22.6, angle) -> Cardinal.POS_X_POS_Z //SOUTH-EAST
            isBetween(-22.5, 22.5, angle) -> Cardinal.POS_Z //SOUTH
            isBetween(22.6, 67.5, angle) -> Cardinal.NEG_X_POS_Z //SOUTH-WEST
            isBetween(67.6, 112.5, angle) -> Cardinal.NEG_X //WEST
            isBetween(112.6, 157.5, angle) -> Cardinal.NEG_X_NEG_Z //NORTH-WEST
            else -> Cardinal.ERROR
        }
    }

    @JvmStatic
    fun getPlayerMainCardinal(mc: Minecraft): CardinalMain {
        return when (Character.toUpperCase(mc.player.horizontalFacing.toString()[0])) {
            'N' -> CardinalMain.NEG_Z
            'E' -> CardinalMain.POS_X
            'S' -> CardinalMain.POS_Z
            'W' -> CardinalMain.NEG_X
            else -> CardinalMain.NULL
        }
    }

    @JvmStatic
    fun convertRange(valueIn: Int, minIn: Int, maxIn: Int, minOut: Int, maxOut: Int): Int {
        return convertRange(valueIn.toDouble(), minIn.toDouble(), maxIn.toDouble(), minOut.toDouble(), maxOut.toDouble()).toInt()
    }

    @JvmStatic
    fun convertRange(valueIn: Float, minIn: Float, maxIn: Float, minOut: Float, maxOut: Float): Float {
        return convertRange(valueIn.toDouble(), minIn.toDouble(), maxIn.toDouble(), minOut.toDouble(), maxOut.toDouble()).toFloat()
    }

    @JvmStatic
    fun convertRange(valueIn: Double, minIn: Double, maxIn: Double, minOut: Double, maxOut: Double): Double {
        val rangeIn = maxIn - minIn
        val rangeOut = maxOut - minOut
        val convertedIn = (valueIn - minIn) * (rangeOut / rangeIn) + minOut
        val actualMin = min(minOut, maxOut)
        val actualMax = max(minOut, maxOut)
        return min(max(convertedIn, actualMin), actualMax)
    }

    enum class Cardinal(val directionName: String, @JvmField val cardinalName: String, val isDiagonal: Boolean) {
        NEG_Z("-Z", "North",  false),
        POS_X_NEG_Z("+X / -Z", "North East", true),
        POS_X("+X", "East", false),
        POS_X_POS_Z("+X / +Z", "South East", true),
        POS_Z("+Z", "South", false),
        NEG_X_POS_Z("-X / +Z", "South West", true),
        NEG_X("-X", "West", false),
        NEG_X_NEG_Z("-X / -Z", "North West",  true),
        ERROR("ERROR_CALC_DIRECT", "ERROR_CALC_DIRECT", true)
    }

    enum class CardinalMain(val directionName: String, val cardinalName: String) {
        NEG_Z("-Z", "North"),
        POS_X("+X", "East"),
        POS_Z("+Z", "South"),
        NEG_X("-X", "West"),
        NULL("N/A", "N/A")
    }
}