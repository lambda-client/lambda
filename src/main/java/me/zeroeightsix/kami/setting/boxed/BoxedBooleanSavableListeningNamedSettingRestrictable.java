package me.zeroeightsix.kami.setting.boxed;

import me.zeroeightsix.kami.setting.SavableListeningNamedSettingRestrictable;
import me.zeroeightsix.kami.setting.converter.BooleanConverter;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Created by 086 on 12/10/2018.
 */
public class BoxedBooleanSavableListeningNamedSettingRestrictable extends SavableListeningNamedSettingRestrictable<Boolean> {

    private static final BooleanConverter converter = new BooleanConverter();

    public BoxedBooleanSavableListeningNamedSettingRestrictable(Boolean value, Predicate<Boolean> restriction, BiConsumer<Boolean, Boolean> consumer, String name) {
        super(value, restriction, consumer, name);
    }

    @Override
    public BooleanConverter converter() {
        return converter;
    }

}
