package com.dxc.monitoring.service.dashboard;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Target({ TYPE, ANNOTATION_TYPE })
@Retention(RUNTIME)
@Constraint(validatedBy = DashboardWidgetValidator.class)
public @interface ValidDashboardWidget
{
    String message() default "Invalid dashboard widget configuration.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}