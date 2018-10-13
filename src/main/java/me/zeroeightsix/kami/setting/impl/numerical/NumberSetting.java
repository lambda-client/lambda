package me.zeroeightsix.kami.setting.impl.numerical;

import me.zeroeightsix.kami.setting.AbstractSetting;
import me.zeroeightsix.kami.setting.converter.AbstractBoxedNumberConverter;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Created by 086 on 12/10/2018.
 */
public abstract class NumberSetting<T extends Number> extends AbstractSetting<T> {

    public NumberSetting(T value, Predicate<T> restriction, BiConsumer<T, T> consumer, String name, Predicate<T> visibilityPredicate) {
        super(value, restriction, consumer, name, visibilityPredicate);
    }

    @Override
    public abstract AbstractBoxedNumberConverter converter();

}
