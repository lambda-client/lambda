package me.zeroeightsix.kami.util;

/**
 * Created by Dewy on the 17th of April, 2020
 */
public class MathsUtils {

    public static double normalizeAngle(double angleIn) {
        while (angleIn <= -180.0) {
            angleIn += 360.0;
        }

        while (angleIn > 180.0) {
            angleIn -= 360.0;
        }

        return angleIn;
    }
}
