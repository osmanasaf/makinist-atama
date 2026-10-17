package com.makinistatama.kullanici;

/** Sistem rolleri: ATAMACI (günlük iş), YONETICI (+ parametre, kullanıcı, tanım), MAKINIST (yalnız kendi görünümü). */
public enum Rol {
    YONETICI,
    ATAMACI,
    /** Makinist hesabı (F2a): yalnız /api/benim/** ve kendi parola/oturum uçları; kullanici.makinist_id ile bir makinist kartına bağlıdır. */
    MAKINIST
}
