package com.makinistatama.guvenlik;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Rol beyanı (MK-25): giriş yapmış her rol (ATAMACI, YONETICI, MAKINIST): oturum kapatma, kimlik, kendi parolası. */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HerkesGiris {
}
