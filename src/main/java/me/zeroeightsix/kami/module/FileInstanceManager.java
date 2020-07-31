package me.zeroeightsix.kami.module;

import me.zeroeightsix.kami.util.WaypointInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author dominikaaaa
 * Lazy fix used for Java instance of {@link me.zeroeightsix.kami.util.Macro} and {@link MacroManager}
 */
public class FileInstanceManager {
    /*
     * Map of all the macros.
     * KeyCode, Actions
     */
    public static Map<Integer, List<String>> macros = new LinkedHashMap<>();

    public static ArrayList<WaypointInfo> waypoints = new ArrayList<>();
}
