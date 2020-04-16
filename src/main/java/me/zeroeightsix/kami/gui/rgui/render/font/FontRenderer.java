package me.zeroeightsix.kami.gui.rgui.render.font;

import java.awt.*;

/**
 * Created by 086 on 26/06/2017.
 */
public interface FontRenderer {

    int getFontHeight();

    int getStringHeight(String text);

    int getStringWidth(String text);

    void drawString(int x, int y, String text);

    void drawString(int x, int y, int r, int g, int b, String text);

    void drawString(int x, int y, Color color, String text);

    void drawString(int x, int y, int colour, String text);

    void drawStringWithShadow(int x, int y, int r, int g, int b, String text);

}
