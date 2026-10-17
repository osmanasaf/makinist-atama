-- F3a WARNING: kadro eğrisinin paralel hesabı için iş parçacığı sayısı. 0 = otomatik (min(6, işlemci çekirdeği / 2), bugünkü davranış); 1..32 = sabit sayı.
-- Geri alma: satırı sil (şema değişmez).
INSERT INTO uygulama_ayar (anahtar, deger, birim, alt, ust, aciklama) VALUES
    ('kadro.is_parcacigi', 0, 'adet', 0, 32, 'Kadro ihtiyacı hesabında paralel iş parçacığı sayısı (0 = otomatik: işlemci çekirdeğinin yarısı, en çok 6). 8 aylık geçmişli eğri için en az 8 çekirdek önerilir; 4 çekirdekte yaklaşık 38 sn sürer.');
