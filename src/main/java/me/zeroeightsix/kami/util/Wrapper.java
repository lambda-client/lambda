package me.zeroeightsix.kami.util;

import me.zeroeightsix.kami.gui.rgui.render.font.FontRenderer;
import me.zeroeightsix.kami.gui.kami.KamiGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/**
 * Created by 086 on 11/11/2017.
 */
public class Wrapper {

    static FontRenderer cfr;

    public static void init() {
//        cfr = new CFontRenderer(new Font("Segoe UI", Font.PLAIN, 19), true, false);
        cfr = KamiGUI.fontRenderer;
    }
    public static Minecraft getMinecraft() {
        return Minecraft.getMinecraft();
    }
    public static EntityPlayerSP getPlayer() {
        return getMinecraft().player;
    }
    public static World getWorld() {
        return getMinecraft().world;
    }
    public static int getKey(String keyname){
        return Keyboard.getKeyIndex(keyname.toUpperCase());
    }

    public static FontRenderer getFontRenderer() {
        return cfr;
    }
}
