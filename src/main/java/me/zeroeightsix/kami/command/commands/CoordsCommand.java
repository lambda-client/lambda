package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.syntax.ChunkBuilder;
import me.zeroeightsix.kami.command.syntax.parsers.EnumParser;
import me.zeroeightsix.kami.module.modules.movement.AutoWalk;
import me.zeroeightsix.kami.util.Coordinate;
import me.zeroeightsix.kami.util.CoordinateInfo;
import me.zeroeightsix.kami.util.MessageSendHelper;

import java.util.ArrayList;
import java.util.Objects;

import static me.zeroeightsix.kami.KamiMod.MODULE_MANAGER;
import static me.zeroeightsix.kami.util.CoordUtil.*;
import static me.zeroeightsix.kami.util.MessageSendHelper.sendChatMessage;
import static me.zeroeightsix.kami.util.MessageSendHelper.sendRawChatMessage;

/**
 * @author wnuke
 * Created by wnuke on 17/04/20
 */

public class CoordsCommand extends Command {
    public CoordsCommand() {
        super("coord", new ChunkBuilder()
                .append("command", true, new EnumParser(new String[]{"add", "del", "goto", "list", "stashes", "help"}))
                .append("name|id", false)
                .append("custom coord", false)
                .build(), "pos");
        setDescription("Log the current coordinates.");
    }

    public void call(String[] args) {
        if (args[0] != null) {
            switch (args[0].toLowerCase()) {
                case "add":
                    if (args[1] != null) {
                        if (args[2] != null) {
                            if (!args[2].matches("[0-9-]+,[0-9-]+,[0-9-]+")) {
                                sendChatMessage("You have to enter custom coordinates in the format of '&7x,y,z&f', for example '&7400,60,-100&f', but you can also leave it blank to use the current coordinates");
                                return;
                            }
                            String[] split = args[2].split(",");
                            Coordinate coordinate = new Coordinate(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
                            confirm(args[1], writeCustomCoords(coordinate, args[1]));
                        } else {
                            confirm(args[1], writePlayerCoords(args[1]));
                        }
                    } else {
                        confirm("Unnamed", writePlayerCoords("Unnamed"));
                    }
                    break;
                case "list":
                    if (args[1] != null) {
                        searchCoords(args[1]);
                    } else {
                        listCoords(false);
                    }
                    break;
                case "stashes":
                    listCoords(true);
                    break;
                case "del":
                    if (args[1] != null) {
                        if (removeCoord(args[1], coordsLogFilename)) {
                            sendChatMessage("Removed coordinate with ID " + args[1]);
                        } else {
                            sendChatMessage("No coordinate with ID " + args[1]);
                        }
                    } else {
                        sendChatMessage("Please provide the ID of a coordinate to remove.");
                    }
                    break;
                case "goto":
                    if (args[1] != null) {
                        Coordinate current = getCoord(args[1], coordsLogFilename);
                        if (current != null) {
                            if (MODULE_MANAGER.isModuleEnabled(AutoWalk.class)) {
                                MODULE_MANAGER.getModuleT(AutoWalk.class).disable();
                            }
                            MessageSendHelper.sendBaritoneCommand("goto", Integer.toString(current.x), Integer.toString(current.y), Integer.toString(current.z));
                        } else {
                            sendChatMessage("Couldn't find a coordinate with the ID " + args[1]);
                        }
                    } else {
                        sendChatMessage("Please provide the ID of a coordinate to go to.");
                    }
                    break;
                case "help":
                    sendChatMessage("Coordinate command help:");
                    sendRawChatMessage("  list &7<search>&f - lists logged coordinates, optionally searches and filters the results");
                    sendRawChatMessage("  stashes - lists logged stashes");
                    sendRawChatMessage("  add &7<name>&f - logs a coordinate with an optional name");
                    sendRawChatMessage("  del &7<id>&f - removes a coordinate with the specified ID");
                    sendRawChatMessage("  goto &7<id>&f - goes to a coordinate with the specified ID");
                    sendRawChatMessage("  help - displays this list");
                    break;
                default:
                    sendChatMessage("Please use a valid command (add, del, list, stashes or help");
                    break;
            }
        } else {
            sendChatMessage("Please choose a command (list or save)");
        }
    }

    private void listCoords(boolean stashes) {
        ArrayList<CoordinateInfo> coords = readCoords(coordsLogFilename);
        if (coords.isEmpty()) {
            if (!stashes) {
                sendChatMessage("No coordinates have been logged.");
            } else {
                sendChatMessage("No stashes have been logged.");
            }
        } else {
            if (!stashes) {
                sendChatMessage("List of logged coordinates:");
            } else {
                sendChatMessage("List of logged stashes:");
            }
            String stashRegex = "(\\(.* chests, .* shulkers, .* droppers, .* dispensers\\))";
            Objects.requireNonNull(coords).forEach(coord -> {
                if (stashes) {
                    if (coord.name.matches(stashRegex)) {
                        sendRawChatMessage(format(coord, ""));
                    }
                } else {
                    if (!coord.name.matches(stashRegex)) {
                        sendRawChatMessage(format(coord, ""));
                    }
                }
            });
        }
    }

    private void searchCoords(String search) {
        boolean hasfound = false;
        boolean firstfind = true;
        ArrayList<CoordinateInfo> coords = readCoords(coordsLogFilename);
        for (CoordinateInfo coord : Objects.requireNonNull(coords)) {
            if (coord.name.contains(search)) {
                if (firstfind) {
                    sendChatMessage("Result of search for &7" + search + "&f: ");
                    firstfind = false;
                }
                sendRawChatMessage(format(coord, search));
                hasfound = true;
            }
        }
        if (!hasfound) {
            sendChatMessage("No results for " + search);
        }
    }

    private String format(CoordinateInfo coord, String search) {
        String message = "   [" + coord.id + "] " + coord.name + " (" + coord.xyz.x + " " + coord.xyz.y + " " + coord.xyz.z + ")";
        return message.replaceAll(search, "&7" + search + "&f");
    }

    private void confirm(String name, Coordinate xyz) {
        sendChatMessage("Added coordinate " + xyz.x + " " + xyz.y + " " + xyz.z + " with name " + name + ".");
    }
}