
package com.minato.brigadier.argument

import com.minato.brigadier.ArgumentReader
import com.minato.brigadier.BrigadierDsl
import com.minato.brigadier.DefaultArgumentConstructor
import com.minato.brigadier.DefaultArgumentReader
import com.minato.brigadier.argument
import com.mojang.brigadier.arguments.StringArgumentType

/**
 * Reads the string value from the argument in
 * the receiver [ArgumentReader].
 *
 * @see StringArgumentType.getString
 */
@BrigadierDsl
fun DefaultArgumentReader<StringArgumentType>.value(): String =
    StringArgumentType.getString(context, name)

/**
 * Creates a string argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> string(name: String): DefaultArgumentConstructor<S, StringArgumentType> =
    argument(name, StringArgumentType.string())

/**
 * Creates a greedy string argument with [name] as the parameter name.
 *
 * Note that no further arguments can be added after
 * a greedy string, as any command text will be treated
 * as part of the greedy string argument.
 */
@BrigadierDsl
fun <S> greedyString(name: String): DefaultArgumentConstructor<S, StringArgumentType> =
    argument(name, StringArgumentType.greedyString())

/**
 * Creates a word argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> word(name: String): DefaultArgumentConstructor<S, StringArgumentType> =
    argument(name, StringArgumentType.word())
