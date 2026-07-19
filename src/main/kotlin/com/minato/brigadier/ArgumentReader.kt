
package com.minato.brigadier

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext

typealias DefaultArgumentReader<T> = ArgumentReader<*, DefaultArgumentDescriptor<T>>

/**
 * Argument readers are a general access point for obtaining
 * argument values from the command context.
 *
 * Accessors are defined as extensions methods on the reader
 * allowing reading of arguments with specific [descriptors][argumentDescriptor]
 * from [context]s with specific source types [S]
 *
 * @property context [CommandContext] on which the command is being executed
 * @property name name of the argument as declared in the command
 * @property argumentDescriptor descriptor provided for the argument's declaration
 */
class ArgumentReader<S, out D : ArgumentDescriptor<*>>(
    val context: CommandContext<S>,
    val name: String,
    private val argumentDescriptor: D,
)

/**
 * A marker interface for any object or class that describes a subtype
 * of the argument type [A]. The descriptor is used to find the [ArgumentReader]
 * extensions applicable to the argument being described, and possibly to
 * provide additional data for those extensions.
 */
interface ArgumentDescriptor<A : ArgumentType<*>>

/**
 * Default [ArgumentDescriptor], used for argument types such as
 * [IntegerArgumentType] and many others that do only provide a
 * single argument type with no extra data needed to resolve it.
 */
class DefaultArgumentDescriptor<T : ArgumentType<*>> : ArgumentDescriptor<T>

/**
 * Casts the context to a different source, assuming that
 * the source is not used, so the source object itself
 * will never be cast and invalid casts will not throw.
 *
 * This is needed due to MC arg types requiring
 * a specific command source to resolve, even though
 * they have no need for it at all.
 *
 * @see net.minecraft.command.argument.ItemSlotArgumentType.getItemSlot
 */
@Suppress("UNCHECKED_CAST")
internal fun <S> CommandContext<*>.assumeSourceNotUsed(): CommandContext<S> {
    return this as CommandContext<S>
}
