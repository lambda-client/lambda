package me.zeroeightsix.kami.setting;

import java.util.function.BiConsumer;

/**
 * Created by 086 on 12/10/2018.
 */
public interface ISetting<T> extends ISettingUnknown {

    T getValue();

    /**
     * @param value
     * @return true if value was set
     */
    boolean setValue(T value);

    /**
     * @return A consumer that expects first the previous value and then the new value
     */
    BiConsumer<T, T> changeListener();
}
