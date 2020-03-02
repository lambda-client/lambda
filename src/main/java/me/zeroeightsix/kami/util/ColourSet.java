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
    public static Color checkButtonBackgroundColour = new Color(67, 54, 191); // normal colored
    public static Color checkButtonBackgroundColourHover = new Color(67, 54, 191); // light colored
    public static float checkButtonBackgroundColourOther = .9f;

    public static Color checkButtonIdleColourNormal = new Color(200, 200, 200); // lighter grey
    public static Color checkButtonDownColourNormal = new Color(190, 190, 190); // light grey

    public static Color checkButtonIdleColourToggle = new Color(165, 158, 232); // lighter colored
    public static Color checkButtonDownColourToggle = checkButtonIdleColourToggle.brighter();

    /* Component colours */
    public static Color componentMainWindow = new Color(.17f, .17f, .18f, .9f);
    public static float[] componentMainWindowArray;

//    public static float[] getComponentMainWindowArray() {
//        componentMainWindowArray[0] = .17f;
//        componentMainWindowArray[1] = .17f;
//        componentMainWindowArray[2] = .18f;
//        componentMainWindowArray[3] = .9f;
//        return componentMainWindowArray;
//    }

    public static Color componentWindowOutline = new Color(116, 101, 247);
    public static float componentWindowOutlineWidth = 1.5f;

    public static Color componentPinnedColour = new Color(165, 41, 255);
    public static double componentUnpinnedColour = 168.3;
    public static double componentLineColour = 112.2;

    public static Color sliderColour = new Color(155, 144, 255);

    public static Color enumColour = new Color(116, 101, 247);

}