-- F2a-3 review W4: makinist başına talep açma kotası (açıp geri çekme döngüsü listeyi / denetim tablosunu şişirmesin).
INSERT INTO uygulama_ayar (anahtar, deger, birim, alt, ust, aciklama) VALUES
    ('takas.gunluk_acma', 10, 'adet', 1, 200, 'Bir makinistin son 24 saatte açabileceği takas / bırakma talebi sayısı (geri çekilenler de sayılır). Dolunca yeni talep 429 ile reddedilir.');
