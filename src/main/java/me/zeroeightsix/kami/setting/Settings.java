package me.zeroeightsix.kami.setting;

import com.google.common.base.Converter;
import com.google.common.base.Predicate;
import me.zeroeightsix.kami.setting.builder.numerical.DoubleSettingBuilder;
import me.zeroeightsix.kami.setting.builder.numerical.FloatSettingBuilder;
import me.zeroeightsix.kami.setting.builder.numerical.IntegerSettingBuilder;
import me.zeroeightsix.kami.setting.builder.numerical.NumericalSettingBuilder;
import me.zeroeightsix.kami.setting.builder.primitive.BooleanSettingBuilder;
import me.zeroeightsix.kami.setting.builder.primitive.StringSettingBuilder;

import java.util.function.BiConsumer;

/**
 * Created by 086 on 13/10/2018.
 */
public class Settings {

    public static FloatSettingBuilder floatBuilder() {
        return new FloatSettingBuilder();
    }

    public static DoubleSettingBuilder doubleBuilder() {
        return new DoubleSettingBuilder();
    }

    public static IntegerSettingBuilder integerBuilder() {
        return new IntegerSettingBuilder();
    }

    public static BooleanSettingBuilder booleanBuilder() {
        return new BooleanSettingBuilder();
    }

    public static StringSettingBuilder stringBuilder() {
        return new StringSettingBuilder();
    }

    public static NumericalSettingBuilder<Float> floatBuilder(String name) {
        return new FloatSettingBuilder().withName(name);
    }

    public static NumericalSettingBuilder<Double> doubleBuilder(String name) {
        return new DoubleSettingBuilder().withName(name);
    }

    public static NumericalSettingBuilder<Integer> integerBuilder(String name) {
        return new IntegerSettingBuilder().withName(name);
    }

    public static BooleanSettingBuilder booleanBuilder(String name) {
        return new BooleanSettingBuilder().withName(name);
    }

    public static StringSettingBuilder stringBuilder(String name) {
        return (StringSettingBuilder) new StringSettingBuilder().withName(name);
    }

    public static <T> Setting<T> custom(String name, T initialValue, Converter converter, Predicate<T> restriction, BiConsumer<T, T> consumer, Predicate<T> visibilityPredicate) {
        return new HideableSavableListeningNamedSettingRestrictable<T>(initialValue, restriction, consumer, name, visibilityPredicate) {
            @Override
            public Converter converter() {
                return converter;
            }
        };
    }

    public static <T> Setting<T> custom(String name, T initialValue, Converter converter, Predicate<T> restriction, BiConsumer<T, T> consumer, boolean hidden) {
        return custom(name, initialValue, converter, restriction, consumer, t -> !hidden);
    }

    public static <T> Setting<T> custom(String name, T initialValue, Converter converter, Predicate<T> restriction, boolean hidden) {
        return custom(name, initialValue, converter, restriction, (t, t2) -> {}, hidden);
    }

    public static <T> Setting<T> custom(String name, T initialValue, Converter converter, boolean hidden) {
        return custom(name, initialValue, converter, input -> true, (t, t2) -> {}, hidden);
    }

    public static <T> Setting<T> custom(String name, T initialValue, Converter converter) {
        return custom(name, initialValue, converter, input -> true, (t, t2) -> {}, false);
    }

}
