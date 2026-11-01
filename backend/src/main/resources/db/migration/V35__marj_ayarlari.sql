-- F3 (MK-173): sınıra yakınlık işareti ve yorgunluk göstergesi ayarları. Bunlar KURAL değil BİLGİ eşikleridir; kural motoruna (kural_parametre / Parametreler) girmez.
-- Hepsi tamsayı; grup MARJ (Ayarlar ekranında otomatik görünür, neden zorunlu, denetim kaydı mevcut mekanizmadır). Davranış değişikliği yoktur: marj.aktif=1 yalnız
-- bilgi işareti üretir, hiçbir atamayı engel/uyarı/kırmızı yapmaz. Değerler [V] kaynaksız yer tutucudur (OK-45 Q4).
-- Geri alma: DELETE FROM uygulama_ayar WHERE grup = 'MARJ';
INSERT INTO uygulama_ayar (anahtar, deger, birim, alt, ust, aciklama, varsayilan, grup, sira) VALUES
    ('marj.aktif', 1, '0/1', 0, 1, '1 = aday panelinde ve masada "sınıra yakın" bilgi işareti ile yorgunluk özeti gösterilir; 0 = hiç hesaplanmaz. [V] kaynaksız yer tutucu — bilgi amaçlıdır', 1, 'MARJ', 10),
    ('marj.gunluk_dk', 60, 'dk', 0, 720, 'Günlük (ve haftalık kural açıksa haftalık) görev süresi sınırına bu kadar dakika ya da daha az kaldıysa işaret çıkar. [V] kaynaksız yer tutucu — bilgi amaçlıdır', 60, 'MARJ', 20),
    ('marj.gece_dk', 30, 'dk', 0, 240, 'Gece nöbeti azami süresine bu kadar dakika ya da daha az kaldıysa işaret çıkar. [V] kaynaksız yer tutucu — bilgi amaçlıdır', 30, 'MARJ', 30),
    ('marj.dinlenme_dk', 60, 'dk', 0, 720, 'Dinlenme süresi gerekli süreyi en çok bu kadar dakika aşıyorsa işaret çıkar. [V] kaynaksız yer tutucu — bilgi amaçlıdır', 60, 'MARJ', 40),
    ('marj.hafta_tatili_dk', 120, 'dk', 0, 1440, 'Haftalık kesintisiz dinlenme boşluğu gerekli süreyi en çok bu kadar dakika aşıyorsa işaret çıkar. [V] kaynaksız yer tutucu — bilgi amaçlıdır', 120, 'MARJ', 50),
    ('marj.yillik_saat', 20, 'saat', 0, 270, 'Yıllık fazla mesai üst sınırına bu kadar saat ya da daha az kaldıysa işaret çıkar. [V] kaynaksız yer tutucu — bilgi amaçlıdır', 20, 'MARJ', 60),
    ('marj.aktarma_dk', 10, 'dk', 0, 120, 'Nöbet içi aktarma süresi gerekli süreyi en çok bu kadar dakika aşıyorsa işaret çıkar. [V] kaynaksız yer tutucu — bilgi amaçlıdır', 10, 'MARJ', 70),
    ('marj.gece_hafta', 0, 'hafta', 0, 3, 'Üst üste gece haftası sınırına bu kadar hafta ya da daha az kaldıysa işaret çıkar (0 = sınıra ulaşınca). [V] kaynaksız yer tutucu — bilgi amaçlıdır', 0, 'MARJ', 80),
    ('yorgunluk.gun', 7, 'gün', 1, 14, 'Yorgunluk göstergesinin baktığı geçmiş pencere (gün). [V] kaynaksız yer tutucu — bilgi amaçlıdır; bilimsel bir yorgunluk modeli değildir', 7, 'MARJ', 90),
    ('yorgunluk.hafta_saat', 40, 'saat', 1, 168, 'Pencere içi toplam çalışma bu süreyi aşarsa değer vurgulanır. [V] kaynaksız yer tutucu — bilgi amaçlıdır', 40, 'MARJ', 100),
    ('yorgunluk.gece_adet', 3, 'adet', 1, 14, 'Pencere içi gece nöbeti sayısı bu değere ulaşırsa değer vurgulanır. [V] kaynaksız yer tutucu — bilgi amaçlıdır', 3, 'MARJ', 110),
    ('yorgunluk.dinlenme_saat', 12, 'saat', 1, 72, 'Pencere içi en kısa dinlenme bu süreden kısaysa değer vurgulanır. [V] kaynaksız yer tutucu — bilgi amaçlıdır', 12, 'MARJ', 120);
