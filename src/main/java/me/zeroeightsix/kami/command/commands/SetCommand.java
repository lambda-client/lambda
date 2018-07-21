package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.syntax.ChunkBuilder;
import me.zeroeightsix.kami.command.syntax.parsers.ModuleParser;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.SettingsClass;

/**
 * Created by 086 on 18/11/2017.
 */
public class SetCommand extends Command {

    public SetCommand() {
        super("set", new ChunkBuilder()
                .append("module", true, new ModuleParser())
                .append("setting", true)
                .append("value", true)
                .build());
    }

    @Override
    public void call(String[] args) {
        if (args[0] == null) {
            Command.sendChatMessage("Please specify a module!");
            return;
        }

        Module m = ModuleManager.getModuleByName(args[0]);
        if (m == null) {
            Command.sendChatMessage("Unknown module &b" + args[0] + "&r!");
            return;
        }

        if (args[1] == null) {
            final String[] settings = {""};
            m.getSettings().stream().forEach(staticSetting -> settings[0]+=staticSetting.getDisplayName()+", ");
            if (settings[0].isEmpty())
                Command.sendChatMessage("Module &b" + m.getName() + "&r has no settings.");
            else{
                Command.sendStringChatMessage(new String[]{
                        "Please specify a setting! Choose one of the following:",
                        settings[0].substring(0,settings[0].length()-2)
                });
            }
            return;
        }

        SettingsClass.StaticSetting setting = m.getSettingByDisplayName(args[1]);
        if (setting == null) {
            Command.sendChatMessage("Unknown setting &b" + args[1] + "&r in &b" + m.getName() + "&r!");
            return;
        }

        if (args[2] == null) {
            Command.sendChatMessage("&b" + setting.getDisplayName() + "&r is a &3" + setting.getField().getType().getSimpleName() + "&r. Its current value is &3" + setting.getValue());
            return;
        }

        try{
            setting.setValue(args[2]);
            Command.sendChatMessage("Set &b" + setting.getDisplayName() + "&r to &3" + args[2] + "&r.");
        }catch (Exception e) {
            e.printStackTrace();
            Command.sendChatMessage("Unable to set value! &6" + e.getMessage());
        }
    }
}
