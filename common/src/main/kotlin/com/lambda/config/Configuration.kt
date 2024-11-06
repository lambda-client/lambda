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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lambda.Lambda.LOG
import com.lambda.Lambda.gson
import com.lambda.config.configurations.ModuleConfig
import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.threading.runIO
import com.lambda.util.Communication.info
import com.lambda.util.Communication.logError
import com.lambda.util.StringUtils.capitalize
import java.io.File
import java.time.Duration
import kotlin.concurrent.fixedRateTimer


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
        unsafeListener<ClientEvent.Startup> { tryLoad() }

        unsafeListener<ClientEvent.Shutdown>(Int.MIN_VALUE) { trySave() }

        register()
    }

    // Avoid context-leaking warning
    private fun register() {
        fixedRateTimer(
            daemon = true,
            name = "Scheduler-config-${configName}",
            initialDelay = Duration.ofMinutes(5).toMillis(),
            period = Duration.ofMinutes(5).toMillis()
        ) {
            trySave()
        }

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
            configurables.find {
                it.name == name
            }?.loadFromJson(value)
                ?: LOG.warn("No matching setting found for saved setting $name with $value in ${configName.capitalize()} config")
        }
    }

    private fun save() {
        with(primary) {
            if (exists()) copyTo(backup, true)

            parentFile.mkdirs()
            writeText(gson.toJson(toJson()))
        }
    }

    private fun load(file: File) {
        check(file.exists()) { "No configuration file found for ${configName.capitalize()}" }

        loadFromJson(JsonParser.parseReader(file.reader()).asJsonObject)
    }

    fun tryLoad() {
        runIO {
            runCatching { load(primary) }
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
                            message =
                                "Failed to load ${configName.capitalize()} config from backup, unrecoverable error"
                            LOG.error(message, error)
                            logError(message)
                        }
                }
        }
    }

    fun trySave(logToChat: Boolean = false) {
        runIO {
            runCatching { save() }
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
    }

    companion object {
        val configurations = mutableSetOf<Configuration>()
    }
}
