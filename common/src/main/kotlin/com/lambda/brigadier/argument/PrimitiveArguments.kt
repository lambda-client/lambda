/*
 * Copyright 2023 The Quilt Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Preserve binary compatibility when moving extensions between files
 */
@file:JvmMultifileClass
@file:JvmName("ArgumentsKt")

package com.lambda.brigadier.argument

import com.lambda.brigadier.BrigadierDsl
import com.lambda.brigadier.DefaultArgumentConstructor
import com.lambda.brigadier.DefaultArgumentReader
import com.lambda.brigadier.argument
import com.mojang.brigadier.arguments.*

/**
 * Reads the boolean value from the argument in
 * the receiver [DefaultArgumentReader].
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
 * the receiver [DefaultArgumentReader].
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
 * the receiver [DefaultArgumentReader].
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
 * the receiver [DefaultArgumentReader].
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
 * the receiver [DefaultArgumentReader].
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
