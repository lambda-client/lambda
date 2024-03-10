package com.lambda.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.gson
import com.lambda.event.EventFlow.lambdaScope
import com.lambda.event.events.GameEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

abstract class Configuration : Jsonable {
    abstract val configName: String
    abstract val primary: File

    val configurables = mutableSetOf<Configurable>()
    private val backup: File
        get() = File("${primary.parent}/${primary.nameWithoutExtension}-backup.${primary.extension}")

    init {
        unsafeListener<GameEvent.Startup> { tryLoad() }

        unsafeListener<GameEvent.Shutdown> { trySave() }
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
            }?.loadFromJson(value) ?: LOG.warn("No matching setting found for saved setting $name with $value in $configName config")
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
            "No configuration file found for $configName"
        }

        loadFromJson(JsonParser.parseReader(file.reader()).asJsonObject)
    }

    private fun tryLoad() {
        lambdaScope.launch(Dispatchers.IO) {
            runCatching { load(primary) }
                .onSuccess { LOG.info("$configName config loaded") }
                .onFailure { LOG.error("Failed to load $configName config, loading backup", it) }
                .recoverCatching {
                    runCatching { load(backup) }
                        .onSuccess { LOG.info("$configName config loaded from backup") }
                        .onFailure { LOG.error("Failed to load $configName config from backup, unrecoverable error", it) }
                        .isSuccess
                }
                .isSuccess
        }
    }

    private fun trySave() {
        lambdaScope.launch(Dispatchers.IO) {
            runCatching { save() }
                .onSuccess { LOG.info("$configName config saved") }
                .onFailure { LOG.error("Failed to save $configName config", it) }
                .isSuccess
        }
    }

}