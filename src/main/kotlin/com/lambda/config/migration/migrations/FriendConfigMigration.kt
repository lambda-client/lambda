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

package com.lambda.config.migration.migrations

import com.lambda.Lambda.Log
import com.lambda.config.categories.FriendCategory
import com.lambda.config.migration.StepConfigMigration
import tools.jackson.databind.JsonNode
import java.util.*

@Suppress("unused")
object FriendConfigMigration : StepConfigMigration() {
    override val category = FriendCategory
    override val latestVersion = 2

    init {
        step(1, 2) { root ->
            val config = root.objectOrCreate("friends")
            val rawFriends = config.arrayOrCreate("friends")
            val migrated = config.putArray("friends")
            val seen = mutableSetOf<UUID>()
            var dropped = 0

            rawFriends.forEach { entry ->
                entry.parseUuidOrNull()
                    ?.let { uuid ->
                        if (seen.add(uuid)) migrated.add(uuid.toString())
                    }
                    ?: run { dropped++ }
            }

            Log.info("Migrated Friend config category schema v1 -> v2: ${migrated.size()} entries converted, $dropped entries dropped")
        }
    }

    fun JsonNode.parseUuidOrNull(): UUID? {
        val raw = when {
            isString -> asString()
            isObject && asObject().has("id") -> asObject().get("id").asString()
            else -> return null
        }

        val normalized =
            if (raw.length == 32) raw.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})".toRegex(),
                "$1-$2-$3-$4-$5"
            )
            else raw

        return runCatching { UUID.fromString(normalized) }.getOrNull()
    }
}
