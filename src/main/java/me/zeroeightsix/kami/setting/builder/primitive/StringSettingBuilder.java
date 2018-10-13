package me.zeroeightsix.kami.setting.builder.primitive;

import me.zeroeightsix.kami.setting.builder.SettingBuilder;
import me.zeroeightsix.kami.setting.impl.StringSavableListeningNamedSettingRestrictable;

/**
 * Created by 086 on 13/10/2018.
 */
public class StringSettingBuilder extends SettingBuilder<String> {
    @Override
    public StringSavableListeningNamedSettingRestrictable build() {
        return new StringSavableListeningNamedSettingRestrictable(initialValue, predicate(), consumer(), name, visibilityPredicate());
    }
}
