-- K-23 (Q-1): aynı makinistin aynı nöbetteki ardışık iki sürüş görevi arasında asgari aktarma/dönüş süresi (önceki varış -> sonraki kalkış).
-- Tohumda KAPALI ve 0 dk: mevcut davranış birebir korunur. Aynı trenin ardışık parçaları (OK-17) ve yolculuk geçişleri (yolculuk.aktarma_dk) muaftır.
-- Geri alma: iki satırı sil.
INSERT INTO kural_parametre (anahtar, deger, tip, alt_sinir, ust_sinir, birim, aciklama, kaynak, guvenilirlik) VALUES
    ('nobet.asgari_aktarma_dk', '0', 'SAYI', 0, 240, 'dk', 'Aynı nöbetteki ardışık iki görev arasında en az aktarma/dönüş süresi (önceki görev varışı ile sonrakinin kalkışı arası); 0 = aranmaz', '[V] iş kuralı açık soru (SON-QA Q-1)', 'DUSUK');
INSERT INTO kural_siddet (kod, siddet, aciklama, kaynak, guvenilirlik) VALUES
    ('K-23', 'KAPALI', 'Aynı nöbette ardışık iki görev arasında asgari aktarma/dönüş süresi', '[V] iş kuralı açık soru (SON-QA Q-1); parametre nobet.asgari_aktarma_dk', 'DUSUK');
