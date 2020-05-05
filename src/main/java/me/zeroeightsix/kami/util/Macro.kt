package me.zeroeightsix.kami.util

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.zeroeightsix.kami.KamiMod
import me.zeroeightsix.kami.module.Macros
import java.io.*
import java.util.*

/**
 * @author dominikaaaa
 * Created by dominikaaaa on 04/05/20
 */
object Macro {
    private val gson = GsonBuilder().create()
    private const val configName = "KAMIBlueMacros.json"
    private val file = File(configName)

    fun writeMemoryToFile() {
        try {
            val fw = FileWriter(file, false)
            gson.toJson(Macros.macros, fw)
            fw.flush()
            fw.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun readFileToMemory() {
        try {
            Macros.macros = gson.fromJson(FileReader(file), object : TypeToken<HashMap<String?, List<String?>?>?>() {}.type)
        } catch (e: FileNotFoundException) {
            KamiMod.log.warn("Could not find file $configName, clearing the macros list")
            Macros.macros.clear()
        }
    }

    fun getMacrosForKey(keyCode: Int): List<String?>? {
        for ((key, value) in Macros.macros) {
            if (keyCode == key.toInt()) {
                return value
            }
        }
        return null
    }

    fun addMacroToKey(keyCode: String?, macro: String?) {
        if (macro == null) return  // prevent trying to add a null macro
        Macros.macros.getOrPut(keyCode, ::mutableListOf).add(macro)
    }

    fun removeMacro(keyCode: String) {
        for (entry in Macros.macros.entries) {
            if (entry.key == keyCode) {
                entry.setValue(null)
            }
        }
    }
}