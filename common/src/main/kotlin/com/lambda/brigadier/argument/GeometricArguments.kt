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


import com.lambda.brigadier.*
import com.lambda.brigadier.ArgumentReader
import com.lambda.brigadier.BrigadierDsl
import com.lambda.brigadier.DefaultArgumentConstructor
import com.lambda.brigadier.DefaultArgumentDescriptor
import com.lambda.brigadier.DefaultArgumentReader
import net.minecraft.command.argument.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.math.*
import java.util.*

/**
 * Reads the float value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see AngleArgumentType.getAngle
 */
@JvmName("valueAngleArg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        DefaultArgumentDescriptor<
                AngleArgumentType
                >
        >.value(): Float {
    return AngleArgumentType.getAngle(context, name)
}

/**
 * Reads the raw [PosArgument] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see RotationArgumentType.getRotation
 */
@JvmName("valueRotationArg")
@BrigadierDsl
fun DefaultArgumentReader<RotationArgumentType>.value(): PosArgument {
    return RotationArgumentType.getRotation(context.assumeSourceNotUsed(), name)
}

/**
 * Reads the [Vec2f] value from the argument
 * in the receiver [ArgumentReader], converting
 * the contained [PosArgument] to an absolute rotation.
 *
 * @see RotationArgumentType.getRotation
 * @see PosArgument.toAbsoluteRotation
 */
@JvmName("absoluteRotationArg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        DefaultArgumentDescriptor<
                RotationArgumentType
                >
        >.absolute(): Vec2f {
    return RotationArgumentType.getRotation(context, name).toAbsoluteRotation(context.source)
}

/**
 * Reads the set of [Direction.Axis] from the
 * argument in the receiver [ArgumentReader].
 *
 * @see SwizzleArgumentType.getSwizzle
 */
@JvmName("valueSwizzleArg")
@BrigadierDsl
fun DefaultArgumentReader<SwizzleArgumentType>.value(): EnumSet<Direction.Axis> {
    return SwizzleArgumentType.getSwizzle(context.assumeSourceNotUsed(), name)
}

/**
 * Reads the [BlockPos] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see BlockPosArgumentType.getBlockPos
 */
@JvmName("valueBlockPosArg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        DefaultArgumentDescriptor<
                BlockPosArgumentType
                >
        >.value(): BlockPos {
    return BlockPosArgumentType.getBlockPos(context, name)
}

/**
 * Reads the [BlockPos] value from the
 * argument in the receiver [ArgumentReader].
 *
 * Throws an exception if the selected position is not
 * loaded on the world in the command's [ServerCommandSource].
 *
 * @see BlockPosArgumentType.getLoadedBlockPos
 */
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        DefaultArgumentDescriptor<
                BlockPosArgumentType
                >
        >.requireLoaded(): BlockPos {
    return BlockPosArgumentType.getLoadedBlockPos(context, name)
}

/**
 * Reads the [ColumnPos] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see ColumnPosArgumentType.getColumnPos
 */
@JvmName("valueColumnPosArg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        DefaultArgumentDescriptor<
                ColumnPosArgumentType
                >
        >.value(): ColumnPos {
    return ColumnPosArgumentType.getColumnPos(context, name)
}

/**
 * Reads the [Vec2f] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see Vec2ArgumentType.getVec2
 */
@JvmName("valueVec2Arg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        DefaultArgumentDescriptor<
                Vec2ArgumentType
                >
        >.value(): Vec2f {
    return Vec2ArgumentType.getVec2(context, name)
}

/**
 * Reads the [Vec3d] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see Vec3ArgumentType.getVec3
 */
@JvmName("valueVec3Arg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        DefaultArgumentDescriptor<
                Vec3ArgumentType
                >
        >.value(): Vec3d {
    return Vec3ArgumentType.getVec3(context, name)
}

/**
 * Reads the raw [PosArgument] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see Vec3ArgumentType.getPosArgument
 */
@JvmName("posVec3Arg")
@BrigadierDsl
fun DefaultArgumentReader<Vec3ArgumentType>.posArgument(): PosArgument {
    return Vec3ArgumentType.getPosArgument(context.assumeSourceNotUsed(), name)
}


/**
 * Creates an angle argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> angle(
    name: String
): DefaultArgumentConstructor<S, AngleArgumentType> {
    return argument(name, AngleArgumentType.angle())
}

/**
 * Creates a rotation argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> rotation(
    name: String
): DefaultArgumentConstructor<S, RotationArgumentType> {
    return argument(name, RotationArgumentType.rotation())
}

/**
 * Creates a swizzle argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> swizzle(
    name: String
): DefaultArgumentConstructor<S, SwizzleArgumentType> {
    return argument(name, SwizzleArgumentType.swizzle())
}

/**
 * Creates a block pos argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> blockPos(
    name: String
): DefaultArgumentConstructor<S, BlockPosArgumentType> {
    return argument(name, BlockPosArgumentType.blockPos())
}

/**
 * Creates a column pos argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> columnPos(
    name: String
): DefaultArgumentConstructor<S, ColumnPosArgumentType> {
    return argument(name, ColumnPosArgumentType.columnPos())
}

/**
 * Creates a vec2 argument with [name] as the parameter name.
 *
 * @param centerIntegers whether the integers are centered on the block
 */
@BrigadierDsl
fun <S> vec2(
    name: String,
    centerIntegers: Boolean = false
): DefaultArgumentConstructor<S, Vec2ArgumentType> {
    return argument(name, Vec2ArgumentType.vec2(centerIntegers))
}

/**
 * Creates a vec3 argument with [name] as the parameter name.
 *
 * @param centerIntegers whether the integers are centered on the block
 */
@BrigadierDsl
fun <S> vec3(
    name: String,
    centerIntegers: Boolean = false
): DefaultArgumentConstructor<S, Vec3ArgumentType> {
    return argument(name, Vec3ArgumentType.vec3(centerIntegers))
}
