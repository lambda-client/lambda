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

package com.lambda.config

import com.google.gson.JsonElement
import com.google.gson.JsonIOException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.lambda.Lambda.Log
import com.lambda.Lambda.gson
import com.lambda.config.ConfigLoader.configByName
import com.lambda.config.ConfigLoader.configCategories
import com.lambda.config.categories.AutomationCategory
import com.lambda.config.categories.ModuleCategory
import com.lambda.config.migration.ConfigMigrations
import com.lambda.core.Loadable
import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.BaritoneHandler.primary
import com.lambda.threading.runIO
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.CommunicationUtils.logError
import com.lambda.util.FileUtils.createIfNotExists
import com.lambda.util.FileUtils.ifExists
import com.lambda.util.FileUtils.ifNotExists
import com.lambda.util.FolderRegistry
import com.lambda.util.StringUtils.capitalize
import java.io.File
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.minutes

/**
 * Represents a compound of [Config] objects whose [SettingCore]s
 * are saved into a single [ConfigCategory] file ([ConfigCategory.primaryFile]).
 *
 * This class also handles the concurrent loading and saving of persisted data on the `Dispatchers.IO` thread.
 * Each configuration will be loaded concurrently,
 * while the underlying configs are populated with the settings in sequence.
 *
 * See also [ModuleCategory].
 *
 * @property configName The name of the configuration.
 * @property primary The primary file where the configuration is saved.
 * @property configs A set of [Config] objects that this configuration manages.
 */
abstract class ConfigCategory(
    val configName: String
) : Jsonable, Loadable {
    val primaryFile: File = FolderRegistry.config.resolve("${AutomationCategory.configName}.json").toFile()
    private val backup = File("${primaryFile.parent}/${primaryFile.nameWithoutExtension}-backup.${primaryFile.extension}")
    override val priority = 1

    val configs = mutableSetOf<Config>()

    final override fun load(): String {
        if (configCategories.any { it.configName == configName })
            throw IllegalStateException("Configuration with name $configName already exists")

        fixedRateTimer(
            daemon = true,
            name = "Scheduler-config-${configName}",
            initialDelay = 5.minutes.inWholeMilliseconds,
            period = 5.minutes.inWholeMilliseconds,
        ) { trySaveToFile() }

        configCategories.add(this)

        listenUnsafe<ClientEvent.Shutdown>({ Int.MIN_VALUE }) { trySaveToFile() }

        return super.load()
    }

    fun tryLoadFromFile() = runIO { internalTryLoad() }
    fun trySaveToFile(logToChat: Boolean = false) = runIO { internalTrySave(logToChat) }

    final override fun toJson() =
        JsonObject().apply {
            val latestSchemaVersion = ConfigMigrations.latestVersion(configName)
            if (latestSchemaVersion > 1) {
                addProperty(
                    ConfigMigrations.schemaVersionKey(configName) ?: ConfigMigrations.DefaultSchemaVersionKey,
                    latestSchemaVersion
                )
            }
            configs.forEach {
                add(it.name, it.toJson())
            }
        }

    final override fun loadFromJson(serialized: JsonElement) {
        val schemaKey = ConfigMigrations.schemaVersionKey(configName) ?: ConfigMigrations.DefaultSchemaVersionKey
        serialized.asJsonObject.entrySet().forEach { (name, value) ->
            if (name == schemaKey) return@forEach
            configByName(name)
                ?.loadFromJson(value)
                ?: Log.warn("No matching setting found for saved setting $name with $value in ${configName.capitalize()} config")
        }
    }

    protected open fun internalTryLoad() {
        loadFromFile(primaryFile)
            .onSuccess {
                val message = "${configName.capitalize()} config loaded."
                Log.info(message)
                info(message)
            }
            .onFailure { primaryError ->
                Log.error(primaryError)

                runCatching { loadFromFile(backup) }
                    .onSuccess {
                        val message = "${configName.capitalize()} config loaded from backup"
                        Log.info(message)
                        info(message)
                    }
                    .onFailure { error ->
                        val message = "Failed to load ${configName.capitalize()} config from backup, unrecoverable error"
                        Log.error(message, error)
                        logError(message)
                    }
            }
    }

    protected open fun internalTrySave(logToChat: Boolean) {
        saveToFile()
            .onSuccess {
                val message = "Saved ${configName.capitalize()} config."
                Log.info(message)
                if (logToChat) info(message)
            }
            .onFailure {
                val message = "Failed to save ${configName.capitalize()} config"
                Log.error(message, it)
                logError(message)
            }
    }

    /**
     * Loads the config from the [file]
     * Encapsulates [JsonIOException] and [JsonSyntaxException] in a runCatching block
     */
    private fun loadFromFile(file: File) = runCatching {
        file.ifNotExists { Log.warn("No configuration file found for ${configName.capitalize()}. Creating new file when saving.") }
            .ifExists {
                val parsed = JsonParser.parseReader(it.reader()).asJsonObject
                val migrationResult = ConfigMigrations.migrate(configName, parsed)

                if (migrationResult.migrated && file == primary) {
                    file.writeText(gson.toJson(migrationResult.json))
                    file.copyTo(backup, true)
                }

                loadFromJson(migrationResult.json)
            }
    }

    private fun saveToFile() = runCatching {
        primaryFile.createIfNotExists()
            .let {
                it.writeText(gson.toJson(toJson()))
                it.copyTo(backup, true)
            }
    }
}
