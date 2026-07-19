
package com.minato.brigadier

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
