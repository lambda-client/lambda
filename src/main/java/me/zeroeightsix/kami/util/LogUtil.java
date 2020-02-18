package me.zeroeightsix.kami.util;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author S-B99
 * Created by S-B99 on 18/02/20
 */
public class LogUtil {
    private static String COORDS_FILE_NAME = "KAMIBlueCoords.txt";
    private static final File coordsFileName = new File(COORDS_FILE_NAME);

    public static void writePlayerCoords(String locationName) {
        Minecraft mc = Minecraft.getMinecraft();
        writeTo((int) mc.player.posX, (int) mc.player.posY, (int) mc.player.posZ, locationName);
    }

    public static void writeTo(int x, int y, int z, String locationName) {
        try {
            FileWriter fW = new FileWriter(COORDS_FILE_NAME);
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
