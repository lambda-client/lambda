/*
 * Copyright 2026 Lambda
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

import com.lambda.brigadier.ArgumentDescriptor
import com.lambda.brigadier.ArgumentReader
import com.lambda.brigadier.BrigadierDsl
import com.lambda.brigadier.DefaultArgumentConstructor
import com.lambda.brigadier.DefaultArgumentReader
import com.lambda.brigadier.RequiredArgumentConstructor
import com.lambda.brigadier.argument
import com.lambda.brigadier.assumeSourceNotUsed
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
@BrigadierDsl
fun DefaultArgumentReader<EntityAnchorArgumentType>.value() =
    EntityAnchorArgumentType.getEntityAnchor(
        context.assumeSourceNotUsed(), name
    )

/**
 * Reads the collection of entities from the argument in
 * the receiver [ArgumentReader].
 *
 * Throws an exception if no entities are matched.
 *
 * @see EntityArgumentType.getEntities
 */
@BrigadierDsl
fun ArgumentReader<ServerCommandSource, ListEntityArgumentDescriptor>.required() =
    EntityArgumentType.getEntities(context, name)

/**
 * Reads the collection of entities from the argument in
 * the receiver [ArgumentReader].
 *
 * Returns an empty collection if no entities are matched.
 *
 * @see EntityArgumentType.getOptionalEntities
 */
@BrigadierDsl
fun ArgumentReader<ServerCommandSource, ListEntityArgumentDescriptor>.optional(): Collection<Entity> =
    EntityArgumentType.getOptionalEntities(context, name)

/**
 * Reads the [Entity] value from the argument in
 * the receiver [ArgumentReader].
 *
 * @see EntityArgumentType.getEntity
 */
@BrigadierDsl
fun ArgumentReader<ServerCommandSource, SingleEntityArgumentDescriptor>.value(): Entity =
    EntityArgumentType.getEntity(context, name)

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
): RequiredArgumentConstructor<S, ListEntityArgumentDescriptor> =
    argument(name, EntityArgumentType.entities(), ListEntityArgumentDescriptor)

/**
 * Creates an entity selector argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> entity(
    name: String,
): RequiredArgumentConstructor<S, SingleEntityArgumentDescriptor> =
    argument(name, EntityArgumentType.entity(), SingleEntityArgumentDescriptor)
