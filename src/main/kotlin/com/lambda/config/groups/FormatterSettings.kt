/*
 * Copyright 2025 Lambda
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

package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.util.NamedEnum
import com.lambda.util.math.Vec2d
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import org.joml.Vector3f
import org.joml.Vector4f
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

class FormatterSettings(
    owner: Configurable,
    vararg baseGroup: NamedEnum,
    vis: () -> Boolean = { true }
) : FormatterConfig, SettingGroup(owner) {
    val localeEnum by owner.setting("Locale", FormatterConfig.Locales.US, "The regional formatting used for numbers", vis).group(*baseGroup)
    override val locale get() = localeEnum.locale

    val sep by owner.setting("Separator", FormatterConfig.TupleSeparator.Comma, "Separator for string serialization of tuple data structures", vis).group(*baseGroup)
    val customSep by owner.setting("Custom Separator", "") { vis() && sep == FormatterConfig.TupleSeparator.Custom }.group(*baseGroup)
    override val separator get() = if (sep == FormatterConfig.TupleSeparator.Custom) customSep else sep.separator

    val group by owner.setting("Tuple Prefix", FormatterConfig.TupleGrouping.Parentheses) { vis() }.group(*baseGroup)
    override val prefix get() = group.prefix
    override val postfix get() = group.postfix

    val floatingPrecision by owner.setting("Floating Precision", 3, 0..6, 1, "Precision for floating point numbers") { vis() }.group(*baseGroup)
    override val precision get() = floatingPrecision

    val timeFormat by owner.setting("Time Format", FormatterConfig.Time.IsoDateTime) { vis() }.group(*baseGroup)
    override val format get() = timeFormat.format

    infix fun format(value: Short)  = "%d".format(locale, value)
    infix fun format(value: Int)    = "%d".format(locale, value)
    infix fun format(value: Long)   = "%d".format(locale, value)
    infix fun format(value: Float)  = "%,.${precision}f".format(locale, value)
    infix fun format(value: Double) = "%,.${precision}f".format(locale, value)

    infix fun format(tuple: Vec2f)      = "$prefix${format(tuple.x)}$separator${format(tuple.y)}$postfix"
    infix fun format(tuple: Vec2d)      = "$prefix${format(tuple.x)}$separator${format(tuple.y)}$postfix"
    infix fun format(tuple: Vec3i)      = "$prefix${format(tuple.x)}$separator${format(tuple.y)}$separator${format(tuple.z)}$postfix"
    infix fun format(tuple: Vec3d)      = "$prefix${format(tuple.x)}$separator${format(tuple.y)}$separator${format(tuple.z)}$postfix"
    infix fun format(tuple: Vector3f)   = "$prefix${format(tuple.x)}$separator${format(tuple.y)}$separator${format(tuple.z)}$postfix"
    infix fun format(tuple: Vector4f)   = "$prefix${format(tuple.x)}$separator${format(tuple.y)}$separator${format(tuple.z)}$separator${format(tuple.w)}$postfix"

    infix fun format(tuple: ShortArray)       = tuple.joinToString(separator, prefix, postfix) { format(it) }
    infix fun format(tuple: IntArray)         = tuple.joinToString(separator, prefix, postfix) { format(it) }
    infix fun format(tuple: LongArray)        = tuple.joinToString(separator, prefix, postfix) { format(it) }
    infix fun format(tuple: FloatArray)       = tuple.joinToString(separator, prefix, postfix) { format(it) }
    infix fun format(tuple: DoubleArray)      = tuple.joinToString(separator, prefix, postfix) { format(it) }

    @JvmName("formatVec2fList") infix fun format(tuple: List<Vec2f>) = tuple.joinToString(separator, prefix, postfix) { format(it) }
    @JvmName("formatVec2dList") infix fun format(tuple: List<Vec2d>) = tuple.joinToString(separator, prefix, postfix) { format(it) }
    @JvmName("formatVec3iList") infix fun format(tuple: List<Vec3i>) = tuple.joinToString(separator, prefix, postfix) { format(it) }
    @JvmName("formatVec3dList") infix fun format(tuple: List<Vec3d>) = tuple.joinToString(separator, prefix, postfix) { format(it) }
    @JvmName("formatVector3fList") infix fun format(tuple: List<Vector3f>) = tuple.joinToString(separator, prefix, postfix) { format(it) }
    @JvmName("formatVector4fList") infix fun format(tuple: List<Vector4f>) = tuple.joinToString(separator, prefix, postfix) { format(it) }

    infix fun format(time: LocalDate): String       = time.format(format)
    infix fun format(time: LocalDateTime): String   = time.format(format)
    infix fun format(time: ZonedDateTime): String   = time.format(format)
}