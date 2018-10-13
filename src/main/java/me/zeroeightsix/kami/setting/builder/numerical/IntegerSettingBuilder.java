package me.zeroeightsix.kami.setting.builder.numerical;

import me.zeroeightsix.kami.setting.impl.numerical.BoxedIntegerSavableListeningNamedSettingRestrictable;
import me.zeroeightsix.kami.setting.impl.numerical.BoxedNumberSavableListeningNamedSettingRestrictable;

/**
 * Created by 086 on 13/10/2018.
 */
public class IntegerSettingBuilder extends NumericalSettingBuilder<Integer> {
    @Override
    public BoxedNumberSavableListeningNamedSettingRestrictable build() {
        return new BoxedIntegerSavableListeningNamedSettingRestrictable(initialValue, predicate(), consumer(), name, visibilityPredicate());
    }
}
