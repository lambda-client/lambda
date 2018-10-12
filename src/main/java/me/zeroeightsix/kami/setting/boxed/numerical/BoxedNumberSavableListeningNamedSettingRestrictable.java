package me.zeroeightsix.kami.setting.boxed.numerical;

import me.zeroeightsix.kami.setting.SavableListeningNamedSettingRestrictable;
import me.zeroeightsix.kami.setting.converter.AbstractBoxedNumberConverter;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Created by 086 on 12/10/2018.
 */
public abstract class BoxedNumberSavableListeningNamedSettingRestrictable<T extends Number> extends SavableListeningNamedSettingRestrictable<T> {

    public BoxedNumberSavableListeningNamedSettingRestrictable(T value, Predicate<T> restriction, BiConsumer<T, T> consumer, String name) {
        super(value, restriction, consumer, name);
    }

    @Override
    public abstract AbstractBoxedNumberConverter converter();

}
