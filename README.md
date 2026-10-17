# Makinist Atama

Demiryolu işletmesinde makinist atamasını yapan atamacı için masa uygulaması (Faz 1: Atamacı Masası). Makinist kartı (bröve, belge tarihleri, izin),
Excel'den sefer yükleme, kural kontrollü atama, otomatik atama, sefer değişikliği (iptal/gecikme), yazdırılabilir liste ve raporlar. **Karar destek
aracıdır; mevzuata uyum garantisi vermez.** Tüm eşikler parametredir ve kaynakları/güvenilirlikleri ekranda görünür.

Durum: **M3 (Değişiklik ve otomatik atama)** tamam (M2 üzerine): giriş, tanımlar (gar/hat/araç tipi), makinist kartı, sefer listesi ve Excel yükleme, demo veri, saf kural
motoru, **Ayarlar ekranı** (yönetici; eşik, kural şiddeti ve uygulama ayarı değişimi nedenli ve kayıtlı; "varsayılana dön"), **atama masası** (gün listesi + aday paneli: kurala uymayan makinist
soluk görünür ve nedeni yazar; ASIL/YARDIMCI; UYARI'da gerekçe, değiştirme/kaldırmada neden), **yazdırma** (A4 yatay, `#/yazdir/gun/<tarih>`, tarayıcıdan
Yazdır), **belge panosu**, **sefer iptal/gecikme** (etkilenen atamalar canlı KIRMIZI, "yerine ata" en uygun 3 aday), **otomatik atama** (önizle + uygula) ve **denetim raporu** (Excel dahil). Adalet, izin/yedek ve puantaj raporları M4'te eklendi.

- Backend: Java 21, Spring Boot 4.1, PostgreSQL 17, Apache POI (`backend/`)
- Frontend: Vue 3, Vite, TypeScript (`frontend/`); üretimde Spring Boot jar'ının içinden servis edilir
- Paketleme: Docker Compose (`db` ve `app` servisleri), yedek betikleri `ops/`

## Portlar (ihale-takip ile yan yana çalışır)

| Hizmet | Port |
|--------|------|
| Veritabanı (Docker, ana makine) | 5434 |
| Backend / paket | 8081 |
| Vite geliştirme | 5174 |

## Kurulum (geliştirme)

Komutlar proje kökünden, PowerShell'de çalıştırılır.

1. `Copy-Item .env.example .env`, sonra `.env`'i düzenleyin (`.env` git'e girmez): `DB_PAROLA`, ilk yönetici `MAKINIST_ADMIN_EPOSTA` /
   `MAKINIST_ADMIN_SIFRE` (en az 10 karakter; yalnız kullanıcı tablosu boşken tohumlanır). Yollarda ileri eğik çizgi kullanın.
2. Veritabanı: `docker compose up -d db --wait`
3. Frontend bağımlılıkları: `npm --prefix frontend install`

```powershell
.\backend\mvnw.cmd -f backend\pom.xml spring-boot:run "-Dspring-boot.run.profiles=dev"   # http://localhost:8081 (API)
npm --prefix frontend run dev                                                           # http://localhost:5174 (arayüz, /api -> 8081)
```

`dev` profili kökteki `.env`'i okur ve demo veri uçlarını açar: giriş yaptıktan sonra yönetici olarak **Ana sayfadaki "Demo veriyi yükle" düğmesi** (ya da `POST /api/demo/yukle`; 68 uydurma makinist
`D001..D068`, 4 gar, 4 hat, 3 araç tipi, yükleme gününden başlayan 31 günlük ~3.100 sefer; makinist sayısı ve bröve dağılımı sefer talebine göre ayarlıdır, tipik haftada
otomatik atama seferlerin ~%97'sini doldurur, kalanı izin, belge süresi, hafta tatili gibi nedenlerle boş kalır; puantaj oranları yalnız demo yüklemesinde dolar; dizel ekibi 8 kişi
haftalık 45 saati aşar ve puantajda fazla mesai görünür (varsayılan K-14 kapalı); demo hiçbir kural ayarına dokunmaz) ve "Demo veriyi sıfırla" / `POST /api/demo/sifirla`. **Gerçek kişi verisi yoktur ve tasarlanmaz** (TC kimlik,
rapor tanısı, telefon alanı yok).

