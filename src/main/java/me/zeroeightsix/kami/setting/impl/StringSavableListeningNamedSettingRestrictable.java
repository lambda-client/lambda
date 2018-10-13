package me.zeroeightsix.kami.setting.impl;

import me.zeroeightsix.kami.setting.HideableSavableListeningNamedSettingRestrictable;
import me.zeroeightsix.kami.setting.converter.StringConverter;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Created by 086 on 12/10/2018.
 */
public class StringSavableListeningNamedSettingRestrictable extends HideableSavableListeningNamedSettingRestrictable<String> {

    private static final StringConverter converter = new StringConverter();

    public StringSavableListeningNamedSettingRestrictable(String value, Predicate<String> restriction, BiConsumer<String, String> consumer, String name, Predicate<String> visibilityPredicate) {
        super(value, restriction, consumer, name, visibilityPredicate);
    }

    @Override
    public StringConverter converter() {
        return converter;
    }

}
