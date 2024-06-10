package com.lambda.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lambda.Lambda.LOG
import com.lambda.Lambda.gson
import com.lambda.config.configurations.ModuleConfig
import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.util.FolderRegister
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
abstract class Configuration(
    val configName: String,
) : Jsonable {
    private val primary = FolderRegister.config.resolve("$configName.json")
    private val backup = File("${primary.parent}/${primary.nameWithoutExtension}-backup.${primary.extension}")

    val configurables = mutableSetOf<Configurable>()

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

    fun tryLoad() =
        runCatching { load(primary) }
            .onSuccess {
                val message = "${configName.capitalize()} config loaded."
                LOG.info(message)
            }
            .onFailure {
                runCatching { load(backup) }
                    .onSuccess {
                        LOG.info("${configName.capitalize()} config loaded from backup.")
                    }
                    .onFailure { backupError ->
                        LOG.error(
                            "Failed to load ${configName.capitalize()} config from backup, unrecoverable error.",
                            backupError
                        )
                    }
            }
            .exceptionOrNull()

    fun trySave() =
        runCatching { save() }
            .onSuccess {
                val message = "Saved ${configName.capitalize()} config."
                LOG.info(message)
            }
            .onFailure {
                val message = "Failed to save ${configName.capitalize()} config"
                LOG.error(message, it)
            }
            .exceptionOrNull()

    companion object {
        val configurations = mutableSetOf<Configuration>()
    }
}
