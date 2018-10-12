package me.zeroeightsix.kami.setting.converter;

/**
 * Created by 086 on 13/10/2018.
 */
public class BoxedFloatConverter extends AbstractBoxedNumberConverter<Float> {
    @Override
    protected Float doBackward(String s) {
        return Float.parseFloat(s);
    }
}
