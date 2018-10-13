package me.zeroeightsix.kami.setting.builder.numerical;

import me.zeroeightsix.kami.setting.impl.numerical.BoxedDoubleSavableListeningNamedSettingRestrictable;
import me.zeroeightsix.kami.setting.impl.numerical.BoxedNumberSavableListeningNamedSettingRestrictable;

/**
 * Created by 086 on 13/10/2018.
 */
public class DoubleSettingBuilder extends NumericalSettingBuilder<Double> {
    @Override
    public BoxedNumberSavableListeningNamedSettingRestrictable build() {
        return new BoxedDoubleSavableListeningNamedSettingRestrictable(initialValue, predicate(), consumer(), name, visibilityPredicate());
    }
}
