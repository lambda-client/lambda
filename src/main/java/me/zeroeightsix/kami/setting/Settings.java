package me.zeroeightsix.kami.setting;

import me.zeroeightsix.kami.setting.builder.numerical.DoubleSettingBuilder;
import me.zeroeightsix.kami.setting.builder.numerical.FloatSettingBuilder;
import me.zeroeightsix.kami.setting.builder.numerical.IntegerSettingBuilder;
import me.zeroeightsix.kami.setting.builder.numerical.NumericalSettingBuilder;
import me.zeroeightsix.kami.setting.builder.primitive.BooleanSettingBuilder;

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

}
