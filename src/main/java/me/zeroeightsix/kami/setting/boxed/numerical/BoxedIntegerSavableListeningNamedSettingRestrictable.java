package me.zeroeightsix.kami.setting.boxed.numerical;

import me.zeroeightsix.kami.setting.converter.AbstractBoxedNumberConverter;
import me.zeroeightsix.kami.setting.converter.BoxedIntegerConverter;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Created by 086 on 12/10/2018.
 */
public class BoxedIntegerSavableListeningNamedSettingRestrictable extends BoxedNumberSavableListeningNamedSettingRestrictable<Integer> {

    private static final BoxedIntegerConverter converter = new BoxedIntegerConverter();

    public BoxedIntegerSavableListeningNamedSettingRestrictable(Integer value, Predicate<Integer> restriction, BiConsumer<Integer, Integer> consumer, String name) {
        super(value, restriction, consumer, name);
    }

    @Override
    public AbstractBoxedNumberConverter converter() {
        return converter;
    }

}
