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
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.command.argument.TimeArgumentType
import net.minecraft.command.argument.UuidArgumentType
import java.util.*

/**
 * Descriptor for a literal argument.
 *
 * Separate from [DefaultArgumentDescriptor]
 * to stand out more in type hints.
 */
object LiteralDescriptor : ArgumentDescriptor<ArgumentType<*>>

/**
 * Reads the integer value in ticks from the
 * argument in the receiver [ArgumentReader].
 */
@JvmName("valueTimeArg")
@BrigadierDsl
fun DefaultArgumentReader<TimeArgumentType>.value(): Int {
    return IntegerArgumentType.getInteger(context, name)
} // TimeArgumentType does not provide an accessor, defaulting to int

/**
 * Reads the [UUID] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see UuidArgumentType.getUuid
 */
@JvmName("valueUuidArg")
@BrigadierDsl
fun DefaultArgumentReader<UuidArgumentType>.value(): UUID {
    return UuidArgumentType.getUuid(context.assumeSourceNotUsed(), name)
}

/**
 * Creates a time argument with [name] as the parameter name.
 *
 * @see TimeArgumentType.time
 */
@BrigadierDsl
fun <S> time(
    name: String,
    minimumTicks: Int = 0,
): DefaultArgumentConstructor<S, TimeArgumentType> {
    return argument(name, TimeArgumentType.time(minimumTicks))
}

/**
 * Creates a UUID argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> uuid(
    name: String,
): RequiredArgumentConstructor<
        S,
        DefaultArgumentDescriptor<
                UuidArgumentType>
        > {
    return argument(name, UuidArgumentType.uuid())
}

/**
 * Creates a literal argument with [name] as the literal.
 *
 * Like other arguments, literal arguments create accessors,
 * which can be checked for presence of [optional] literals.
 * However, [ArgumentReader]s produced from those accessors
 * serve no purpose due to the argument not having a value.
 */
@BrigadierDsl
fun <S> literal(
    name: String,
): ArgumentConstructor<
        S,
        LiteralArgumentBuilder<S>,
        LiteralDescriptor
        > {
    return ArgumentConstructor(LiteralArgumentBuilder.literal(name), name, LiteralDescriptor)
}
