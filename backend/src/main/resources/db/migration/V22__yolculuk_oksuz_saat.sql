-- F2b tur 2: öksüz yolculuk uyarısının penceresi, yolculuk ARAMA penceresinden (yolculuk.arama_saat, 12 s) ayrılır. Dinlenme (K-11/K-12) + sonraki gün sürüşü 12 saati
-- kolayca aşar; aynı pencereyle uyarı yanlış alarm verirdi. Öneri / otomatik aşama aramasını etkilemez.
INSERT INTO uygulama_ayar (anahtar, deger, birim, alt, ust, aciklama) VALUES
    ('yolculuk.oksuz_saat', 24, 'saat', 1, 168, 'Yolculuk varışından sonra aynı garda bir sürüş görevi aranan pencere; yoksa yolculuk satırı "Yolculuktan sonra bu garda görev yok" uyarısı taşır.');
