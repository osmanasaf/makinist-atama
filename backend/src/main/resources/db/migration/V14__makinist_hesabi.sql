-- F2a (MK-26): makinist hesabı. Giriş adı = küçük harf sicil (kullanici.eposta sütununda; '@' içermediği için gerçek e-postayla çakışmaz).
-- makinist_id düz sayıdır, FK YOKTUR: demo sıfırlama `TRUNCATE makinist ... CASCADE` FK olsaydı kullanici tablosunu (yönetici dahil) silerdi.
ALTER TABLE kullanici
    ADD COLUMN makinist_id          BIGINT,
    ADD COLUMN parola_degistirmeli  BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE kullanici DROP CONSTRAINT kullanici_rol_ck;
ALTER TABLE kullanici
    ADD CONSTRAINT kullanici_rol_ck CHECK (rol IN ('YONETICI', 'ATAMACI', 'MAKINIST')),
    ADD CONSTRAINT kullanici_makinist_uk UNIQUE (makinist_id),
    ADD CONSTRAINT kullanici_makinist_bag_ck CHECK ((rol = 'MAKINIST') = (makinist_id IS NOT NULL)),
    ADD CONSTRAINT kullanici_parola_bayrak_ck CHECK (NOT parola_degistirmeli OR rol = 'MAKINIST'),
    ADD CONSTRAINT kullanici_makinist_ad_ck CHECK (rol <> 'MAKINIST' OR position('@' IN eposta) = 0);
