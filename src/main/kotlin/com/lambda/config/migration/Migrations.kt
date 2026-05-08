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

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lambda.Lambda.Log
import com.lambda.core.Loadable
import com.lambda.util.ReflectionUtils.getInstances
import java.util.*
import kotlin.math.max

interface ConfigMigration {
    val configName: String
    val latestVersion: Int
    val schemaVersionKey: String get() = ConfigMigrations.DefaultSchemaVersionKey
    fun applyStep(fromVersion: Int, root: JsonObject): StepResult?
}

data class StepResult(
    val toVersion: Int,
    val changed: Boolean = true,
)

data class MigrationResult(
    val json: JsonObject,
    val migrated: Boolean,
)

abstract class StepConfigMigration : ConfigMigration {
    private val steps = mutableMapOf<Int, Pair<Int, JsonObject.() -> Unit>>()

    protected fun step(fromVersion: Int, toVersion: Int, migrate: JsonObject.() -> Unit) {
        steps[fromVersion] = toVersion to migrate
    }

    final override fun applyStep(fromVersion: Int, root: JsonObject): StepResult? {
        val step = steps[fromVersion] ?: return null
        val (toVersion, block) = step
        root.block()
        return StepResult(toVersion = toVersion, changed = true)
    }
}

object ConfigMigrations : Loadable {
    const val DefaultSchemaVersionKey = "_schemaVersion"
    override val priority: Int = 2

    @Volatile
    private var initialized = false
    private var byConfig = emptyMap<String, ConfigMigration>()

    override fun load(): String {
        refreshRegistry()
        return "Loaded ${byConfig.size} config migrations"
    }

    fun schemaVersionKey(configName: String): String? {
        ensureInitialized()
        return byConfig[configName]?.schemaVersionKey
    }

    fun latestVersion(configName: String): Int {
        ensureInitialized()
        return byConfig[configName]?.latestVersion ?: 1
    }

    fun migrate(configName: String, original: JsonObject): MigrationResult {
        ensureInitialized()
        val migration = byConfig[configName] ?: return MigrationResult(original.deepCopy(), false)
        if (migration.latestVersion <= 1) return MigrationResult(original.deepCopy(), false)

        val json = original.deepCopy()
        var currentVersion = runCatching {
            json.get(migration.schemaVersionKey)?.takeIf { it.isJsonPrimitive }?.asInt
        }.getOrNull() ?: 1
        var migrated = false

        if (currentVersion > migration.latestVersion) {
            Log.warn(
                "Config ${configName.replaceFirstChar { it.uppercase() }} has schema version $currentVersion " +
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
                        "Missing migration step for ${configName.replaceFirstChar { it.uppercase() }} " +
                            "schema version $fromVersion -> ?. Expected latest schema version is ${migration.latestVersion}"
                    )
                    break
                }

                currentVersion = stepResult.toVersion
                migrated = migrated || stepResult.changed
                if (currentVersion > 1) {
                    json.addProperty(migration.schemaVersionKey, currentVersion)
                    migrated = true
                }
            } catch (t: Throwable) {
                Log.error(
                    "Failed to migrate ${configName.replaceFirstChar { it.uppercase() }} config " +
                        "from v$currentVersion to next version",
                    t
                )
                break
            }
        }

        val normalizedVersion = max(1, currentVersion)
        if (normalizedVersion > 1) {
            val existingVersion = runCatching { json.get(migration.schemaVersionKey)?.asInt }.getOrNull()
            if (existingVersion != normalizedVersion) {
                json.addProperty(migration.schemaVersionKey, normalizedVersion)
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
        val duplicates = discovered.groupBy { it.configName }.filter { it.value.size > 1 }
        if (duplicates.isNotEmpty()) {
            duplicates.keys.forEach { key ->
                Log.warn("Multiple config migrations found for '$key'. Using the last discovered migration.")
            }
        }

        byConfig = discovered.associateBy { it.configName }
        initialized = true
    }

    private fun ensureInitialized() {
        if (!initialized) refreshRegistry()
    }
}

fun JsonObject.objectOrCreate(key: String): JsonObject =
    getAsJsonObject(key) ?: JsonObject().also { add(key, it) }

fun JsonObject.arrayOrCreate(key: String): JsonArray =
    getAsJsonArray(key) ?: JsonArray().also { add(key, it) }

fun JsonElement.parseUuidOrNull(): UUID? {
    val raw = when {
        isJsonPrimitive -> asString
        isJsonObject && asJsonObject.has("id") -> asJsonObject.get("id").asString
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
