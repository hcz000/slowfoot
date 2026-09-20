package org.hcz.security.access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Limit {

    int limit();

    long time();


// Optional shared bucket name.

    String key() default "";

    String msg() default "请求过于频繁";
}
