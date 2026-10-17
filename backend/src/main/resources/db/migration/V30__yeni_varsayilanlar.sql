-- OK-24 (kullanıcı kararı, 2026-10-15): yeni varsayılanlar.
--   * K-14 (haftalık üst sınır) KAPALI: haftalık 45 saat üstü engel değil, fazla mesai sayılır (üst sınırı günlük 11 s, hafta tatili ve yıllık 270 s koyar).
--   * K-23 (asgari aktarma) ENGEL ve nobet.asgari_aktarma_dk = 20 dk.
-- `varsayilan` sütunları HER ZAMAN yeni değere çekilir ("varsayılana dön" bunu kullanır). Çalışan değer (deger/siddet) yalnız "değiştirilmemiş" satırlarda çevrilir:
--   değiştirilmemiş = mevcut değer eski tohumla aynı VE ilgili anahtar için demo kaynaklı OLMAYAN PARAMETRE_DEGISTI kaydı yok.
--   Demo kaydı = neden_notu 'DEMO' ile ya da 'Demo sıfırlandı' ile başlar (DemoVeriServisi'nin eski yazımları). Elle değiştirilmiş değerlere dokunulmaz.
-- Çevrilen her satır için ek-yalnız degisiklik_kaydi'na sistem kaydı yazılır. Taze veritabanında kayıt yoktur: üç değer de çevrilir (üç sistem kaydı).
-- haftalik.max_saat: eski demo yüklemesinden kalan '55' (yalnız demo kayıtlarıyla) '45'e döner; K-14 kapalıyken kullanılmaz.
-- Geri alma: kural_siddet K-14 ENGEL, K-23 KAPALI; nobet.asgari_aktarma_dk '0'; varsayilan sütunları aynı eski değerlere; açıklamalar V29'daki (V4/V28) metinlere. Sistem kayıtları ek-yalnız kalır.

UPDATE kural_siddet SET varsayilan = 'KAPALI' WHERE kod = 'K-14';
UPDATE kural_siddet SET varsayilan = 'ENGEL' WHERE kod = 'K-23';
UPDATE kural_parametre SET varsayilan = '20' WHERE anahtar = 'nobet.asgari_aktarma_dk';

WITH g AS (
    UPDATE kural_siddet SET siddet = 'KAPALI'
    WHERE kod = 'K-14' AND siddet = 'ENGEL'
      AND NOT EXISTS (SELECT 1 FROM degisiklik_kaydi d WHERE d.tur = 'PARAMETRE_DEGISTI' AND d.varlik_tipi = 'KURAL_SIDDET' AND d.ayrinti LIKE '%"K-14"%'
                        AND NOT (coalesce(d.neden_notu, '') LIKE 'DEMO%' OR coalesce(d.neden_notu, '') LIKE 'Demo sıfırlandı%'))
    RETURNING kod
)
INSERT INTO degisiklik_kaydi (kullanici_id, kullanici_adi, tur, varlik_tipi, eski, yeni, neden, neden_notu, ayrinti)
SELECT NULL, 'sistem', 'PARAMETRE_DEGISTI', 'KURAL_SIDDET', 'ENGEL', 'KAPALI', 'DIGER', 'V30: yeni varsayılan (OK-24)', '{"anahtar":"' || kod || '"}' FROM g;

WITH g AS (
    UPDATE kural_siddet SET siddet = 'ENGEL'
    WHERE kod = 'K-23' AND siddet = 'KAPALI'
      AND NOT EXISTS (SELECT 1 FROM degisiklik_kaydi d WHERE d.tur = 'PARAMETRE_DEGISTI' AND d.varlik_tipi = 'KURAL_SIDDET' AND d.ayrinti LIKE '%"K-23"%'
                        AND NOT (coalesce(d.neden_notu, '') LIKE 'DEMO%' OR coalesce(d.neden_notu, '') LIKE 'Demo sıfırlandı%'))
    RETURNING kod
)
INSERT INTO degisiklik_kaydi (kullanici_id, kullanici_adi, tur, varlik_tipi, eski, yeni, neden, neden_notu, ayrinti)
SELECT NULL, 'sistem', 'PARAMETRE_DEGISTI', 'KURAL_SIDDET', 'KAPALI', 'ENGEL', 'DIGER', 'V30: yeni varsayılan (OK-24)', '{"anahtar":"' || kod || '"}' FROM g;

WITH g AS (
    UPDATE kural_parametre SET deger = '20'
    WHERE anahtar = 'nobet.asgari_aktarma_dk' AND deger = '0'
      AND NOT EXISTS (SELECT 1 FROM degisiklik_kaydi d WHERE d.tur = 'PARAMETRE_DEGISTI' AND d.varlik_tipi = 'PARAMETRE' AND d.ayrinti LIKE '%"nobet.asgari_aktarma_dk"%'
                        AND NOT (coalesce(d.neden_notu, '') LIKE 'DEMO%' OR coalesce(d.neden_notu, '') LIKE 'Demo sıfırlandı%'))
    RETURNING anahtar
)
INSERT INTO degisiklik_kaydi (kullanici_id, kullanici_adi, tur, varlik_tipi, eski, yeni, neden, neden_notu, ayrinti)
SELECT NULL, 'sistem', 'PARAMETRE_DEGISTI', 'PARAMETRE', '0', '20', 'DIGER', 'V30: yeni varsayılan (OK-24)', '{"anahtar":"' || anahtar || '"}' FROM g;

WITH g AS (
    UPDATE kural_parametre SET deger = '45'
    WHERE anahtar = 'haftalik.max_saat' AND deger = '55'
      AND EXISTS (SELECT 1 FROM degisiklik_kaydi d WHERE d.tur = 'PARAMETRE_DEGISTI' AND d.varlik_tipi = 'PARAMETRE' AND d.ayrinti LIKE '%"haftalik.max_saat"%')
      AND NOT EXISTS (SELECT 1 FROM degisiklik_kaydi d WHERE d.tur = 'PARAMETRE_DEGISTI' AND d.varlik_tipi = 'PARAMETRE' AND d.ayrinti LIKE '%"haftalik.max_saat"%'
                        AND NOT (coalesce(d.neden_notu, '') LIKE 'DEMO%' OR coalesce(d.neden_notu, '') LIKE 'Demo sıfırlandı%'))
    RETURNING anahtar
)
INSERT INTO degisiklik_kaydi (kullanici_id, kullanici_adi, tur, varlik_tipi, eski, yeni, neden, neden_notu, ayrinti)
SELECT NULL, 'sistem', 'PARAMETRE_DEGISTI', 'PARAMETRE', '55', '45', 'DIGER', 'V30: demo artığı (OK-24)', '{"anahtar":"' || anahtar || '"}' FROM g;

UPDATE kural_siddet SET aciklama = 'Takvim haftası toplam görev süresi. Varsayılan KAPALI: haftalık 45 saat üstü engel değildir, fazla mesai sayılır; üst sınırı günlük süre, hafta tatili ve yıllık fazla mesai kuralları koyar.'
    WHERE kod = 'K-14';
UPDATE kural_siddet SET aciklama = 'Gece görevi haftalık normal süreyi aşırtamaz: gece çalışılan haftada normal sürenin üstüne fazla mesai yazılmaz (fazla mesai yasağı, 4857 md. 41).'
    WHERE kod = 'K-17';
UPDATE kural_siddet SET aciklama = 'Aynı nöbette ardışık iki görev arasında asgari aktarma/dönüş süresi (varsayılan AÇIK, 20 dk; nöbet ayırma süresinin altında olmalı)'
    WHERE kod = 'K-23';
UPDATE kural_parametre SET aciklama = 'Takvim haftası (Pzt-Paz) toplam görev süresi üst sınırı. Yalnız K-14 açıkken kullanılır; kapalıyken (varsayılan) aranmaz.'
    WHERE anahtar = 'haftalik.max_saat';
UPDATE kural_parametre SET aciklama = 'Haftalık normal çalışma; üstü fazla mesai sayılır (puantajda ve yıllık fazla mesai toplamında)'
    WHERE anahtar = 'haftalik.normal_saat';
UPDATE kural_parametre SET aciklama = 'Aynı nöbetteki ardışık iki görev arasında en az aktarma/dönüş süresi (önceki görev varışı ile sonrakinin kalkışı arası); varsayılan 20 dk; nöbet ayırma süresinin (dk) altında olmalı; 0 = aranmaz'
    WHERE anahtar = 'nobet.asgari_aktarma_dk';
