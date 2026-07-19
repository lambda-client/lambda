
package com.minato.brigadier

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext

typealias RequiredArgumentConstructor<S, D> =
        ArgumentConstructor<S, RequiredArgumentBuilder<S, *>, D>

typealias DefaultArgumentConstructor<S, T> =
        RequiredArgumentConstructor<S, DefaultArgumentDescriptor<T>>

/**
 * Class containing all data necessary to construct an
 * [optional] or [required] argument.
 */
class ArgumentConstructor<S, B : ArgumentBuilder<S, *>, D : ArgumentDescriptor<*>>(
    private val builder: B,
    val name: String,
    private val descriptor: D,
) {
    /**
     * Converts this constructor into a required argument.
     *
     * @see CommandArgument.Required
     * @see required
     */
    fun required(): CommandArgument.Required<S, B, D> {
        return CommandArgument.Required(builder, name, descriptor)
    }

    /**
     * Converts this constructor into an optional argument.
     *
     * @see CommandArgument.Optional
     * @see optional
     *
     * @author Cypher121
     */
    fun optional(): CommandArgument.Optional<S, D> {
        return CommandArgument.Optional(builder, name, descriptor)
    }
}

/**
 * An argument ready to be registered into a command.
 * Can be either optional or required.
 */
sealed class CommandArgument<S, out B : ArgumentBuilder<S, *>, out D : ArgumentDescriptor<*>, out A>(
    val builder: B,
    val name: String,
    val descriptor: D,
) {
    /**
     * Registers the argument on the [parentBuilder].
     *
     * Exact behavior differs between [Required]
     * and [Optional] arguments.
     *
     * @see Required.register
     * @see Optional.register
     */
    abstract fun register(parentBuilder: ArgumentBuilder<S, *>, action: B.(A) -> Unit)

    /**
     * [CommandArgument] that must be present in the command.
     *
     * @see required
     */
    class Required<S, B : ArgumentBuilder<S, *>, D : ArgumentDescriptor<*>>(
        builder: B,
        name: String,
        descriptor: D,
    ) : CommandArgument<S, B, D, ArgumentAccessor<S, D>>(builder, name, descriptor) {
        /**
         * Registers the argument on the [parentBuilder]
         * as a required argument and further configures the
         * resulting subcommand with the given [action].
         *
         * Accessor passed to [action] can be used on a [CommandContext]
         * within an [execute] block to obtain an [ArgumentReader] for this argument.
         */
        // will inline if type is known, e.g. in `required`
        // calls normally otherwise
        @Suppress("OVERRIDE_BY_INLINE")
        override inline fun register(
            parentBuilder: ArgumentBuilder<S, *>,
            action: B.(ArgumentAccessor<S, D>) -> Unit,
        ) {
            builder.action {
                ArgumentReader(this, name, descriptor)
            }

            parentBuilder.then(builder)
        }
    }

    /**
     * [CommandArgument] that may be absent from the command.
     *
     * @see optional
     */
    class Optional<S, D : ArgumentDescriptor<*>>(
        builder: ArgumentBuilder<S, *>,
        name: String,
        descriptor: D,
    ) : CommandArgument<S, ArgumentBuilder<S, *>, D, ArgumentAccessor<S, D>?>(builder, name, descriptor) {
        /**
         * Registers the argument on the [parentBuilder]
         * as an optional argument and further configures the
         * resulting subcommands with the given [action].
         *
         * The [action] is called once on the argument's builder,
         * and once on the parent builder, creating a branching path.
         *
         * On the path where the argument is present, the accessor
         * passed to [action] is not `null` and can be used on a [CommandContext]
         * within an [execute] block to obtain an [ArgumentReader] for this argument.
         *
         * On the path where the argument is not present,
         * the argument passed to [action] is instead `null`.
         */
        // will inline if type is known, e.g. in `optional`
        // calls normally otherwise
        @Suppress("OVERRIDE_BY_INLINE")
        override inline fun register(
            parentBuilder: ArgumentBuilder<S, *>,
            action: (ArgumentBuilder<S, *>.(ArgumentAccessor<S, D>?) -> Unit),
        ) {
            builder.action {
                ArgumentReader(this, name, descriptor)
            }

            parentBuilder.then(builder)

            parentBuilder.action(null)
        }
    }
}

/**
 * Creates an argument of the specified [argumentType] with the
 * specified parameter [name] and [argumentDescriptor].
 */
@BrigadierDsl
fun <S, D : ArgumentDescriptor<A>, AT, A : ArgumentType<AT>> argument(
    name: String,
    argumentType: A,
    argumentDescriptor: D,
): RequiredArgumentConstructor<S, D> {
    val builder = RequiredArgumentBuilder.argument<S, AT>(
        name,
        argumentType
    )

    return ArgumentConstructor(builder, name, argumentDescriptor)
}

/**
 * Creates an argument of the specified [argumentType] with the
 * specified parameter [name] and a [DefaultArgumentDescriptor]
 * for the argument type used.
 */
@BrigadierDsl
fun <S, AT, A : ArgumentType<AT>> argument(
    name: String,
    argumentType: A,
): RequiredArgumentConstructor<S, DefaultArgumentDescriptor<A>> {
    return argument(name, argumentType, DefaultArgumentDescriptor())
}

/**
 * Registers the argument specified by the [constructor]
 * as a required argument and further configures the
 * resulting subcommand with the given [action].
 *
 * Accessor passed to [action] can be used on a [CommandContext]
 * within an [execute] block to obtain an [ArgumentReader] for this argument.
 *
 * @see CommandArgument.Required
 */
@BrigadierDsl
inline fun <S, B : ArgumentBuilder<S, *>, D : ArgumentDescriptor<*>> ArgumentBuilder<S, *>.required(
    constructor: ArgumentConstructor<S, B, D>,
    action: B.(ArgumentAccessor<S, D>) -> Unit,
) {
    constructor.required().register(this, action)
}

/**
 * Registers the argument specified by the [constructor]
 * as an optional argument and further configures the
 * resulting subcommands with the given [action].
 *
 * The [action] is called once on the argument's builder,
 * and once on the parent builder, creating a branching path.
 *
 * On the path where the argument is present, the accessor
 * passed to [action] is not `null` and can be used on a [CommandContext]
 * within an [execute] block to obtain an [ArgumentReader] for this argument.
 *
 * On the path where the argument is not present,
 * the argument passed to [action] is instead `null`.
 *
 * @see CommandArgument.Optional
 */
@BrigadierDsl
inline fun <S, D : ArgumentDescriptor<*>> ArgumentBuilder<S, *>.optional(
    constructor: ArgumentConstructor<S, *, D>,
    action: ArgumentBuilder<S, *>.(ArgumentAccessor<S, D>?) -> Unit,
) {
    constructor.optional().register(this, action)
}
