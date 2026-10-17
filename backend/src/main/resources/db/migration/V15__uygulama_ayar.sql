-- F2a (MK-29): uygulama ayarları. kural_parametre kural kataloğudur (ParametreTohumTest tam 31 satırı kilitler); makinist görünümü eşikleri burada.
-- Tamsayı ayar: alt/üst sınır doğrulaması serviste. Tohum satırları TestVeritabani temizliğinde korunur.
CREATE TABLE uygulama_ayar (
    anahtar   VARCHAR(60) PRIMARY KEY,
    deger     INTEGER     NOT NULL,
    birim     VARCHAR(10) NOT NULL,
    alt       INTEGER     NOT NULL,
    ust       INTEGER     NOT NULL,
    aciklama  TEXT        NOT NULL,
    CONSTRAINT uygulama_ayar_aralik_ck CHECK (alt <= deger AND deger <= ust)
);

INSERT INTO uygulama_ayar (anahtar, deger, birim, alt, ust, aciklama) VALUES
    ('makinist.gorunur_gun', 7, 'gün', 1, 30, 'Makinist, bugünden başlayarak kaç günlük görevini görür (bugün dahil).');
