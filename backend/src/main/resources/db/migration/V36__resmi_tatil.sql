-- Resmi tatil listesi (F5, MK-178): yönetici elle girer; YALNIZ GÖSTERİM (aylık çizelge başlığında işaret). Kural motoru, puantaj, adalet, yedek ve çizelge toplamları tatilden etkilenmez.
-- TOHUMLANMAZ: bayram tarihlerinin doğruluğu bu ürünün iddiası değildir.
-- Geri alma: DROP TABLE resmi_tatil.
CREATE TABLE resmi_tatil (
    tarih     DATE        PRIMARY KEY,
    ad        VARCHAR(80) NOT NULL,
    versiyon  BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT resmi_tatil_ad_ck CHECK (length(btrim(ad)) > 0)
);
