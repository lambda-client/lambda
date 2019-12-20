package me.zeroeightsix.kami.module.modules.bewwawho.misc;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.event.events.PacketEvent;
//import me.zeroeightsix.kami.event.events.GuiScreenEvent;
//import me.zeroeightsix.kami.util.Wrapper;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;

import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
//import net.minecraft.client.gui.inventory.GuiEditSign;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.stream.IntStream;
import java.util.Random;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

/**
 * Created on 16 December by 0x2E | PretendingToCode
 *
 * If someone can come up with a way to automatically close the sign GUI
 * that would be amazing, thanks
 */
@Module.Info(name = "LagSign", description = "Makes large, laggy signs", category = Module.Category.MISC)
public class LagSign extends Module {

    private Setting<Integer> characters = register(Settings.integerBuilder("Characters").withMinimum(1).withValue(375).withMaximum(375).build());

    private boolean exit = false;

    public void onUpdate() {
        if (exit) {
            mc.displayGuiScreen(null);
            Command.sendChatMessage("exit false");
            exit = false;
        }
    }

    @EventHandler
    public Listener<PacketEvent.Send> sendListener = new Listener<>(event -> {
        if (event.getPacket() instanceof CPacketUpdateSign) {
            String[] lines = ((CPacketUpdateSign) event.getPacket()).lines;
            for (int i = 0; i < 4; i++) {
                IntStream characterGenerator = new Random().ints(0x80, 0x10ffff - 0x800).map(r -> r < 0xd800 ? r : r + 0x800);
                lines[i] = characterGenerator.limit(characters.getValue()).mapToObj(r -> String.valueOf((char) r)).collect(Collectors.joining());
                if (i == 3) {
                    Command.sendChatMessage("exit true");
                    exit = true;
                }
            }
        }
    });
}
