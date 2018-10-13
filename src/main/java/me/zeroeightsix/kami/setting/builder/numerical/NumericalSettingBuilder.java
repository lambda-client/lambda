package me.zeroeightsix.kami.setting.builder.numerical;

import me.zeroeightsix.kami.setting.impl.numerical.BoxedNumberSavableListeningNamedSettingRestrictable;
import me.zeroeightsix.kami.setting.builder.SettingBuilder;

import java.util.function.BiConsumer;

/**
 * Created by 086 on 13/10/2018.
 */
public abstract class NumericalSettingBuilder<T extends Number> extends SettingBuilder<T> {

    public NumericalSettingBuilder<T> withMinimum(T minimum) {
        predicateList.add((t -> t.doubleValue() >= minimum.doubleValue()));
        return this;
    }

    public NumericalSettingBuilder<T> withMaximum(T maximum) {
        predicateList.add((t -> t.doubleValue() <= maximum.doubleValue()));
        return this;
    }

    public NumericalSettingBuilder<T> withRange(T from, T to) {
        predicateList.add((t -> {
            double doubleValue = t.doubleValue();
            return doubleValue >= from.doubleValue() && doubleValue <= to.doubleValue();
        }));
        return this;
    }

    public NumericalSettingBuilder<T> withListener(BiConsumer<T, T> consumer) {
        this.consumer = consumer;
        return this;
    }

    @Override
    public NumericalSettingBuilder<T> withValue(T value) {
        return (NumericalSettingBuilder) super.withValue(value);
    }

    @Override
    public NumericalSettingBuilder withName(String name) {
        return (NumericalSettingBuilder) super.withName(name);
    }

    public abstract BoxedNumberSavableListeningNamedSettingRestrictable build();

}
