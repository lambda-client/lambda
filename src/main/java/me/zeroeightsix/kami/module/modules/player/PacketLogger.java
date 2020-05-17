package me.zeroeightsix.kami.module.modules.player;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.network.Packet;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


@Module.Info(
        name = "PacketLogger",
        description = "Logs sent packets to a file",
        category = Module.Category.PLAYER
)
public class PacketLogger extends Module {
    private final String filename = "KAMIBluePackets.txt";
    private List<String> lines = new ArrayList<>();
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

    public void onEnable() {
        readToList();
    }

    public void onDisable() {
        write();
    }

    @EventHandler
    public Listener<PacketEvent.Send> packetListener = new Listener<>(event -> {
        if (mc.player == null) return;
        addLine(event.getPacket());
    });

    private void addLine(Packet packet) {
        lines.add(FORMAT.format(new Date()) + " " + packet.getClass().getSimpleName() + "\n" + packet.getClass().toString() + "\n" + packet.toString() + "\n\n");
    }

    private void write() {
        try {
            FileWriter writer = new FileWriter(filename);

            for (String line : lines) {
                writer.write(line);
            }

            writer.close();
        } catch (IOException e) {
            KamiMod.log.error(getChatName() + "Error saving!");
            e.printStackTrace();
        }
    }

    private void readToList() {
        BufferedReader bufferedReader;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8));
            String line;
            lines.clear();
            while ((line = bufferedReader.readLine()) != null) {
                lines.add(line);
            }
            bufferedReader.close();
        } catch (IOException ignored) { }
    }
}
