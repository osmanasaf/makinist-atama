-- F3b (MK-82, MK-86, MK-87): diğer personel (kondüktör / tren şefi). Kart tablosu AYNI (`makinist`), tür tek sütun; mevcut kartlar MAKINIST kalır.
-- Seferde KONDUKTOR rolü: kısmi tekil indeks atama_sefer_rol_uk (rol <> 'YOLCU') değişmez => seferde en çok bir kondüktör (ASIL/YARDIMCI ile ayrı satır).
-- Rol-tür uyumu (ASIL/YARDIMCI = MAKINIST, KONDUKTOR = KONDUKTOR) iki tablo arasında olduğundan CHECK/trigger yok; uygulama kapısı AtamaRol.turler().
-- Geri alma (kondüktör kartı/ataması yokken): DELETE ayar; atama_rol_ck eski kümeye; ALTER TABLE makinist DROP COLUMN tur.
ALTER TABLE makinist ADD COLUMN tur VARCHAR(10) NOT NULL DEFAULT 'MAKINIST';
ALTER TABLE makinist ADD CONSTRAINT makinist_tur_ck CHECK (tur IN ('MAKINIST', 'KONDUKTOR'));

ALTER TABLE atama DROP CONSTRAINT atama_rol_ck;
ALTER TABLE atama ADD CONSTRAINT atama_rol_ck CHECK (rol IN ('ASIL', 'YARDIMCI', 'YOLCU', 'KONDUKTOR'));

-- Varsayılan 0: kondüktör kullanmayan işletmede hiçbir sefer "kondüktör eksik" sayılmaz (eşdeğerlik); demo kondüktör örneği 1 yapar.
INSERT INTO uygulama_ayar (anahtar, deger, birim, alt, ust, aciklama) VALUES
    ('konduktor.yolcu_trenine_gerekli', 0, '0/1', 0, 1, 'Yolcu trenlerinde kondüktör gerekli mi (1 evet, 0 hayır); evet iken kondüktörü atanmamış yolcu treni masada "kondüktör eksik" olarak işaretlenir (bilgi, engel değil).');
