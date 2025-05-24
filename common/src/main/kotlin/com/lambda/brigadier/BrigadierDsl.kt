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

package com.lambda.brigadier

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext

typealias ArgumentAccessor<S, D> =
        CommandContext<S>.() -> ArgumentReader<S, D>

@DslMarker
annotation class BrigadierDsl

@BrigadierDsl
fun <S> CommandDispatcher<S>.register(
    command: String,
    action: LiteralArgumentBuilder<S>.() -> Unit,
) {
    val argument = LiteralArgumentBuilder.literal<S>(command)
    argument.apply(action)
    register(argument)
}

@JvmName("getRequired")
operator fun <S, D : ArgumentDescriptor<*>> CommandContext<S>.get(
    accessor: ArgumentAccessor<S, D>,
): ArgumentReader<S, D> {
    return accessor()
}

/**
 * Applies the [accessor] to the receiver [CommandContext].
 *
 * If [accessor] is `null`, returns `null`.
 *
 * Shorthand/alternative to `context.accessor()`.
 */
@JvmName("getOptional")
operator fun <S, D : ArgumentDescriptor<*>> CommandContext<S>.get(
    accessor: ArgumentAccessor<S, D>?,
): ArgumentReader<S, D>? {
    return accessor?.invoke(this)
}
