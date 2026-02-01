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

/*
 * Preserve binary compatibility when moving extensions between files
 */
@file:JvmMultifileClass
@file:JvmName("ArgumentsKt")

package com.lambda.brigadier.argument

import com.lambda.brigadier.ArgumentReader
import com.lambda.brigadier.BrigadierDsl
import com.lambda.brigadier.DefaultArgumentConstructor
import com.lambda.brigadier.DefaultArgumentReader
import com.lambda.brigadier.argument
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType

/**
 * Reads the boolean value from the argument in
 * the receiver [ArgumentReader].
 *
 * @see BoolArgumentType.getBool
 */
@JvmName("valueBoolArg")
@BrigadierDsl
fun DefaultArgumentReader<BoolArgumentType>.value(): Boolean {
	return BoolArgumentType.getBool(context, name)
}

/**
 * Reads the boolean value from the argument in
 * the receiver [ArgumentReader].
 *
 * @see BoolArgumentType.getBool
 */
@JvmName("valueDoubleArg")
@BrigadierDsl
fun DefaultArgumentReader<DoubleArgumentType>.value(): Double {
	return DoubleArgumentType.getDouble(context, name)
}

/**
 * Reads the float value from the argument in
 * the receiver [ArgumentReader].
 *
 * @see FloatArgumentType.getFloat
 */
@JvmName("valueFloatArg")
@BrigadierDsl
fun DefaultArgumentReader<FloatArgumentType>.value(): Float {
	return FloatArgumentType.getFloat(context, name)
}

/**
 * Reads the integer value from the argument in
 * the receiver [ArgumentReader].
 *
 * @see IntegerArgumentType.getInteger
 */
@JvmName("valueIntArg")
@BrigadierDsl
fun DefaultArgumentReader<IntegerArgumentType>.value(): Int {
	return IntegerArgumentType.getInteger(context, name)
}

/**
 * Reads the long value from the argument in
 * the receiver [ArgumentReader].
 *
 * @see LongArgumentType.getLong
 */
@JvmName("valueLongArg")
@BrigadierDsl
fun DefaultArgumentReader<LongArgumentType>.value(): Long {
	return LongArgumentType.getLong(context, name)
}

/**
 * Creates a boolean argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> boolean(
	name: String,
): DefaultArgumentConstructor<S, BoolArgumentType> {
	return argument(name, BoolArgumentType.bool())
}

/**
 * Creates a double argument with [name] as the parameter name.
 *
 * @param min the minimum value.
 * @param max the maximum value.
 */
@BrigadierDsl
fun <S> double(
	name: String,
	min: Double = -Double.MAX_VALUE,
	max: Double = Double.MAX_VALUE,
): DefaultArgumentConstructor<S, DoubleArgumentType> {
	return argument(name, DoubleArgumentType.doubleArg(min, max))
}

/**
 * Creates a float argument with [name] as the parameter name.
 *
 * @param min the minimum value.
 * @param max the maximum value.
 */
@BrigadierDsl
fun <S> float(
	name: String,
	min: Float = -Float.MAX_VALUE,
	max: Float = Float.MAX_VALUE,
): DefaultArgumentConstructor<S, FloatArgumentType> {
	return argument(name, FloatArgumentType.floatArg(min, max))
}

/**
 * Creates an integer argument with [name] as the parameter name.
 *
 * @param min the minimum value.
 * @param max the maximum value.
 */
@BrigadierDsl
fun <S> integer(
	name: String,
	min: Int = -Int.MAX_VALUE,
	max: Int = Int.MAX_VALUE,
): DefaultArgumentConstructor<S, IntegerArgumentType> {
	return argument(name, IntegerArgumentType.integer(min, max))
}

/**
 * Creates a long argument with [name] as the parameter name.
 *
 * @param min the minimum value.
 * @param max the maximum value.
 */
@BrigadierDsl
fun <S> long(
	name: String,
	min: Long = -Long.MAX_VALUE,
	max: Long = Long.MAX_VALUE,
): DefaultArgumentConstructor<S, LongArgumentType> {
	return argument(name, LongArgumentType.longArg(min, max))
}
