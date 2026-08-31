/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.command.commands

import com.lambda.pathing.core.Stance

/**
 * Shared ground between the pathing commands: the goal staged by `goal` (walked by
 * a bare `path`), and the coordinate parsing they all use.
 *
 * Coordinates follow the familiar `~` convention: `~` alone is the player's own
 * coordinate, `~n` an offset from it, a bare integer absolute. Any number of
 * whole triples makes a route walked in order.
 */
object PathingWaypoints {
    /** Waypoints staged by `goal`, walked in order by a bare `path`. */
    var pending: List<Stance> = emptyList()

    /** Parses whitespace-separated coordinate triples; null when the text is not
     *  a positive multiple of three valid coordinates. */
    fun parse(raw: String, origin: Stance): List<Stance>? {
        val tokens = raw.trim().split(WHITESPACE)
        if (tokens.isEmpty() || tokens.size % 3 != 0) return null
        return tokens.chunked(3).map { (x, y, z) ->
            Stance(
                coordinate(x, origin.x) ?: return null,
                coordinate(y, origin.y) ?: return null,
                coordinate(z, origin.z) ?: return null,
            )
        }
    }

    private fun coordinate(token: String, origin: Int): Int? = when {
        token == "~" -> origin
        token.startsWith("~") -> token.drop(1).toIntOrNull()?.let { origin + it }
        else -> token.toIntOrNull()
    }

    fun describe(verb: String, waypoints: List<Stance>): String =
        waypoints.joinToString(" -> ", prefix = "$verb ") { "(${it.x}, ${it.y}, ${it.z})" }

    private val WHITESPACE = Regex("\\s+")
}
