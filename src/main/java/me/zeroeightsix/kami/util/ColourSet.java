package me.zeroeightsix.kami.util;

import java.awt.*;

import static me.zeroeightsix.kami.util.ColourConverter.toF;

/**
 * @author S-B99
 * Class for all the main GUI colours used by the default kami theme
 * mfw I make it easier for skids to customize kami
 */
public class ColourSet {
    /* Check Button colours */
    public static Color bgColour = new Color(67, 54, 191); // normal colored
    public static Color bgColourHover = new Color(67, 54, 191); // light colored
    public static double bgColourOther = 229.5;

    public static Color buttonPressed = new Color(177, 52, 235);

    public static Color buttonIdleN = new Color(200, 200, 200); // lighter grey
    public static Color buttonHoveredN = new Color(190, 190, 190); // light grey

    public static Color buttonIdleT = new Color(165, 158, 232); // lighter colored
    public static Color buttonHoveredT = buttonIdleT.brighter();

    public static Color windowOutline = new Color(116, 101, 247);
    public static float windowOutlineWidth = 1.8f;

    public static Color pinnedWindow = new Color(116, 101, 247);
    public static double unpinnedWindow = 168.3;
    public static double lineWindow = 112.2;

    public static Color sliderColour = new Color(155, 144, 255);

    public static Color enumColour = new Color(116, 101, 247);

}