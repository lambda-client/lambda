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

import com.google.gson.JsonArray
import com.lambda.Lambda.LOG
import com.lambda.config.migration.StepConfigMigration
import com.lambda.config.migration.arrayOrCreate
import com.lambda.config.migration.objectOrCreate
import com.lambda.config.migration.parseUuidOrNull
import java.util.*

@Suppress("unused")
object FriendConfigMigration : StepConfigMigration() {
    override val configName = "friends"
    override val latestVersion = 2

    init {
        step(1, 2) {
            val config = objectOrCreate("friends")
            val rawFriends = config.arrayOrCreate("friends")
            val migrated = JsonArray()
            val seen = mutableSetOf<UUID>()
            var dropped = 0

            rawFriends.forEach { entry ->
                entry.parseUuidOrNull()
                    ?.let { uuid ->
                        if (seen.add(uuid)) migrated.add(uuid.toString())
                    }
                    ?: run { dropped++ }
            }

            config.add("friends", migrated)
            LOG.info("Migrated Friend config schema v1 -> v2: ${migrated.size()} entries converted, $dropped entries dropped")
        }
    }
}
