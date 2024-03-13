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
import net.minecraft.command.argument.EntityAnchorArgumentType
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.entity.Entity
import net.minecraft.server.command.ServerCommandSource

/**
 * [ArgumentDescriptor] for an [EntityArgumentType]
 * allowing a single entity to be selected.
 *
 * @see entity
 * @see EntityArgumentType.entity
 */
object SingleEntityArgumentDescriptor : ArgumentDescriptor<EntityArgumentType>

/**
 * [ArgumentDescriptor] for an [EntityArgumentType]
 * allowing multiple entities to be selected.
 *
 * @see entities
 * @see EntityArgumentType.entities
 */
object ListEntityArgumentDescriptor : ArgumentDescriptor<EntityArgumentType>

/**
 * Reads the [entity anchor][EntityAnchorArgumentType.EntityAnchor] value
 * of the argument in the receiver [ArgumentReader].
 *
 * @see EntityAnchorArgumentType.getEntityAnchor
 */
@JvmName("valueEntityAnchorArg")
@BrigadierDsl
fun DefaultArgumentReader<EntityAnchorArgumentType>.value(): EntityAnchorArgumentType.EntityAnchor {
    return EntityAnchorArgumentType.getEntityAnchor(
        context.assumeSourceNotUsed(),
        name
    )
}

/**
 * Reads the collection of entities from the argument in
 * the receiver [ArgumentReader].
 *
 * Throws an exception if no entities are matched.
 *
 * @see EntityArgumentType.getEntities
 */
@JvmName("requiredEntityArg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        ListEntityArgumentDescriptor
        >.required(): Collection<Entity> {
    return EntityArgumentType.getEntities(context, name)
}

/**
 * Reads the collection of entities from the argument in
 * the receiver [ArgumentReader].
 *
 * Returns an empty collection if no entities are matched.
 *
 * @see EntityArgumentType.getOptionalEntities
 */
@JvmName("optionalEntityArg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        ListEntityArgumentDescriptor
        >.optional(): Collection<Entity> {
    return EntityArgumentType.getOptionalEntities(context, name)
}

/**
 * Reads the [Entity] value from the argument in
 * the receiver [ArgumentReader].
 *
 * @see EntityArgumentType.getEntity
 */
@JvmName("valueSingleEntityArg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        SingleEntityArgumentDescriptor
        >.value(): Entity {
    return EntityArgumentType.getEntity(context, name)
}

/**
 * Creates an entity anchor argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> entityAnchor(
    name: String,
): DefaultArgumentConstructor<S, EntityAnchorArgumentType> {
    return argument(name, EntityAnchorArgumentType.entityAnchor())
}

/**
 * Creates entity argument allowing multiple entities with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> entities(
    name: String,
): RequiredArgumentConstructor<
        S,
        ListEntityArgumentDescriptor
        > {
    return argument(name, EntityArgumentType.entities(), ListEntityArgumentDescriptor)
}

/**
 * Creates an entity selector argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> entity(
    name: String,
): RequiredArgumentConstructor<
        S,
        SingleEntityArgumentDescriptor
        > {
    return argument(name, EntityArgumentType.entity(), SingleEntityArgumentDescriptor)
}
