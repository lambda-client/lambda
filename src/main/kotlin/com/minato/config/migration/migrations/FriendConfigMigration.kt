
package com.minato.config.migration.migrations

import com.minato.Minato.LOG
import com.minato.config.categories.FriendCategory
import com.minato.config.migration.StepConfigMigration
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

            LOG.info("Migrated Friend config category schema v1 -> v2: ${migrated.size()} entries converted, $dropped entries dropped")
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
