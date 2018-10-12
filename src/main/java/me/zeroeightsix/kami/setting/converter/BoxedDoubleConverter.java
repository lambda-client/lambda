package me.zeroeightsix.kami.setting.converter;

/**
 * Created by 086 on 13/10/2018.
 */
public class BoxedDoubleConverter extends AbstractBoxedNumberConverter<Double> {
    @Override
    protected Double doBackward(String s) {
        return Double.parseDouble(s);
    }
}
