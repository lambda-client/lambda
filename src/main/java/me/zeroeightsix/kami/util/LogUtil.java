package me.zeroeightsix.kami.util;

import net.minecraft.client.Minecraft;

import java.io.FileWriter;
import java.io.IOException;

/**
 * @author S-B99
 * Created by S-B99 on 18/02/20
 */
public class LogUtil {
    public static void writePlayerCoords(String locationName) {
        Minecraft mc = Minecraft.getMinecraft();
        writeCoords((int) mc.player.posX, (int) mc.player.posY, (int) mc.player.posZ, locationName);
    }

    public static void writeCoords(int x, int y, int z, String locationName) {
        try {
            FileWriter fW = new FileWriter("KAMIBlueCoords.txt", true);
            fW.write(formatter(x, y, z, locationName));
            fW.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String formatter(int x, int y, int z, String locationName) {
        return x + ", " + y + ", " + z + ", " + locationName + "\n";
    }
}
