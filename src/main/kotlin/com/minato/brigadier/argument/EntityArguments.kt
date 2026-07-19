
package com.minato.brigadier.argument

import com.minato.brigadier.ArgumentDescriptor
import com.minato.brigadier.ArgumentReader
import com.minato.brigadier.BrigadierDsl
import com.minato.brigadier.DefaultArgumentConstructor
import com.minato.brigadier.DefaultArgumentReader
import com.minato.brigadier.RequiredArgumentConstructor
import com.minato.brigadier.argument
import com.minato.brigadier.assumeSourceNotUsed
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
