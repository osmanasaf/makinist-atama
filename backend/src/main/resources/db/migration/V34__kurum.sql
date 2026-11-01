-- Kurum adı + logosu (F1, MK-167): müşterinin kendi verisi; tek satır (id = 1). Logo veritabanında (yedek betikleri yalnız veritabanını yedekler).
-- Logo yalnız PNG/JPEG, <= 256 KB (sunucu imza + başlık boyutu doğrular). versiyon = logo ETag'ı. sihirbaz_gizli: ilk kurulum kartını gizleme tercihi.
-- Geri alma: DROP TABLE kurum.
CREATE TABLE kurum (
    id              SMALLINT     PRIMARY KEY DEFAULT 1,
    ad              VARCHAR(120),
    logo            BYTEA,
    logo_tur        VARCHAR(10),
    logo_boyut      INTEGER      NOT NULL DEFAULT 0,
    sihirbaz_gizli  BOOLEAN      NOT NULL DEFAULT FALSE,
    versiyon        BIGINT       NOT NULL DEFAULT 0,
    guncelleme      TIMESTAMPTZ,
    CONSTRAINT kurum_tek_satir_ck CHECK (id = 1),
    CONSTRAINT kurum_logo_tur_ck CHECK (logo_tur IS NULL OR logo_tur IN ('PNG', 'JPEG')),
    CONSTRAINT kurum_logo_birlikte_ck CHECK ((logo IS NULL) = (logo_tur IS NULL)),
    CONSTRAINT kurum_logo_boyut_ck CHECK (logo IS NULL OR octet_length(logo) <= 262144)
);
INSERT INTO kurum (id) VALUES (1);
