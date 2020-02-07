package me.zeroeightsix.kami.util;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.Display;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Taken from Minecraft.java, as unfortunately it's method does not support using custom icons.
 * Updated by S-B99 on 07/02/20
 */
public class WindowIcon extends Module {
    public final DefaultResourcePack defaultResourcePack = Minecraft.getMinecraft().defaultResourcePack;
    public void setWindowIcon() {
        Util.EnumOS util$enumos = Util.getOSType();

        if (util$enumos != Util.EnumOS.OSX) {
            InputStream inputstream = null;
            InputStream inputstream1 = null;
            try {
                inputstream = this.defaultResourcePack.getInputStreamAssets(new ResourceLocation("kamiLowRes.png"));
                inputstream1 = this.defaultResourcePack.getInputStreamAssets(new ResourceLocation("kami.png"));

                if (inputstream != null && inputstream1 != null) {
                    Display.setIcon(new ByteBuffer[] {Minecraft.getMinecraft().readImageToBuffer(inputstream), Minecraft.getMinecraft().readImageToBuffer(inputstream1)});
                }
            }
            catch (IOException ioexception) {
                KamiMod.log.error("Couldn't set icon: ", ioexception);
            }
            finally {
                IOUtils.closeQuietly(inputstream);
                IOUtils.closeQuietly(inputstream1);
            }
        }
    }
}
