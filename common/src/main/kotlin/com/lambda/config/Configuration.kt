/*
 * Copyright 2024 Lambda
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
import com.lambda.config.configurations.ModuleConfig
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
 * Represents a compound of [Configurable] objects whose [AbstractSetting]s
 * are saved into a single [Configuration] file ([Configuration.primary]).
 *
 * This class also handles the concurrent loading and saving of persisted data on the `Dispatchers.IO` thread.
 * Each configuration will be loaded concurrently,
 * while the underlying configurables are populated with the settings in sequence.
 *
 * See also [ModuleConfig].
 *
 * @property configName The name of the configuration.
 * @property primary The primary file where the configuration is saved.
 * @property configurables A set of [Configurable] objects that this configuration manages.
 */
abstract class Configuration : Jsonable {
    abstract val configName: String
    abstract val primary: File

    val configurables = mutableSetOf<Configurable>()
    private val backup: File
        get() = File("${primary.parent}/${primary.nameWithoutExtension}-backup.${primary.extension}")

    init {
        // We need to implement a dependency graph of loadables and add functions to run before
        // and/or after a given Loadable children class is initialized
        //
        // class Load1(
        //     override val priority = 1000,
        //     override val before = Load2,
        // ) : Loadable {}
        //
        // class Load2(
        //     override val priority = 1000,
        // ) : Loadable {}
        //
        // class Load3(
        //     override val priority = 1000,
        //     override val after = Load2,
        // ) : Loadable {}
        //
        // clientLifecycle<Load2>(shift = Pre) { event -> } // Will run before Load2
        // clientLifecycle<Load2>(shift = Post) { event -> } // Will run after Load2
        listenUnsafe<ClientEvent.Startup> { tryLoad() }
        listenUnsafe<ClientEvent.Shutdown>(Int.MIN_VALUE) { trySave() }

        register()
    }

    // Avoid context-leaking warning
    private fun register() {
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

    fun save() = runCatching {
        primary.createIfNotExists()
            .let {
                it.writeText(gson.toJson(toJson()))
                it.copyTo(backup, true)
            }
    }

    /**
     * Loads the config from the [file]
     * Encapsulates [JsonIOException] and [JsonSyntaxException] in a runCatching block
     */
    fun load(file: File) = runCatching {
        file.ifNotExists { LOG.warn("No configuration file found for ${configName.capitalize()}. Creating new file when saving.") }
            .ifExists { loadFromJson(JsonParser.parseReader(it.reader()).asJsonObject) }
    }

    fun tryLoad() = runIO {
        load(primary)
            .onSuccess {
                val message = "${configName.capitalize()} config loaded."
                LOG.info(message)
                info(message)
            }
            .onFailure {
                var message: String
                runCatching { load(backup) }
                    .onSuccess {
                        message = "${configName.capitalize()} config loaded from backup"
                        LOG.info(message)
                        info(message)
                    }
                    .onFailure { error ->
                        message = "Failed to load ${configName.capitalize()} config from backup, unrecoverable error"
                        LOG.error(message, error)
                        logError(message)
                    }
            }
    }

    fun trySave(logToChat: Boolean = false) = runIO {
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

    companion object {
        val configurations = mutableSetOf<Configuration>()
        val configurables: Set<Configurable>
            get() = configurations.flatMapTo(mutableSetOf()) { it.configurables }
        val settings: Set<AbstractSetting<*>>
            get() = configurables.flatMapTo(mutableSetOf()) { it.settings }

        //ToDo: Store owner in setting
        fun configurableBySetting(setting: AbstractSetting<*>) =
            configurables.find { it.settings.contains(setting) }
        fun configurableByName(name: String) =
            configurables.find { it.name == name }
        fun configurableByCommandName(name: String) =
            configurables.find { it.commandName == name }

        fun settingByName(configurable: Configurable, name: String) =
            configurable.settings.find { it.name == name }
        fun settingByCommandName(configurable: Configurable, name: String) =
            configurable.settings.find { it.commandName == name }
    }
}
