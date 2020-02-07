package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author S-B99
 * Updated by S-B99 on 07/02/20
 */
public class EnabledCommand extends Command {
    public EnabledCommand() {
        super("enabledlist", null, "enabled");
        setDescription("Prints Enabled Modules");
    }

    @Override
    public void call(String[] args) {
        AtomicReference<String> enabled = new AtomicReference<>("");
        List<Module> mods = ModuleManager.getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());

        mods.forEach(module -> {
            if (module.isEnabled()) {
                enabled.set(String.join(", ", module.getName()));
            }
        });
        Command.sendChatMessage("Enabled modules: \n" + enabled);
    }

}
