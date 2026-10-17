package com.makinistatama.guvenlik;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Rol beyanı (MK-25): yalnız MAKINIST. {@code /api/benim/**} controller'ları sınıf düzeyinde taşır (personel bu uçlara giremez). */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SadeceMakinist {
}
