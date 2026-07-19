package com.minato.gui.components

import com.minato.Minato.mapper
import com.minato.module.HudModule
import com.minato.module.ModuleRegistry
import com.minato.util.FolderRegistry
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.notExists

/**
 * A snapshot of all HUD module positions, scales and enabled states.
 */
data class HudPreset(
    val name: String,
    val slots: Map<String, HudSlotData>,
) {
    data class HudSlotData(
        val x: Float,
        val y: Float,
        val scale: Float,
        val enabled: Boolean,
    )

    companion object {
        private val presetsDir: Path
            get() = FolderRegistry.hudPresets

        /**
         * Take a snapshot of all current [HudModule] positions/scales/enabled states.
         */
        fun snapshot(name: String): HudPreset {
            val slots = ModuleRegistry.modules
                .filterIsInstance<HudModule>()
                .associate { module ->
                    module.name to HudSlotData(
                        x = module.hudX.value,
                        y = module.hudY.value,
                        scale = module.hudScale.value,
                        enabled = module.isEnabled,
                    )
                }
            return HudPreset(name = name, slots = slots)
        }

        /** File path for a given preset name — sanitizes to avoid path traversal. */
        private fun fileFor(name: String): File {
            val safeName = name.replace(Regex("[\\\\/:?*|<>\"]"), "_")
            return presetsDir.resolve("$safeName.json").toFile()
        }

        /**
         * Save this preset to disk as JSON using Jackson.
         */
        fun save(preset: HudPreset) {
            if (presetsDir.notExists()) presetsDir.createDirectories()
            mapper.writeValue(fileFor(preset.name), preset)
        }

        /**
         * Load a preset from disk by name.
         */
        fun load(name: String): HudPreset? = runCatching {
            val file = fileFor(name)
            if (!file.exists()) return@runCatching null
            mapper.readValue(file, HudPreset::class.java)
        }.getOrNull()

        /**
         * List all saved preset names (sorted).
         */
        fun listPresets(): List<String> {
            if (presetsDir.notExists()) return emptyList()
            return presetsDir.toFile()
                .listFiles { f -> f.extension == "json" }
                ?.map { it.nameWithoutExtension }
                ?.sorted()
                ?: emptyList()
        }

        /**
         * Delete a saved preset by name.
         */
        fun delete(name: String): Boolean = fileFor(name).delete()

        /**
         * Apply this preset to all HUD modules — sets x, y, scale and enable/disable.
         */
        fun apply(snapshot: HudPreset) {
            val modulesByName = ModuleRegistry.modules
                .filterIsInstance<HudModule>()
                .associateBy { it.name }

            snapshot.slots.forEach { (moduleName, data) ->
                val module = modulesByName[moduleName] ?: return@forEach
                module.hudX.value = data.x
                module.hudY.value = data.y
                module.hudScale.value = data.scale
                if (data.enabled) {
                    module.enable()
                } else {
                    module.disable()
                }
            }
        }
    }
}
