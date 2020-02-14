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
    public static Color checkButtonBackgroundColour = new Color(toF(67), toF(54), toF(191)); // normal colored
    public static Color checkButtonBackgroundColourHover = new Color(toF(67), toF(54), toF(191)); // light colored
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

    public static Color componentWindowOutline = new Color(toF(116), toF(101), toF(247));
    public static float componentWindowOutlineWidth = 1.5f;

    public static Color componentPinnedColour = new Color(toF(165), toF(41), toF(255));
    public static float componentUnpinnedColour = toF(168.3);
    public static float componentLineColour = toF(112.2);

}