## Kullanım (atamacı)

1. **Seferler**: günlük liste, elle ekleme ya da Excel (şablonu indirin; 5 MB üstü dosya yüklenmez).
2. **Atama Masası**: günü seçin, seferi seçin; sağdaki aday listesi uygunları başa alır (bu ay en az çalışan önce). Uygun olmayan makinist soluk ve nedenli
   görünür, seçilemez. Kural UYARI veriyorsa gerekçe yazılır; mevcut atamayı değiştirmek/kaldırmak için neden seçilir. Sunucu ENGEL'li atamayı hiçbir
   yoldan yazmaz (eşzamanlı iki atamacıda bile).
3. **Yazdır**: masadaki Yazdır bağlantısı menüsüz listeyi açar; tarayıcının Yazdır komutu (A4 yatay, 100 sefer ≈ 3 sayfa).
4. **Belgeler**: ehliyet/sağlık/psikoteknik/bröve ve 18 aylık sürüş süresi dolumu (dolmuş, yaklaşan).
5. **Ayarlar** (yalnız yönetici, `/ayarlar`; eski `/parametreler` buraya yönlenir): tüm eşikler, kural şiddetleri ve uygulama ayarları gruplu tek sayfada (açıklama, birim, alt-üst sınır, kaynak/güvenilirlik); her değişiklik neden ister ve değişiklik kaydına yazılır; "Varsayılana dön" ilk değeri geri yazar (`varsayilan` sütunu: sabit tohum değerleri, V29 + V31); "Sistem" bölümü salt okunur bilgidir. Yeni varsayılanlar (V30, OK-24): **K-14 KAPALI** (haftalık 45 saat üstü engel değil, fazla mesai sayılır; üst sınırı günlük 11 s, hafta tatili ve yıllık 270 s koyar; gece nöbetli haftada fazla mesai K-17 ile yine engeldir), dinlenme 11 s, **K-23 ENGEL ve 20 dk**. V30 kurulu veritabanında yalnız elle değiştirilmemiş değerleri çevirir. Ayar API'si: `GET /api/ayar` (yönetici), `PUT /api/ayar/{anahtar}`.
   K-22 (bitmiş sefere atama) varsayılan UYARI'dır; ENGEL ya da KAPALI yapılabilir.
   K-23 (aynı nöbette ardışık iki görev arası en az aktarma/dönüş süresi, `nobet.asgari_aktarma_dk`) varsayılan olarak ENGEL ve 20 dk'dır (V28 tohumu KAPALI/0 idi; V30 çevirir); açıkken önceki görevin varışı ile sonrakinin kalkışı
   arasında en az o kadar dakika aranır (aynı trenin ardışık parçaları ve yolculuk geçişleri muaf). Aktarma süresi nöbet ayırma süresinin dakikasından küçük olmalıdır (ayırma 4 s iken en çok 239 dk). Demo bu ayarı değiştirmez.
6. **Sefer değişikliği**: masada seferin Gecikme / İptal düğmeleri (neden zorunlu). Etkilenen atama kırmızı "KONTROL" olur ve nedeni yazar (saklanmaz, her okumada
   hesaplanır: izin, belge ya da parametre değişince kendiliğinden güncellenir); "Yerine ata" mevcut makinist hariç en uygun 3 adayı gerekçesiyle önerir.
7. **Otomatik ata**: tarih aralığı seçin, önizleyin, uygulayın. Seferler kalkış sırasıyla gezilir; kurallara tam uyan (ENGEL de UYARI da olmayan) ve bu ay en az
   çalışmış makinist seçilir (eşitlikte km, sonra sicil). Yalnız ASIL doldurulur; elle atanmış ve iptal seferlere dokunulmaz; kimse uygun değilse sefer boş
   kalır ve nedeni yazar. İkinci çalıştırma yeni atama üretmez.
