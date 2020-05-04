package me.zeroeightsix.kami.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.util.*;

import static me.zeroeightsix.kami.module.MacroManager.macros;
import static me.zeroeightsix.kami.util.MessageSendHelper.sendErrorMessage;

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
            e.printStackTrace();
        }
    }

    public static List<String> getMacrosAsArray(int keyCode) {
        for (Map.Entry<String, List<String>> entry : macros.entrySet()) {
            if (keyCode == Integer.parseInt(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static void addMacroToKey(String keyCode, String macro) {
        boolean exists = false;
        int found = 0;
        for (Map.Entry<String, List<String>> entry : macros.entrySet()) {
            if (entry.getKey().equals(keyCode)) {
                entry.getValue().add(macro);
                exists = true;
                found++;
            }
        }
        if (!exists) {
            macros.put(keyCode, Collections.singletonList(macro));
            found++;
        }
        if (found == 0) {
            sendErrorMessage("Error adding macro '" + macro + "' with keycode " + keyCode);
        } else if (found != 1) {
            sendErrorMessage("Error: duplicate macros with the keycode " + keyCode + " found, this shouldn't ever happen but you need to edit your macros file manually. Contact the developers if this happens often.");
        }
    }

//    private static void clearMacrosFromDisk() {
//        PrintWriter pw = null;
//        try {
//            pw = new PrintWriter(CONFIG_NAME);
//        } catch (FileNotFoundException exception) {
//            exception.printStackTrace();
//        }
//        Objects.requireNonNull(pw).close();
//    }
}