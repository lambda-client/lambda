package me.zeroeightsix.kami.util;

import net.minecraft.client.Minecraft;

import java.io.FileWriter;
import java.io.IOException;

/**
 * @author S-B99
 * Created by S-B99 on 18/02/20
 */
public class LogUtil {
    public static int[] getCurrentCoord(boolean chunk) {
        Minecraft mc = Minecraft.getMinecraft();
        int[] currentCoords = {(int) mc.player.posX, (int) mc.player.posY, (int) mc.player.posZ};
        if (chunk == true) {
            int[] chunkCoords = {currentCoords[0]/16, currentCoords[1]/16, currentCoords[2]/16};
            return chunkCoords;
        } else {
            return currentCoords;
        }
    }

    public static void writePlayerCoords(String locationName, boolean chunk) {
        writeCoords(getCurrentCoord(chunk), "chunk: " + chunk + ", " + locationName);
    }

    public static void writeCoords(int[] xyz, String locationName) {
        try {
            FileWriter fW = new FileWriter("KAMIBlueCoords.txt", true);
            fW.write(formatter(xyz[0], xyz[1], xyz[2], locationName));
            fW.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String formatter(int x, int y, int z, String locationName) {
        return x + ", " + y + ", " + z + ", " + locationName + "\n";
    }
}