8. **Denetim raporu**: kim, ne zaman, neyi, hangi nedenle değiştirdi (bölümler, süzgeçler, Excel, yazdırma). Makinist süzgeci o makinistin atandığı, yerine
   başkası atandığı ve seferi iptal/gecikmeden etkilendiği kayıtları bulur; otomatik atamalar satırda "(otomatik)" ile ayırt edilir.
9. **Adalet raporu**: ay seçin; AKTIF makinist başına sefer sayısı, görev saati, km, gece saati, izin günü ve ortalamadan fark (iptal sayılmaz; gecikme ve hazırlık/alıkoyma
   dahil; ay sınırını aşan vardiya gece yarısından bölünür; süre ve gece saati puantajla AYNI hesaptır; km'si girilmemiş seferler dipnotta). Excel ve yazdırma var.
10. **İzinler ve Yedek planı**: izin talebi açılır, atamacı/yönetici onaylar ya da reddeder (kimse kendi talebini onaylayamaz; aynı makinistin bekleyen/onaylı iziyle çakışan talep reddedilir; yalnız ONAY atamayı etkiler); onaydan sonra etkilenen atamalar ve yedek
    açığı gösterilir. Yedek planı gün gün ihtiyaç (`yedek.oran`, `yedek.min` - kaynaksız yer tutucu), mevcut ve açığı verir.
11. **Puantaj**: aylık fiili (planlanan kalkıştan güncel varışa, hazırlık/alıkoyma dahil) ve ücretlendirilen çalışma (gece yarısını aşan vardiya tek gün sayılır), fazla mesai (hafta Pazar'ı hangi aydaysa o aya), gece süresi ve km. Saat ücreti / km tutarı parametresi
    boşsa yalnız miktar gösterilir ("oran girilmedi"). **Ön hesaptır, ücret bordrosu değildir**; demo verilerde "DEMO – gerçek değil" etiketi çıkar.
12. **Özel kurallar** (menüde "Özel kurallar"; yönetici kurar, atamacı yalnız okur): yeni kural = ölçü + pencere + koşul (en fazla / en az) + kapsam + şiddet + gerekçe.
    Ölçüler: toplam görev / gece süresi, tek sefer süresi, görev / gece nöbeti sayısı, ardışık çalışma / gece günü, en uzun görevsiz aralık, izin günü sayısı, nöbetler
    arası dinlenme. Pencereler: sefer, nöbet, gün, hafta, ay, yıl, kayan N gün (2–366), seri. Kapsam: hat, araç tipi, kalkış / varış garı, tren türü (Yük / Yolcu;
    seferde isteğe bağlı, Excel'de isteğe bağlı "Tren Türü" kolonu, boş sefer hiçbir tür kapsamına girmez), vardiya (gece / gündüz). Kural kaydedilmeden önce
    "Dene" bugünden 31 gün için kaç atamanın kırmızı olacağını sayar (hiçbir şey yazmaz). Özel kurallar yerleşik kurallarla birlikte (VE) uygulanır, en sıkısı
    belirleyicidir; kural kapatılabilir (silinmez), her değişiklik neden ister ve denetim raporunda okunur metinle görünür. "En az" kuralları yalnız azalan
    ölçülerde vardır (izin günü, görevsiz aralık, dinlenme): boş günler izin sayılır, kural ayın başında engellemez, eşiğin altına düşüren ilk atamada engeller.
    Yıl penceresi olan kural açıkken yükleme penceresi yıl başından yıl sonuna uzar (otomatik atama önizlemesi buna göre yavaşlar; kural yokken hiçbir şey değişmez).
13. **Kadro İhtiyacı** (yalnız yönetici): seçilen aralıktaki tarife için mevcut kadronun doluluk eğrisi ve %98 / %99 / %100 için gereken ek makinist. Hesap "temiz sayfadan" başlar (mevcut
    atamalar yok sayılır) ve **%13 devamsızlık** varsayar; bu yüzden masadaki gerçek plan doluluğundan (devamsızlıksız, gerçek izinlerle) farklı çıkar. Eğri paralel hesaplanır: iş parçacığı
    sayısı `kadro.is_parcacigi` ayarıdır (0 = otomatik: çekirdek sayısının yarısı, en çok 6). **8 aylık geçmişli kadro eğrisi için en az 8 çekirdek önerilir; 4 çekirdekte yaklaşık 38 sn sürer.**

## Testler

| Ne | Komut |
|----|-------|
| Hepsi (backend + frontend tip, lint, build, test) | `.\scripts\dogrula.ps1` |
| Backend testleri (**Docker açık olmalı**) | `.\backend\mvnw.cmd -f backend\pom.xml test` |
| Tek backend sınıfı | `.\backend\mvnw.cmd -f backend\pom.xml test "-Dtest=KuralMotoruTest"` |
| Ölçek (yavaş) testleri | `.\backend\mvnw.cmd -f backend\pom.xml test -Pyavas` |
| Frontend testleri | `npm --prefix frontend run test:unit -- --run` |
| Yedek doğrulaması | `bash ops/yedek-dogrula.sh` (son satır `OK`) |

Backend testleri gerçek PostgreSQL 17 kullanır: Testcontainers kendi geçici kabını açar (geliştirme veritabanına dokunmaz). Kural motoru (`kural`
paketi) saf Java'dır ve veritabanı olmadan test edilir.

**Süre (ölçüm 2026-10-15, 12 çekirdek):** tam backend paketi ≈ 4:25 (önce ≈ 12 dk; 1139 test), frontend testleri ≈ 8 sn (önce 35–50 sn), `dogrula.ps1` toplamı ≈ 5 dk. Hızlandırma yalnız test altyapısındadır, kapsam aynıdır:
testlerde BCrypt maliyeti 4 (üretim 10 kalır; `HizliSifreleyici`, `SifreleyiciMaliyetiTest` kilitler), `SabitTarihliDemo.yukle` demoyu JVM başına bir kez yükleyip sonrasında `demo_yedek` şemasından geri yükler,
surefire `forkCount=2` (`-DtestForkSayisi`; her fork kendi JVM'i ve PostgreSQL kabıyla çalışır, bu yüzden ~2 GB boş bellek ve Docker gerekir; 3 ve üstü CPU'yu aşırı abone eder, yavaşlatır) ve frontend `pool: 'vmThreads'`.
`-Pyavas` ölçek koşusu (≈ 10,5 dk, 24 test) ve tek sınıf koşuları seri kalmalıdır (`-Pyavas` zaten 1 fork'tur; tek sınıf için `-DtestForkSayisi=1`); duvar saati bütçeli kapılar eşzamanlı yükte yanlış kırmızı verebilir.

## Üretim (Docker Compose)

```powershell
docker compose --profile app up -d --build --wait    # http://localhost:8081  (sağlık: curl.exe -s localhost:8081/actuator/health)
```

`docker compose down -v` **geliştirme verisini siler: kullanmayın.**

## Makinist hesabı (telefon görünümü)

Atamacı ya da yönetici, **Makinistler → makinist kartı → Hesap** bölümünden **Hesap aç** der: giriş adı küçük harf sicildir, **geçici parola yalnız o ekranda bir kez
gösterilir** (kaydedilmez, denetim kaydına girmez). Makinist telefonda aynı adresten sicil + geçici parolayla girer, hemen kendi parolasını belirler ve yeniden girer;
bundan sonra yalnız kendi ekranlarını görür (görevler, belgeler, izin talebi, aylık özet). Parola unutulursa kartta **Parola sıfırla**; 5 hatalı denemede hesap
15 dakika kilitlenir, **Kilidi aç** hemen açar. Hesap açma, sıfırlama, kilit açma ve parola değişimi denetim raporunda görünür.

**Gördüm, takas, görevi bırakma.** Makinist görevinde **Gördüm**'e basar (görev değişirse kart **DEĞİŞTİ** olur, yeniden onay ister); atamacı masasında her atamanın "gördü / görmedi"
durumu görünür. **Takas iste** (karşı makinist kabul eder, sonra atamacı onaylar) ve **Bırak** (doğrudan atamacıya) kalkıştan en az 12 saat önce açılır; hastalık nedenli bırakma bu sınıra
girmez. Atamacı onayında kural motoru iki makinist için (elde kalan görevler dahil) yeniden çalışır; ENGEL varsa onaylanamaz, talep açık kalır, atamacı reddedebilir. Sınırlar uygulama ayarlarındadır (yönetici, `/api/ayar`)
(`takas.son_saat`, `takas.karsi_onay_saat`, `takas.gunluk_acma`: makinist başına 24 saatte açılabilecek talep, geri çekilenler dahil). Atamacı masasında üst çubuk "Bekleyen talep: n" gösterir.

**Yolculuk (yolcu olarak gitme) ve konaklama.** Makinist başka bir gara görevsiz yolcu olarak gidebilir: atama satırı `YOLCU` rolüyle yazılır (bir seferde birden çok olabilir; sürüş sayılmaz, belge/yetki sorulmaz, ama çakışma, dinlenme, konum ve nöbet kuralları onu görür). Süresi `yolculuk.calisma_sayilir` (varsayılan 1) ayarına göre çalışma süresine eklenir; km hiçbir zaman eklenmez. Atama masasında **Yolculuk öner** (K-20 konum uyarılı aday için en çok 3 sefer) ve **Yolculukla ata** (tek işlem), otomatik atamada isteğe bağlı **Garda aday yoksa yolculukla ara** kutusu vardır. Diğer ayarlar: `yolculuk.yuk_trenine_binilir`, `yolculuk.aktarma_dk`, `yolculuk.arama_saat`, `konaklama.oneri_min_saat` (`/api/ayar`). Yolcu satırı yazıldıktan sonra asıl görev kaldırılırsa yolcu satırı "Yolculuktan sonra bu garda görev yok" uyarısı taşır (kırmızı olmaz). Konaklama dış gar kaydıdır (yatakhane/otel); kural motorunu etkilemez, önerilir ama otomatik yazılmaz; aylık gece raporu ve Excel **Konaklama Raporu** sayfasındadır. Makinist kendi konaklamasını **Görevlerim → Konaklamam** ekranında görür.

Uygulama içi oturum, imza değildir; asıl tebliğ yolu panoya asılan listedir. **Paylaşılan telefon:** oturum 8 saat boşta kalana dek açık durur; ortak kullanılan telefonda
makinist işi bitince **Çıkış**'a basmalıdır.

MAKINIST hesabı hiçbir personel/yönetici ucuna giremez (sunucuda iki katlı yetki kapısı); yeni uç eklenirken rol beyanı (`@Personel`, `@SadeceYonetici`, `@HerkesGiris`,
`@SadeceMakinist`, `@Acik`) yazılmazsa uç herkese 403 verir ve `YetkiKapisiTest` kırılır.

## Yedek ve geri yükleme

Git Bash'te: `bash ops/yedekle.sh` (veritabanı dökümü; `YEDEK_DIZINI` OneDrive dışında olmalı), `bash ops/geri-yukle.sh --evet <dosya>`,
`bash ops/yedek-dogrula.sh`.

## Dağıtım güvenliği

Uygulama düz HTTP konuşur ve oturum çerezi varsayılan olarak `secure=false`'tur: ağa açılacaksa **TLS sonlandıran bir ters vekil zorunludur**
(sonra `MAKINIST_COOKIE_SECURE=true`); uygulamayı doğrudan ağa açmayın. Makinist hesapları telefondan düz HTTP ile girerse parola ve oturum çerezi ağda açıkta gider:
makinist girişini açmadan önce TLS vekili şarttır.

## Sorun giderme

- Docker Desktop çökerse/başlamazsa: `%LOCALAPPDATA%\Docker\run` ve `docker-secrets-engine` klasörlerini birlikte yeniden adlandırıp Docker Desktop'ı
  bir kez başlatın.
- Port çakışması: 5432 yerel PostgreSQL'e, 5433/8080/5173 ihale-takip'e ayrılmıştır; bu proje 5434/8081/5174 kullanır.
- PowerShell'de `curl` yerine `curl.exe` kullanın.
