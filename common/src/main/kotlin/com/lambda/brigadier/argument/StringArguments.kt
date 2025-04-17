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

package com.lambda.brigadier.argument

import com.lambda.brigadier.*
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
