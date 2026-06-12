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

import com.lambda.Lambda.LOG
import com.lambda.Lambda.mapper
import com.lambda.config.ConfigLoader.configCategories
import com.lambda.config.categories.ModuleCategory
import com.lambda.config.migration.ConfigMigrationHandler
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
import java.io.File
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.minutes

/**
 * Represents a compound of [Config] objects whose [EntryCore]s
 * are saved into a single [ConfigCategory] file ([ConfigCategory.primaryFile]).
 *
 * This class also handles the concurrent loading and saving of persisted data on the `Dispatchers.IO` thread.
 * Each configuration will be loaded concurrently,
 * while the underlying configs are populated with the settings in sequence.
 *
 * See also [ModuleCategory].
 *
 * @property name The name of the configuration.
 * @property primary The primary file where the configuration is saved.
 * @property configs A set of [Config] objects that this configuration manages.
 */
abstract class ConfigCategory : Loadable {
    abstract val name: String
    abstract val primaryFile: File
    private val backup get() = File("${primaryFile.parent}/${primaryFile.nameWithoutExtension}-backup.${primaryFile.extension}")
    override val priority = 1

    val configs = mutableSetOf<Config>()

    final override fun load(): String {
        if (configCategories.any { it.name == name })
            throw IllegalStateException("Config category with name $name already exists")

        fixedRateTimer(
            daemon = true,
            name = "Scheduler-config-category-${name}",
            initialDelay = 5.minutes.inWholeMilliseconds,
            period = 5.minutes.inWholeMilliseconds,
        ) { trySaveToFile() }

        configCategories.add(this)

        listenUnsafe<ClientEvent.Shutdown>({ Int.MIN_VALUE }) { trySaveToFile() }

        return super.load()
    }

    fun tryLoadFromFile() = runIO { internalTryLoad() }
    fun trySaveToFile(logToChat: Boolean = false) = runIO { internalTrySave(logToChat) }

    protected open fun internalTryLoad() {
        loadFromFile(primaryFile)
            .onSuccess {
                val message = "$name config category loaded."
                LOG.info(message)
                info(message)
            }
            .onFailure { primaryError ->
                LOG.error(primaryError)

                runCatching { loadFromFile(backup).getOrThrow() }
                    .onSuccess {
                        val message = "$name config category loaded from backup"
                        LOG.info(message)
                        info(message)
                    }
                    .onFailure { error ->
                        val message = "Failed to load $name config category from backup, unrecoverable error"
                        LOG.error(message, error)
                        logError(message)
                    }
            }
    }

    protected open fun internalTrySave(logToChat: Boolean) {
        saveToFile()
            .onSuccess {
                val message = "Saved $name category."
                LOG.info(message)
                if (logToChat) info(message)
            }
            .onFailure {
                val message = "Failed to save $name category"
                LOG.error(message, it)
                logError(message)
            }
    }

    private fun loadFromFile(file: File) = runCatching {
        file.ifNotExists { LOG.warn("No config file found for $name. Creating new file when saving.") }
            .ifExists {
                val parsed = mapper.readTree(it)
                if (!parsed.isObject) return@ifExists
                val migrationResult = ConfigMigrationHandler.migrate(this@ConfigCategory, parsed.asObject())

                if (migrationResult.migrated && file == primaryFile) {
                    mapper.writeValue(file, migrationResult.json)
                    file.copyTo(backup, true)
                }

                mapper.updateValue(this@ConfigCategory, migrationResult.json)
            }
    }

    private fun saveToFile() = runCatching {
        primaryFile.createIfNotExists()
            .let {
                mapper.writeValue(it, this)
                it.copyTo(backup, true)
            }
    }
}
