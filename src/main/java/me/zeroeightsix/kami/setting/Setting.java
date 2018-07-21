package me.zeroeightsix.kami.setting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by 086 on 25/08/2017.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Setting {
    String name();
    Class<? extends FieldConverter> converter() default PrimitiveConverter.class;
    boolean hidden() default false;

    double min() default -1;
    double max() default -1;

    boolean integer() default false;
}
