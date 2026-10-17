package com.makinistatama.guvenlik;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/** Yalnız yönetici: controller sınıfında ya da metodunda. Kullanıcı yönetimi ve tanım yazma uç noktaları bunu taşır. */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('YONETICI')")
public @interface SadeceYonetici {
}
