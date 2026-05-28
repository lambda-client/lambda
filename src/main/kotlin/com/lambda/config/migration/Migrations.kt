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

package com.lambda.config.migration

import com.lambda.Lambda.Log
import com.lambda.config.ConfigCategory
import com.lambda.core.Loadable
import com.lambda.util.ReflectionUtils.getInstances
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import kotlin.math.max

object ConfigMigrationHandler : Loadable {
    const val DefaultSchemaVersionKey = "_schemaVersion"
    override val priority: Int = 2

    @Volatile
    private var initialized = false
    private var byCategory = emptyMap<ConfigCategory, ConfigMigration>()

    override fun load(): String {
        refreshRegistry()
        return "Loaded ${byCategory.size} config migrations"
    }

    fun schemaVersionKey(category: ConfigCategory): String? {
        ensureInitialized()
        return byCategory[category]?.schemaVersionKey
    }

    fun latestVersion(category: ConfigCategory): Int {
        ensureInitialized()
        return byCategory[category]?.latestVersion ?: 1
    }

    fun migrate(category: ConfigCategory, original: ObjectNode): MigrationResult {
        ensureInitialized()
        val migration = byCategory[category] ?: return MigrationResult(original.deepCopy(), false)
        if (migration.latestVersion <= 1) return MigrationResult(original.deepCopy(), false)

        val json = original.deepCopy()
        var currentVersion = runCatching {
            json.get(migration.schemaVersionKey)?.takeIf { it.isNumber }?.asInt()
        }.getOrNull() ?: 1
        var migrated = false

        if (currentVersion > migration.latestVersion) {
            Log.warn(
                "Config category '${category.name}' has schema version $currentVersion " +
                    "which is newer than supported ${migration.latestVersion}"
            )
            return MigrationResult(json, false)
        }

        while (currentVersion < migration.latestVersion) {
            try {
                val fromVersion = currentVersion
                val stepResult = migration.applyStep(fromVersion, json)
                if (stepResult == null) {
                    Log.warn(
                        "Missing migration step for '${category.name}' config category " +
                            "schema version $fromVersion -> ?. Expected latest schema version is ${migration.latestVersion}"
                    )
                    break
                }

                currentVersion = stepResult.toVersion
                migrated = migrated || stepResult.changed
                if (currentVersion > 1) {
                    json.put(migration.schemaVersionKey, currentVersion)
                    migrated = true
                }
            } catch (t: Throwable) {
                Log.error(
                    "Failed to migrate ${category.name} config category " +
                        "from v$currentVersion to next version",
                    t
                )
                break
            }
        }

        val normalizedVersion = max(1, currentVersion)
        if (normalizedVersion > 1) {
            val existingVersion = runCatching { json.get(migration.schemaVersionKey)?.asInt() }.getOrNull()
            if (existingVersion != normalizedVersion) {
                json.put(migration.schemaVersionKey, normalizedVersion)
                migrated = true
            }
        } else if (json.has(migration.schemaVersionKey)) {
            json.remove(migration.schemaVersionKey)
            migrated = true
        }

        return MigrationResult(json, migrated)
    }

    @Synchronized
    private fun refreshRegistry() {
        val discovered = getInstances<ConfigMigration>()
        val duplicates = discovered.groupBy { it.category }.filter { it.value.size > 1 }
        if (duplicates.isNotEmpty()) {
            duplicates.keys.forEach { key ->
                Log.warn("Multiple config migrations found for '$key'. Using the last discovered migration.")
            }
        }

        byCategory = discovered.associateBy { it.category }
        initialized = true
    }

    private fun ensureInitialized() {
        if (!initialized) refreshRegistry()
    }
}

abstract class StepConfigMigration : ConfigMigration {
    private val steps = mutableMapOf<Int, Pair<Int, ObjectNode.() -> Unit>>()

    protected fun step(fromVersion: Int, toVersion: Int, migrate: (ObjectNode) -> Unit) {
        steps[fromVersion] = toVersion to migrate
    }

    final override fun applyStep(fromVersion: Int, root: ObjectNode): StepResult? {
        val step = steps[fromVersion] ?: return null
        val (toVersion, block) = step
        root.block()
        return StepResult(toVersion = toVersion, changed = true)
    }

    fun ObjectNode.objectOrCreate(key: String): ObjectNode =
        get(key) as? ObjectNode ?: putObject(key)

    fun ObjectNode.arrayOrCreate(key: String): ArrayNode =
        get(key) as? ArrayNode ?: putArray(key)
}

interface ConfigMigration {
    val category: ConfigCategory
    val latestVersion: Int
    val schemaVersionKey: String get() = ConfigMigrationHandler.DefaultSchemaVersionKey
    fun applyStep(fromVersion: Int, root: ObjectNode): StepResult?
}

data class StepResult(
    val toVersion: Int,
    val changed: Boolean = true,
)

data class MigrationResult(
    val json: ObjectNode,
    val migrated: Boolean,
)

