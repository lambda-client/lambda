package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.syntax.SyntaxChunk;
import me.zeroeightsix.kami.module.ModuleManager;

import java.util.Arrays;

/**
 * Created by 086 on 11/11/2017.
 */
public class HelpCommand extends Command {

    private static final Subject[] subjects = new Subject[]{
            new Subject(
                    new String[]{"type", "int", "boolean", "double", "float"},
                    new String[]{
                            "Every module has a value, and that value is always of a certain &btype.\n",
                            "These types are displayed in kami as the ones java use. They mean the following:",
                            "&bboolean&r: Enabled or not. Values &3true/false",
                            "&bfloat&r: A number with a decimal point",
                            "&bdouble&r: Like a float, but a more accurate decimal point",
                            "&bint&r: A number with no decimal point"
                    }
            )
    };
    private static String subjectsList = "";

    static {
        for (Subject subject : subjects)
            subjectsList += subject.names[0] + ", ";
        subjectsList = subjectsList.substring(0, subjectsList.length() - 2);
    }

    public HelpCommand() {
        super("help", new SyntaxChunk[]{}, "?");
        setDescription("Delivers help on certain subjects. Use &7" + Command.getCommandPrefix() + "help subjects&8 for a list.");
    }

    @Override
    public void call(String[] args) {
        if (args[0] == null) {
            Command.sendStringChatMessage(new String[]{
                    "KAMI Blue " + KamiMod.MODVER,
                    "&7Press &r" + ModuleManager.getModuleByName("ClickGUI").getBindName() + "&7 to open GUI",
                    "&7see &bhttps://blue.bella.wtf&7 for a full version of the faq",
                    "description&7 to see the description of a module",
                    "commands&7 to view all available commands",
                    "bind <module> <key>&7 to bind mods",
                    "prefix <prefix>&r to change the command prefix.",
                    "help &7<bind|subjects:[subject]>&r for more help."
            });
        } else {
            String subject = args[0];
            if (subject.equals("subjects")) {
                Command.sendChatMessage("Subjects: " + subjectsList);
            }
            else if (subject.equals("bind")) {
                Command.sendChatMessage("You can also use &7.bind&r modifiers on to allow modules to be bound to keybinds with modifiers, e.g &7ctrl + shift + w or ctrl + c.&r");
                Command.sendChatMessage("You can unbind modules with backspace in the GUI or by running &7.bind <module> none&r");
            }
            else {
                Subject subject1 = Arrays.stream(subjects).filter(subject2 -> {
                    for (String name : subject2.names)
                        if (name.equalsIgnoreCase(subject))
                            return true;
                    return false;
                }).findFirst().orElse(null);
                if (subject1 == null) {
                    Command.sendChatMessage("No help found for &b" + args[0]);
                    return;
                }
                Command.sendStringChatMessage(subject1.info);
            }
        }
    }

    private static class Subject {
        String[] names;
        String[] info;

        public Subject(String[] names, String[] info) {
            this.names = names;
            this.info = info;
        }
    }
}
