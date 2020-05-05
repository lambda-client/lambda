package me.zeroeightsix.kami.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.zeroeightsix.kami.KamiMod;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static me.zeroeightsix.kami.module.MacroManager.macros;

/**
 * @author dominikaaaa
 * TODO: rewrite in Kotlin once tested and working
 * TODO: register getMacrosAsArray() in {@link me.zeroeightsix.kami.event.ForgeEventProcessor}
 */
public class Macro {
    private static Gson gson = new GsonBuilder().create();
    public static final String CONFIG_NAME = "KAMIBlueMacros.json";
    private static File file = new File(CONFIG_NAME);

    public static void writeMemoryToFile() {
        try {
            FileWriter fw = new FileWriter(file, false);
            gson.toJson(macros, fw);
            fw.flush();
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void readFileToMemory() {
        try {
            macros = gson.fromJson(new FileReader(file), new TypeToken<HashMap<String, List<String>>>() {}.getType());
        } catch (FileNotFoundException e) {
            KamiMod.log.warn("Could not find file " + CONFIG_NAME + ", clearing the macros list");
            macros.clear();
        }
    }

    public static List<String> getMacrosForKey(int keyCode) {
        for (Map.Entry<String, List<String>> entry : macros.entrySet()) {
            if (keyCode == Integer.parseInt(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static void addMacroToKey(String keyCode, String macro) {
        if (macro == null) return; // prevent trying to add a null macro
        macros.computeIfAbsent(keyCode, (key) -> new LinkedList()).add(macro);
    }

    public static void removeMacro(String keyCode) {
        for (Map.Entry<String, List<String>> entry : macros.entrySet()) {
            if (entry.getKey().equals(keyCode)) {
                entry.setValue(null);
            }
        }
    }
}