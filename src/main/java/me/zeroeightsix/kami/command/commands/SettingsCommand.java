package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.syntax.ChunkBuilder;
import me.zeroeightsix.kami.command.syntax.parsers.ModuleParser;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.SettingsClass;

import java.util.ArrayList;

/**
 * Created by 086 on 11/12/2017.
 */
public class SettingsCommand extends Command {
    public SettingsCommand() {
        super("settings", new ChunkBuilder()
            .append("module", true, new ModuleParser())
            .build());
    }

    @Override
    public void call(String[] args) {
        if (args[0]==null) {
            Command.sendChatMessage("Please specify a module to display the settings of.");
            return;
        }

        Module m = ModuleManager.getModuleByName(args[0]);
        if (m == null) {
            Command.sendChatMessage("Couldn't find a module &b" + args[0] + "!");
            return;
        }

        ArrayList<SettingsClass.StaticSetting> settings = m.getSettings();
        String[] result = new String[settings.size()];
        for (int i = 0; i < settings.size(); i++) {
            SettingsClass.StaticSetting staticSetting = settings.get(i);
            result[i] = "&b" + staticSetting.getDisplayName() + "&3(=" + staticSetting.getValue() + ")  &ftype: &3" + staticSetting.getField().getType().getSimpleName();

            if (staticSetting.getField().getType().isEnum()){
                result[i] += "  (";
                Enum[] enums = (Enum[]) staticSetting.getField().getType().getEnumConstants();
                for (Enum e : enums)
                    result[i] += e.name() + ", ";
                result[i] = result[i].substring(0, result[i].length()-2) + ")";
            }
        }
        Command.sendStringChatMessage(result);
    }
}
