package com.lambda.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lambda.Lambda.LOG
import com.lambda.Lambda.gson
import com.lambda.event.EventFlow.lambdaScope
import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.util.Communication.info
import com.lambda.util.Communication.logError
import com.lambda.util.StringUtils.capitalize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

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
            if (exists()) {
                copyTo(backup, true)
            }
            parentFile.mkdirs()
            writeText(gson.toJson(toJson()))
        }
    }

    private fun load(file: File) {
        check(file.exists()) {
            "No configuration file found for ${configName.capitalize()}"
        }

        loadFromJson(JsonParser.parseReader(file.reader()).asJsonObject)
    }

    fun tryLoad() {
        lambdaScope.launch(Dispatchers.IO) {
            runCatching { load(primary) }
                .onSuccess {
                    val message = "${configName.capitalize()} config loaded."
                    LOG.info(message)
                    this@Configuration.info(message)
                }
                .onFailure {
                    val message = "Failed to load ${configName.capitalize()} config, loading backup"
                    LOG.error(message)
                    this@Configuration.logError(message)
                    runCatching { load(backup) }
                        .onSuccess {
                            val message = "${configName.capitalize()} config loaded from backup"
                            LOG.info(message)
                            this@Configuration.info(message)
                        }
                        .onFailure {
                            val message = "Failed to load ${configName.capitalize()} config from backup, unrecoverable error"
                            LOG.error(message, it)
                            this@Configuration.logError(message)
                        }
                }
        }
    }

    fun trySave() {
        lambdaScope.launch(Dispatchers.IO) {
            runCatching { save() }
                .onSuccess {
                    val message = "Saved ${configName.capitalize()} config."
                    LOG.info(message)
                    this@Configuration.info(message)
                }
                .onFailure {
                    val message = "Failed to save ${configName.capitalize()} config"
                    LOG.error(message, it)
                    this@Configuration.logError(message)
                }
        }
    }

    companion object {
        val configurations = mutableSetOf<Configuration>()
    }
}
