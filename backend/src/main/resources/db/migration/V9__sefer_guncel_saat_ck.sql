-- BUG-11: gecikme girişi güncel varışı güncel kalkıştan önceye alabiliyordu (gün listesi, aday listesi ve otomatik atama 500 veriyordu).
-- Uygulama artık 400 döner; bu kısıt aynı kuralı veritabanında da tutar (hangi yoldan yazılırsa yazılsın bozuk kayıt olmaz).
-- Önce var olan bozuk satırlar onarılır: varış gecikmesi en az kalkış gecikmesi kadar yapılır (planlanan varış > planlanan kalkış olduğundan güncel varış > güncel kalkış olur).
UPDATE sefer SET varis_gecikme_dk = kalkis_gecikme_dk
 WHERE extract(epoch FROM planlanan_varis) + varis_gecikme_dk * 60 <= extract(epoch FROM planlanan_kalkis) + kalkis_gecikme_dk * 60;

ALTER TABLE sefer ADD CONSTRAINT sefer_guncel_sure_ck
    CHECK (extract(epoch FROM planlanan_varis) + varis_gecikme_dk * 60 > extract(epoch FROM planlanan_kalkis) + kalkis_gecikme_dk * 60);
