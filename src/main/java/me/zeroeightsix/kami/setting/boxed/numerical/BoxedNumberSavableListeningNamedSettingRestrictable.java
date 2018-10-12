package me.zeroeightsix.kami.setting.boxed.numerical;

import me.zeroeightsix.kami.setting.HideableSavableListeningNamedSettingRestrictable;
import me.zeroeightsix.kami.setting.converter.AbstractBoxedNumberConverter;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Created by 086 on 12/10/2018.
 */
public abstract class BoxedNumberSavableListeningNamedSettingRestrictable<T extends Number> extends HideableSavableListeningNamedSettingRestrictable<T> {

    public BoxedNumberSavableListeningNamedSettingRestrictable(T value, Predicate<T> restriction, BiConsumer<T, T> consumer, String name, Predicate<T> visibilityPredicate) {
        super(value, restriction, consumer, name, visibilityPredicate);
    }

    @Override
    public abstract AbstractBoxedNumberConverter converter();

}
