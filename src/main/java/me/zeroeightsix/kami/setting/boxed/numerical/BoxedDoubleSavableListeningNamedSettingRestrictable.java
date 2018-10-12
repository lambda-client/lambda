package me.zeroeightsix.kami.setting.boxed.numerical;

import me.zeroeightsix.kami.setting.converter.AbstractBoxedNumberConverter;
import me.zeroeightsix.kami.setting.converter.BoxedDoubleConverter;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Created by 086 on 12/10/2018.
 */
public class BoxedDoubleSavableListeningNamedSettingRestrictable extends BoxedNumberSavableListeningNamedSettingRestrictable<Double> {

    private static final BoxedDoubleConverter converter = new BoxedDoubleConverter();

    public BoxedDoubleSavableListeningNamedSettingRestrictable(Double value, Predicate<Double> restriction, BiConsumer<Double, Double> consumer, String name) {
        super(value, restriction, consumer, name);
    }

    @Override
    public AbstractBoxedNumberConverter converter() {
        return converter;
    }

}
