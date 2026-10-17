-- M4 review WARNING-1 / OK-6: görevin başlangıcı, atamanın yapıldığı andaki kalkış gecikmesine bağlıdır.
-- Atama gecikme girilmeden önce yapıldıysa (0) makinist planlanan saatte görevdeydi; gecikme bilindikten sonra yapıldıysa başlangıç güncel kalkıştır.
-- Eski satırlar 0 alır (= önceki davranış: planlanan kalkıştan say).
ALTER TABLE atama ADD COLUMN kalkis_gecikme_dk INTEGER NOT NULL DEFAULT 0;
