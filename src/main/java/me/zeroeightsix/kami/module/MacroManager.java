package me.zeroeightsix.kami.module;

import me.zeroeightsix.kami.KamiMod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static me.zeroeightsix.kami.command.Command.getCommandPrefix;
import static me.zeroeightsix.kami.util.Macro.*;
import static me.zeroeightsix.kami.util.MessageSendHelper.sendKamiCommand;
import static me.zeroeightsix.kami.util.MessageSendHelper.sendServerMessage;

/**
 * @author dominikaaaa
 */
public class MacroManager {

    /*
     * Map of all the macros.
     * KeyCode, Actions
     */
    public static Map<String, List<String>> macros = new LinkedHashMap<>();

    /**
     * Reads macros from KAMIBlueMacros.json into the macros Map
     */
    public static void register() {
        KamiMod.log.info("Registering macros...");
        readFileToMemory();
        KamiMod.log.info("Macros registered");
    }

    /**
     * Saves macros from the macros Map into KAMIBlueMacros.json
     */
    public static void saveMacros() {
        KamiMod.log.info("Saving macros...");
        writeMemoryToFile();
        KamiMod.log.info("Macros saved");
    }

    /**
     * Sends the message or command, depending on which one it is
     * @param keyCode int keycode of the key the was pressed
     */
    public static void sendMacro(int keyCode) {
        List<String> macrosForThisKey = getMacrosForKey(keyCode);
        if (macrosForThisKey == null) return;

        for (String currentMacro : macrosForThisKey) {
            if (currentMacro.startsWith(getCommandPrefix())) { // this is done instead of just sending a chat packet so it doesn't add to the chat history
                sendKamiCommand(currentMacro, false); // ie, the false here
            } else {
                sendServerMessage(currentMacro);
            }
        }
    }
}
