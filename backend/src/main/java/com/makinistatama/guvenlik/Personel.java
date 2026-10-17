package com.makinistatama.guvenlik;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Rol beyanı (MK-25): ATAMACI ve YONETICI (personel uçları). MAKINIST bunlara giremez. */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Personel {
}
