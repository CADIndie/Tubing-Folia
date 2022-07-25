package be.garagepoort.mcioc.tubinggui;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GuiController {

    String conditionalOnProperty() default "";

    boolean priority() default false;
}
