package me.zeroeightsix.kami.command.syntax.parsers;

import me.zeroeightsix.kami.command.syntax.SyntaxChunk;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.SettingsClass;

import java.util.HashMap;
import java.util.TreeMap;

public class ValueParser extends AbstractParser {

    int moduleIndex;

    public ValueParser(int moduleIndex) {
        this.moduleIndex = moduleIndex;
    }

    public String getChunk(SyntaxChunk[] chunks, SyntaxChunk thisChunk, String[] values, String chunkValue) {
        if (moduleIndex>values.length-1 || chunkValue == null) return getDefaultChunk(thisChunk);
        String module = values[moduleIndex];
        Module m = ModuleManager.getModuleByName(module);
        if (m == null) return "";

        HashMap<String, SettingsClass.StaticSetting> possibilities = new HashMap<>();

        for (SettingsClass.StaticSetting v : m.getSettings()){
            if (v.getDisplayName().toLowerCase().startsWith(chunkValue.toLowerCase()))
                possibilities.put(v.getDisplayName(), v);
        }

        if (possibilities.isEmpty()) return "";

        TreeMap<String, SettingsClass.StaticSetting> p = new TreeMap<String, SettingsClass.StaticSetting>(possibilities);
        SettingsClass.StaticSetting aV = p.firstEntry().getValue();
        return aV.getDisplayName().substring(chunkValue.length());
    }
}
