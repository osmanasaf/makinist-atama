-- F2c (MK-58..MK-60): ara istasyonda makinist değişimi. Parça = sıradan sefer satırı; bu sütunlar yalnız etiket + yükleme anahtarı + gecikme/iptal zinciridir.
-- parca_no / parca_sayisi: "2/2" etiketi (parçasız seferde ikisi de NULL). tren_gunu: zincirin 1. parçasının `tarih`'i, DEĞİŞMEZ tren kimliği
-- (parça 2 gece aşıp ertesi güne düşünce `tarih` değişir; zincir `tren_gunu` ile bulunur, saat sırası heuristiği yok).
ALTER TABLE sefer
    ADD COLUMN parca_no INTEGER,
    ADD COLUMN parca_sayisi INTEGER,
    ADD COLUMN tren_gunu DATE;

ALTER TABLE sefer ADD CONSTRAINT sefer_parca_ck CHECK (
    (parca_no IS NULL AND parca_sayisi IS NULL AND tren_gunu IS NULL)
    OR (parca_no IS NOT NULL AND parca_sayisi IS NOT NULL AND tren_gunu IS NOT NULL -- NULL CHECK'i geçirir: açıkça iste
        AND parca_sayisi BETWEEN 2 AND 6 AND parca_no BETWEEN 1 AND parca_sayisi));

-- V6'nın (tarih, tren_no) tekilliği parçaları engellerdi: parçasızlar için eşdeğer işlev-indeksi (COALESCE(parca_no, 0)) ile değişir.
ALTER TABLE sefer DROP CONSTRAINT sefer_tarih_tren_uk;
CREATE UNIQUE INDEX sefer_tarih_tren_parca_uk ON sefer (tarih, tren_no, (COALESCE(parca_no, 0)));
-- Zincir bütünlüğü: bir tren gününde aynı parça numarası tek satır; ayrıca zincir sorgusunun indeksi.
CREATE UNIQUE INDEX sefer_tren_gunu_parca_uk ON sefer (tren_gunu, tren_no, parca_no) WHERE tren_gunu IS NOT NULL;
