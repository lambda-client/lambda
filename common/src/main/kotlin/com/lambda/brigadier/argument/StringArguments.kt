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

package com.lambda.brigadier.argument

import com.lambda.brigadier.ArgumentReader
import com.lambda.brigadier.BrigadierDsl
import com.lambda.brigadier.DefaultArgumentConstructor
import com.lambda.brigadier.DefaultArgumentReader
import com.lambda.brigadier.argument
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
