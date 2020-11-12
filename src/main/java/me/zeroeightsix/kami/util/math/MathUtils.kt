package me.zeroeightsix.kami.util.math

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * Created by Dewy on the 17th of April, 2020
 * Updated by Xiaro on 18/08/20
 * Cleaned up by Avanatiker on 14/09/20
 */
object MathUtils {

    @JvmStatic
    fun ceilToPOT(valueIn: Int): Int {
        // Magical bit shifting
        var i = valueIn
        i--
        i = i or (i shr 1)
        i = i or (i shr 2)
        i = i or (i shr 4)
        i = i or (i shr 8)
        i = i or (i shr 16)
        i++
        return i
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
    fun getPlayerCardinal(player: EntityPlayer): Cardinal {
        val angle = normalizeAngle(player.rotationYaw.toDouble())
        return when {
            angle >= 157.6 || angle <= -157.5 -> Cardinal.NEG_Z //NORTH
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
    fun getPlayerMainCardinal(player: EntityPlayer): CardinalMain {
        return when (Character.toUpperCase(player.horizontalFacing.toString()[0])) {
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

    enum class Cardinal(@JvmField var cardinalName: String) {
        POS_Z("+Z"),
        NEG_X_POS_Z("-X / +Z"),
        NEG_X("-X"),
        NEG_X_NEG_Z("-X / -Z"),
        NEG_Z("-Z"),
        POS_X_NEG_Z("+X / -Z"),
        POS_X("+X"),
        POS_X_POS_Z("+X / +Z"),
        ERROR("ERROR_CALC_DIRECT");
    }

    enum class CardinalMain(@JvmField var cardinalName: String) {
        POS_Z("+Z"),
        NEG_X("-X"),
        NEG_Z("-Z"),
        POS_X("+X"),
        NULL("N/A");
    }
}