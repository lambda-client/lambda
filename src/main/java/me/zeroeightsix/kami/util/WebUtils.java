package me.zeroeightsix.kami.util;

import me.zeroeightsix.kami.KamiMod;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

/**
 * Created by Dewy on 09/04/2020
 */
public class WebUtils {

    public static void openWebLink(URI url) {
        try {
            Desktop.getDesktop().browse(url);
        } catch (IOException e) {
            KamiMod.log.error("Couldn't open link: " + url.toString());
        }
    }
}
