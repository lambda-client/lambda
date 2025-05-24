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

/*
 * Preserve binary compatibility when moving extensions between files
 */
@file:JvmMultifileClass
@file:JvmName("ArgumentsKt")

package com.lambda.brigadier.argument

import com.lambda.brigadier.ArgumentDescriptor
import com.lambda.brigadier.ArgumentReader
import com.lambda.brigadier.BrigadierDsl
import com.lambda.brigadier.DefaultArgumentConstructor
import com.lambda.brigadier.DefaultArgumentDescriptor
import com.lambda.brigadier.RequiredArgumentConstructor
import com.lambda.brigadier.argument
import com.mojang.authlib.GameProfile
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.command.argument.GameProfileArgumentType
import net.minecraft.command.argument.TeamArgumentType
import net.minecraft.scoreboard.Team
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity

/**
 * [ArgumentDescriptor] for an [EntityArgumentType]
 * allowing a single player to be selected.
 *
 * @see player
 * @see EntityArgumentType.player
 */
object SinglePlayerArgumentDescriptor : ArgumentDescriptor<EntityArgumentType>

/**
 * [ArgumentDescriptor] for an [EntityArgumentType]
 * allowing multiple players to be selected.
 *
 * @see players
 * @see EntityArgumentType.players
 */
object ListPlayerArgumentDescriptor : ArgumentDescriptor<EntityArgumentType>

/**
 * Reads the [GameProfile] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see GameProfileArgumentType.getProfileArgument
 */
@JvmName("valueGameProfileArg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        DefaultArgumentDescriptor<
                GameProfileArgumentType
                >
        >.value(): Collection<GameProfile> {
    return GameProfileArgumentType.getProfileArgument(context, name)
}

/**
 * Reads the [Team] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see TeamArgumentType.getTeam
 */
@JvmName("valueTeamArg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        DefaultArgumentDescriptor<
                TeamArgumentType
                >
        >.value(): Team {
    return TeamArgumentType.getTeam(context, name)
}

/**
 * Reads the [ServerPlayerEntity] value from the
 * argument in the receiver [ArgumentReader].
 *
 * @see EntityArgumentType.getPlayer
 */
@JvmName("valuePlayerArg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        SinglePlayerArgumentDescriptor
        >.value(): ServerPlayerEntity {
    return EntityArgumentType.getPlayer(context, name)
}

/**
 * Reads the collection of players from the argument in
 * the receiver [ArgumentReader].
 *
 * Throws an exception if no entities are matched.
 *
 * @see EntityArgumentType.getPlayers
 */
@JvmName("requiredPlayerArg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        ListPlayerArgumentDescriptor
        >.required(): Collection<ServerPlayerEntity> {
    return EntityArgumentType.getPlayers(context, name)
}

/**
 * Reads the collection of players from the argument in
 * the receiver [ArgumentReader].
 *
 * Returns an empty collection if no entities are matched.
 *
 * @see EntityArgumentType.getOptionalPlayers
 */
@JvmName("optionalPlayerArg")
@BrigadierDsl
fun ArgumentReader<
        ServerCommandSource,
        ListPlayerArgumentDescriptor
        >.optional(): Collection<ServerPlayerEntity> {
    return EntityArgumentType.getOptionalPlayers(context, name)
}

/**
 * Creates a game profile argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> gameProfile(
    name: String,
): DefaultArgumentConstructor<S, GameProfileArgumentType> {
    return argument(name, GameProfileArgumentType.gameProfile())
}

/**
 * Creates a team argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> team(
    name: String,
): DefaultArgumentConstructor<S, TeamArgumentType> {
    return argument(name, TeamArgumentType.team())
}

/**
 * Creates a player selector argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> player(
    name: String,
): RequiredArgumentConstructor<
        S,
        SinglePlayerArgumentDescriptor
        > {
    return argument(name, EntityArgumentType.player(), SinglePlayerArgumentDescriptor)
}

/**
 * Creates a multiple player selector argument with [name] as the parameter name.
 */
@BrigadierDsl
fun <S> players(
    name: String,
): RequiredArgumentConstructor<
        S,
        ListPlayerArgumentDescriptor
        > {
    return argument(name, EntityArgumentType.players(), ListPlayerArgumentDescriptor)
}
