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

package com.lambda.config

import com.google.gson.JsonElement
import com.google.gson.JsonIOException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.lambda.Lambda.LOG
import com.lambda.Lambda.gson
import com.lambda.config.Configuration.Companion.configurables
import com.lambda.config.configurations.ModuleConfigs
import com.lambda.core.Loadable
import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.threading.runIO
import com.lambda.util.Communication.info
import com.lambda.util.Communication.logError
import com.lambda.util.FileUtils.createIfNotExists
import com.lambda.util.FileUtils.ifExists
import com.lambda.util.FileUtils.ifNotExists
import com.lambda.util.StringUtils.capitalize
import java.io.File
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.minutes


/**
 * Represents a compound of [Configurable] objects whose [SettingCore]s
 * are saved into a single [Configuration] file ([Configuration.primary]).
 *
 * This class also handles the concurrent loading and saving of persisted data on the `Dispatchers.IO` thread.
 * Each configuration will be loaded concurrently,
 * while the underlying configurables are populated with the settings in sequence.
 *
 * See also [ModuleConfigs].
 *
 * @property configName The name of the configuration.
 * @property primary The primary file where the configuration is saved.
 * @property configurables A set of [Configurable] objects that this configuration manages.
 */
abstract class Configuration : Jsonable, Loadable {
    override val priority = 1
    abstract val configName: String
    abstract val primary: File

    val configurables = mutableSetOf<Configurable>()
    private val backup: File
        get() = File("${primary.parent}/${primary.nameWithoutExtension}-backup.${primary.extension}")

    override fun load(): String {
        listenUnsafe<ClientEvent.Shutdown>({ Int.MIN_VALUE }) { trySave() }
        register()
        return super.load()
    }

    // Avoid context-leaking warning
    private fun register() {
		if (configurations.any { it.configName == configName })
			throw IllegalStateException("Configuration with name $configName already exists")

        fixedRateTimer(
            daemon = true,
            name = "Scheduler-config-${configName}",
            initialDelay = 5.minutes.inWholeMilliseconds,
            period = 5.minutes.inWholeMilliseconds,
        ) { trySave() }

        configurations.add(this)
    }

    override fun toJson() =
        JsonObject().apply {
            configurables.forEach {
                add(it.name, it.toJson())
            }
        }

    override fun loadFromJson(serialized: JsonElement) {
        serialized.asJsonObject.entrySet().forEach { (name, value) ->
            configurableByName(name)
                ?.loadFromJson(value)
                ?: LOG.warn("No matching setting found for saved setting $name with $value in ${configName.capitalize()} config")
        }
    }

    private fun save() = runCatching {
        primary.createIfNotExists()
            .let {
                it.writeText(gson.toJson(toJson()))
                it.copyTo(backup, true)
            }
    }

    protected open fun internalTrySave(logToChat: Boolean) {
        save()
            .onSuccess {
                val message = "Saved ${configName.capitalize()} config."
                LOG.info(message)
                if (logToChat) info(message)
            }
            .onFailure {
                val message = "Failed to save ${configName.capitalize()} config"
                LOG.error(message, it)
                logError(message)
            }
    }

    /**
     * Loads the config from the [file]
     * Encapsulates [JsonIOException] and [JsonSyntaxException] in a runCatching block
     */
    private fun load(file: File) = runCatching {
        file.ifNotExists { LOG.warn("No configuration file found for ${configName.capitalize()}. Creating new file when saving.") }
            .ifExists { loadFromJson(JsonParser.parseReader(it.reader()).asJsonObject) }
    }

    protected open fun internalTryLoad() {
        load(primary)
            .onSuccess {
                val message = "${configName.capitalize()} config loaded."
                LOG.info(message)
                info(message)
            }
            .onFailure { primaryError ->
                LOG.error(primaryError)

                runCatching { load(backup) }
                    .onSuccess {
                        val message = "${configName.capitalize()} config loaded from backup"
                        LOG.info(message)
                        info(message)
                    }
                    .onFailure { error ->
                        val message = "Failed to load ${configName.capitalize()} config from backup, unrecoverable error"
                        LOG.error(message, error)
                        logError(message)
                    }
            }
    }

    fun tryLoad() = runIO { internalTryLoad() }
    fun trySave(logToChat: Boolean = false) = runIO { internalTrySave(logToChat) }

    companion object {
        val configurations = mutableSetOf<Configuration>()
        val configurables: Set<Configurable>
            get() = configurations.flatMapTo(mutableSetOf()) { it.configurables }
        val settings: List<Setting<*, *>>
            get() = configurables.flatMapTo(mutableListOf()) { it.settings }

        fun configurableByName(name: String) =
            configurables.find { it.name == name }

        fun configurableByCommandName(name: String) =
            configurables.find { it.commandName == name }

        fun settingByCommandName(configurable: Configurable, name: String) =
            configurable.settings.find { it.commandName == name }
    }
}
