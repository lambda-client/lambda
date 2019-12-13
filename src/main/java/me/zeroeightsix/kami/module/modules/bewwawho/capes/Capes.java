package me.zeroeightsix.kami.module.modules.bewwawho.capes;

import com.google.gson.Gson;
import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

import javax.net.ssl.HttpsURLConnection;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.net.URL;

//i love crystallinqq <3

/***
 * @author crystalling
 */
public class Capes {

    private static Capes INSTANCE;
    private CapeUser[] capeUser;

    public Capes() {
        INSTANCE = this;
        if (ModuleManager.isModuleEnabled("Cape")) {
            try {
                HttpsURLConnection connection = (HttpsURLConnection) new URL("https://raw.githubusercontent.com/S-B99/KAMI/features-master/assets/capes.json").openConnection();

                connection.connect();
                this.capeUser = new Gson().fromJson(new InputStreamReader(connection.getInputStream()), CapeUser[].class);
                connection.disconnect();
            } catch (Exception e) {
                KamiMod.log.error("Failed to load capes");
                e.printStackTrace();
            }

            if (capeUser != null) {
                for (CapeUser user : capeUser) {
                    bindTexture(user.url, "capes/kami/" + formatUUID(user.uuid));
                }
            }
        }
    }

    public static ResourceLocation getCapeResource(AbstractClientPlayer player) {
        for(CapeUser user : INSTANCE.capeUser) {
            if(player.getUniqueID().toString().equalsIgnoreCase(user.uuid)) {
                return new ResourceLocation("capes/kami/" + formatUUID(user.uuid));
            }
        }

        return null;
    }

    private void bindTexture(String url, String resource) {
        IImageBuffer iib = new IImageBuffer() {
            @Override
            public BufferedImage parseUserSkin(BufferedImage image) {
                return parseCape(image);
            }

            @Override
            public void skinAvailable() {}
        };

        ResourceLocation rl = new ResourceLocation(resource);
        TextureManager textureManager = Wrapper.getMinecraft().getTextureManager();
        textureManager.getTexture(rl);
        ThreadDownloadImageData textureCape = new ThreadDownloadImageData(null, url, null, iib);
        textureManager.loadTexture(rl, textureCape);

    }

    private BufferedImage parseCape(BufferedImage img)  {
        int imageWidth = 64;
        int imageHeight = 32;

        int srcWidth = img.getWidth();
        int srcHeight = img.getHeight();
        while (imageWidth < srcWidth || imageHeight < srcHeight) {
            imageWidth *= 2;
            imageHeight *= 2;
        }
        BufferedImage imgNew = new BufferedImage(imageWidth, imageHeight, 2);
        Graphics g = imgNew.getGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();

        return imgNew;
    }

    private static String formatUUID(String uuid) {
        return uuid.replaceAll("-", "");
    }

    private class CapeUser {
        public String uuid;
        public String url;
    }
}
