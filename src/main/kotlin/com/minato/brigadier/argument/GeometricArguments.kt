
package com.minato.brigadier.argument


import com.minato.brigadier.ArgumentReader
import com.minato.brigadier.BrigadierDsl
import com.minato.brigadier.DefaultArgumentConstructor
import com.minato.brigadier.DefaultArgumentDescriptor
import com.minato.brigadier.DefaultArgumentReader
import com.minato.brigadier.argument
import com.minato.brigadier.assumeSourceNotUsed
import net.minecraft.command.argument.AngleArgumentType
import net.minecraft.command.argument.BlockPosArgumentType
import net.minecraft.command.argument.ColumnPosArgumentType
import net.minecraft.command.argument.PosArgument
import net.minecraft.command.argument.RotationArgumentType
import net.minecraft.command.argument.SwizzleArgumentType
import net.minecraft.command.argument.Vec2ArgumentType
import net.minecraft.command.argument.Vec3ArgumentType
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ColumnPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import java.util.*

/**
 * Reads the float value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see AngleArgumentType.getAngle
 */
@BrigadierDsl
fun ArgumentReader<ServerCommandSource, DefaultArgumentDescriptor<AngleArgumentType>>.value() =
    AngleArgumentType.getAngle(context, name)

/**
 * Reads the raw [PosArgument] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see RotationArgumentType.getRotation
 */
@BrigadierDsl
fun DefaultArgumentReader<RotationArgumentType>.value(): PosArgument =
    RotationArgumentType.getRotation(context.assumeSourceNotUsed(), name)

/**
 * Reads the [Vec2f] value from the argument
 * in the receiver [ArgumentReader], converting
 * the contained [PosArgument] to an absolute rotation.
 *
 * @see RotationArgumentType.getRotation
 * @see PosArgument.toAbsoluteRotation
 */
@BrigadierDsl
fun ArgumentReader<ServerCommandSource, DefaultArgumentDescriptor<RotationArgumentType>>.absolute(): Vec2f =
    RotationArgumentType.getRotation(context, name).getRotation(context.source)

/**
 * Reads the set of [Direction.Axis] from the
 * argument in the receiver [ArgumentReader].
 *
 * @see SwizzleArgumentType.getSwizzle
 */
@BrigadierDsl
fun DefaultArgumentReader<SwizzleArgumentType>.value(): EnumSet<Direction.Axis> =
    SwizzleArgumentType.getSwizzle(context.assumeSourceNotUsed(), name)

/**
 * Reads the [BlockPos] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see BlockPosArgumentType.getBlockPos
 */
@JvmName("valueBlockPosArg")
@BrigadierDsl
fun ArgumentReader<ServerCommandSource, DefaultArgumentDescriptor<BlockPosArgumentType>>.value(): BlockPos =
    BlockPosArgumentType.getBlockPos(context, name)

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
fun ArgumentReader<ServerCommandSource, DefaultArgumentDescriptor<BlockPosArgumentType>>.requireLoaded(): BlockPos =
    BlockPosArgumentType.getLoadedBlockPos(context, name)

/**
 * Reads the [ColumnPos] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see ColumnPosArgumentType.getColumnPos
 */
@BrigadierDsl
fun ArgumentReader<ServerCommandSource, DefaultArgumentDescriptor<ColumnPosArgumentType>>.value(): ColumnPos =
    ColumnPosArgumentType.getColumnPos(context, name)

/**
 * Reads the [Vec2f] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see Vec2ArgumentType.getVec2
 */
@BrigadierDsl
fun ArgumentReader<ServerCommandSource, DefaultArgumentDescriptor<Vec2ArgumentType>>.value(): Vec2f =
    Vec2ArgumentType.getVec2(context, name)

/**
 * Reads the [Vec3d] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see Vec3ArgumentType.getVec3
 */
@BrigadierDsl
fun ArgumentReader<ServerCommandSource, DefaultArgumentDescriptor<Vec3ArgumentType>>.value(): Vec3d =
    Vec3ArgumentType.getVec3(context, name)

/**
 * Reads the raw [PosArgument] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see Vec3ArgumentType.getPosArgument
 */
@BrigadierDsl
fun DefaultArgumentReader<Vec3ArgumentType>.posArgument(): PosArgument =
    Vec3ArgumentType.getPosArgument(context.assumeSourceNotUsed(), name)

/**
 * Creates an angle argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> angle(name: String): DefaultArgumentConstructor<S, AngleArgumentType> =
    argument(name, AngleArgumentType.angle())

/**
 * Creates a rotation argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> rotation(name: String): DefaultArgumentConstructor<S, RotationArgumentType> =
    argument(name, RotationArgumentType.rotation())

/**
 * Creates a swizzle argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> swizzle(
    name: String,
): DefaultArgumentConstructor<S, SwizzleArgumentType> {
    return argument(name, SwizzleArgumentType.swizzle())
}

/**
 * Creates a block pos argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> blockPos(
    name: String,
): DefaultArgumentConstructor<S, BlockPosArgumentType> {
    return argument(name, BlockPosArgumentType.blockPos())
}

/**
 * Creates a column pos argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> columnPos(
    name: String,
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
    centerIntegers: Boolean = false,
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
    centerIntegers: Boolean = false,
): DefaultArgumentConstructor<S, Vec3ArgumentType> {
    return argument(name, Vec3ArgumentType.vec3(centerIntegers))
}
