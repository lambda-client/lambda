package me.zeroeightsix.kami.setting;

/**
 * Used as a non-generic base class for ISetting that prevents weird type issues.
 * Raw ISetting instances are too easy to screw up.
 * @author 20kdc
 */
public interface ISettingUnknown {
    /**
     * @return The Class of the internal value (used for .set's output)
     */
    Class getValueClass();

    /**
     * @return The value in a format that setValueFromString can accept (if possible)
     */
    String getValueAsString();

    /**
     * @return Whether or not this setting should be displayed to the user
     */
    boolean isVisible();

    /**
     * Convert & set for .set & other "generic" setter cases.
     * Will throw if unconvertable.
     */
    void setValueFromString(String value);
}
