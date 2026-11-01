# Gösterim rehberi (15–20 dakika)

Bu rehber, uygulamayı bir makinist atamacısına ya da bir kurum yetkilisine gösterirken izleyeceğiniz yolu anlatır. Ekrandaki makinistler, seferler ve izinler tamamen uydurmadır (Demo Makinist 01 … 68); gerçek kişi verisi yoktur.

## 0. Kısa senaryo: en etkileyici 10 adım (15 dakika)

Süre kısıtlıysa yalnız bunları gösterin. Ayrıntılı tıklama yolları aşağıdaki bölümlerde; burada her adımda **ne söylenir** yazıyor. Sırayı değiştirmeyin: sıra, demo verinin "temiz" kalması için seçildi (aşağıda "Hazırlık sırası").

| # | Süre | Yapın | Ne söylenir |
|---|------|-------|-------------|
| 1 | 1:00 | Giriş, Ana Sayfa, **Demo veriyi yükle** (yaklaşık 15 sn). | "Bu 68 makinist ve 3100 seferlik uydurma bir demo. Her sabah ilk bakacağınız sayfa bu: bugünün seferleri, kontrol bekleyen atamalar, süresi dolan belgeler, izin talepleri." |
| 2 | 1:30 | **Atama Masası → Otomatik ata → Önizle → Uygula** (bugünden 7 gün). İsteğe bağlı: **Daha çok sefer doldur**. | "Bir haftalık işi bir tuşta yapıyor: yaklaşık 600 sefer, 3-4 saniye. Önce önizliyor, siz onaylayınca yazıyor. Kural dışı tek atama yok: kırmızı sayısı 0." |
| 3 | 2:00 | Boş kalan seferi açın (14 Eki, AK-066): satırdaki nedeni, sağdaki adayları gösterin; soluk adaya basın (olmaz); sarı adayda gerekçe yazıp **Onayla**. Nedenlerde ya da soluk adayda **"Aktarma süresi N dk < 20 dk"** (K-23) çıkarsa onu da gösterin. | "Makinist 'neden olmaz' diye sormak zorunda kalmıyor: her boş sefer için nedeni yazıyor. Kural dışına izin vermiyor; uyarılıda gerekçe istiyor, kayıt altına alıyor. Aynı makinist bir trenden iner inmez 1 dakikada diğerine binemez: nöbet içi ardışık iki görev arasında en az 20 dakika aktarma süresi kuralı varsayılan olarak açık (Ayarlar → K-23); gerçek değeri siz belirlersiniz." |
| 4 | 2:00 | Dolu bir seferde **Gecikme** 120 → kırmızı **KONTROL** ve nedeni → **Yerine ata** → birincide **Seç**. | "Gecikme gelince zincirleme etkiyi hemen kırmızıyla gösteriyor, yerine kimin geleceğini en az çalışandan başlayarak öneriyor." |
| 5 | 2:00 | **Özel kurallar → Yeni kural** (dizel hatlarda nöbet en fazla 8 saat) → **Dene** → **Kaydet** → masada dizel gün. | "Kural koda gömülü değil, siz tanımlıyorsunuz. Kaydetmeden önce kaç atamayı kırmızı yapacağını gösteriyor: yanlış kurala karşı güvenli." |
| 6 | 0:30 | **Yazdır**. | "Bugünkü gibi panoya asılabilir: menüsüz, siyah-beyaz, A4 yatay; kim yazdırdı, ne zaman, yazıyor." |
| 7 | 3:00 | Telefon (375 px): makinist girişi, **Gördüm**; atamacıda gecikme girince "DEĞİŞTİ: yeniden onaylayın"; **Takas iste** → karşı makinist **Kabul et** → atamacı **Onayla**. | "Makinist sahada kendi telefonundan görevini görüyor; 'gördüm' tebliğ kanıtı oluyor; değişince yeniden onay ister. Takas iki yönlü kural denetiminden geçmeden olmuyor." |
| 8 | 1:30 | **Yolculukla ata** (konum uyuşmazlığı olan aday → Yolculuk öner) ya da parçalı tren gecikmesi. | "Makinist başka gara yolcu olarak gidip orada sefer sürebiliyor; uzun trende yol ortasında değişim de var. İkisi tek işlemde, biri reddedilirse ikisi de yazılmaz." |
| 9 | 1:30 | **Adalet Raporu**, **Puantaj**. | "Kim fazla yüklenmiş, kim az: tek bakışta. Puantaj kontrol amaçlı, bordro yerine geçmez." |
| 10 | 2:30 | **Kadro İhtiyacı → Hesapla** (15-20 sn), doluluk eğrisini gösterin; son olarak **Denetim Raporu → Raporu getir**. | "Mevcut kadro tarifenin yüzde kaçını dolduruyor, yüzde 98 ve 99 için kaç makinist daha gerekir, son yüzdeler ne kadar pahalı: işe alım kararı için. Kadro tahmini yüzde 13 devamsızlık varsayar, o yüzden masadaki gerçek plandan biraz düşük görünür. Ve her şey silinemez kayıtlı: kim, ne zaman, hangi gerekçeyle." |

Opsiyonel eklemeler (süre kalırsa): Konaklama (2d), Kondüktör (2f), Belge panosu.

**Not (varsayılanlar):** Demo artık hiçbir kural ayarına dokunmaz. Taze kurulumda varsayılanlar zaten şunlardır: haftalık 45 saat üstü **engel değil, fazla mesai** sayılır (K-14 kapalı; günlük 11 saat, hafta tatili ve yıllık 270 saat fazla mesai sınırı yine engeldir; gece nöbetli haftada fazla mesai yazılmaz, K-17), dinlenme 11 saat, aynı nöbette iki sefer arası en az 20 dakika (K-23 ENGEL). Hepsi **Ayarlar** ekranından değişir; "Varsayılana dön" ile geri alınır. Demo veriyi sıfırlamak ayarlara dokunmaz.

**Ayarlar ekranı (yalnız yönetici, menüde "Ayarlar"):** Tüm eşikler, kural şiddetleri ve uygulama ayarları (makinist görünümü, takas/izin sınırları, yolculuk, kadro) tek sayfada, gruplar hâlinde durur. Her satırda açıklama, birim, alt-üst sınır, kaynak ve güvenilirlik görünür; değer değiştirince **neden zorunlu**, kayıt "Denetim Raporu"na yazılır. Varsayılandan farklı satırda "Varsayılandan farklı" etiketi çıkar; **"Varsayılana dön"** ilk değeri (gerekçesiyle) geri yazar. Değişiklik hemen geçerlidir: mevcut atamalar yeni kurala göre yeniden değerlendirilir, kural dışı kalanlar Atama Masası'nda kırmızı görünür. Sayfanın sonundaki "Sistem" bölümü (giriş kilidi, parola uzunluğu, oturum süresi, Excel sınırları) yalnız bilgidir; bunları değiştirmek için geliştirici gerekir. Ekranın reddettiği tutarsız girişler: ör. aktarma süresi nöbet ayırma süresinin dakikasından küçük olmalı, gece görevi süresi günlük sınırı aşamaz.

**Hazırlık sırası (önemli):** Demo veriyi yükledikten HEMEN sonra, Otomatik ata'dan ÖNCE Ana Sayfa'daki **Parçalı örnek ekle** ve **Yolculuk örneklerini ekle** düğmelerine basın. Otomatik ata parçalı trenin iki parçasını da doldurur; yolculuk örneği sonradan eklenirse bir makinistin dinlenmesini bozup 1 kırmızı atama doğurur. **Kondüktör örneği ekle** düğmesine yalnız kondüktör anlatacaksanız ve en sonda basın: basınca yolcu trenlerinin hepsinde "Kondüktör eksik" bilgi rozeti çıkar ve masa kalabalıklaşır. Otomatik atamayı 7 günle sınırlayın: 28 günlük plan makinistlerin takvimini o kadar doldurur ki Takas (Adım 7) için kurala uyan hedef bulmak deneme yanılma olur.

## 1. Gösterimden önce (10 dakika önce, bir kez)

Üç ayrı PowerShell penceresi açın, proje klasöründe (`C:\Projeler\makinist-atama`) sırayla çalıştırın:

1. Veritabanı: `docker compose up -d db --wait`
2. Sunucu: `.\backend\mvnw.cmd -f backend\pom.xml spring-boot:run "-Dspring-boot.run.profiles=dev"` (pencerede "Started MakinistAtamaApplication" yazınca hazır, yaklaşık 10 sn)
3. Arayüz: `npm --prefix frontend run dev`

Tarayıcıda **http://localhost:5174** açın (127.0.0.1 Vite sunucusunda açılmayabilir; ikinci oturum için gizli pencere ya da `http://[::1]:5174` kullanın). Giriş bilgileri proje klasöründeki `.env` dosyasında: e-posta `MAKINIST_ADMIN_EPOSTA`, parola `MAKINIST_ADMIN_SIFRE` satırları (bu hesap "Yönetici"; Ayarlar ve demo düğmeleri yalnız yöneticide görünür).

Bir kez deneme yapın: giriş yapın, Ana Sayfa'da **"Demo veriyi sıfırla" → "Evet, sıfırla"**, sonra **"Demo veriyi yükle"** (5-15 sn, makineye göre; ekranda "Yükleniyor… (yaklaşık 15 sn sürer)" yazar, sayfayı yenilemeyin). Böylece gösterim anında veri tertemiz ve tarihler "bugünden" başlar. Gösterimde bu adımı baştan canlı oynayabilirsiniz (Adım 2).

Ayrıca: tarayıcıyı tam ekran yapın, 1366 piksel ve üstü geniş bir ekranda gösterin. Yazıcı göstereceksiniz: bir kez "Yazdır" deneyin (Adım 8), yazdırma penceresinin açıldığını görün; bunu prova sırasında göremedik.

Kapatmak için: iki pencerede Ctrl+C. Veritabanı kapsayıcısı açık kalabilir.

## 2. Adım adım gösterim

Her adımın sonunda **Dikkat çekin** satırı, o an söylemeniz gerekeni özetler. Saatler yaklaşık.

**Not:** Otomatik ata, kalkışı geçmişte kalan (bitmiş ya da başlamış) seferleri hedeflemez ve "boş" saymaz; özet satırında "(N geçmiş sefer atlandı)" yazar. Bu yüzden başlangıcı bugün bırakabilirsiniz. Aşağıdaki sayılar bugünden başlayan 7 güne göre (kapanış turunda ölçüldü, K-23 20 dk açıkken: 599 atandı, 3 boş, 100 geçmiş atlandı, kırmızı 0). Masada, atanmamış geçmiş seferler hâlâ BOŞ satırı olarak görünür.

| # | Süre | Yapın | Dikkat çekin |
|---|------|-------|--------------|
| 1 | 0:30 | Giriş sayfasında e-posta ve parolayı yazın, **Giriş yap**. | Tek ekran, sade giriş. Yanlış parolada "E-posta veya parola hatalı." çıkar. |
| 2 | 1:00 | Ana Sayfa. Veri yokken: "Bugün için sefer yok…" ve **Demo veriyi yükle** düğmesi. Düğmeye basın, yaklaşık 15 saniye bekleyin. | "68 makinist, 3100 sefer, 15 izin yüklendi." Dört kart: bugünün seferleri, kontrol bekleyen (kırmızı) atamalar, belgesi dolan/dolacaklar, izin talepleri. Kartta "bugünün seferleri 100" görünür; saat ilerledikçe çoğu "geçmiş" sayılır (gün içinde gösteriyorsanız bunu söyleyin). "Bu sayfa her sabah ilk bakacağınız yer." |
| 3 | 1:30 | **Atama Masası** → sağ üstte **Otomatik ata**. Başlangıç: bugün (varsayılan; geçmiş seferler atlanır), Bitiş: 6 gün sonrası (varsayılan). **Önizle** (3-4 sn), sonra **Uygula** (3-6 sn). İsteğe bağlı: **Daha çok sefer doldur** kutusu (önizleme 20 saniyeye kadar sürer, ekranda "Hesaplanıyor…" yazar). | "Bir haftalık işi tek tuşla yapıyor: yaklaşık 600 sefere makinist atadı (100 geçmiş sefer atlandı), 3 sefer boş kaldı." 28 günlük aralıkta "Daha çok sefer doldur" boşları 58'den 49'a indirdi (makinist eklemeden; sayı başlangıç gününe göre değişir, hesap 15 sn kadar sürer). Önizleme "yapmadan önce gösterir"; beğenmediğiniz atamayı sonra tek tek "Kaldır" ile geri alabilirsiniz. Kural: en az çalışan makinist öne alınır (adalet). |
| 4 | 1:30 | Aynı pencerede aşağı inip **Boş kalanlar** listesine bakın. Pencereyi kapatın; masada günü **Sonraki gün ›** ile yarına alın, **BOŞ** yazan satıra bakın (nedeni satırın altında yazar), tıklayınca adaylar açılır. | Her boş sefer için "neden" yazıyor: "0/66 uygun: konum 60, dinlenme 4, çakışma 2" gibi. **"Konum"**, makinistin o garda olmadığı demektir (K-20 konum sürekliliği); "dinlenme", "gece çalışması", "araç tipi yetkisi", "izin" ve "aktarma süresi" (önceki sefer biter bitmez başka sefer başlayamaz, K-23) diğer nedenlerdir. "0/66 uygun", UYARISIZ uygun yok demektir; sağdaki listede sarı (uyarılı) adaylar olabilir. Sağda adaylar: kurala uymayan **soluk** ve nedeniyle birlikte, uyarılı olanlar sarı. "Makinist 'neden olmaz' diye sormak zorunda kalmıyor." |
| 5 | 1:30 | Soluk bir makinistin üstüne tıklayın (hiçbir şey olmaz). Sonra "Uyarı var" yazan bir makinistin üstüne tıklayın, **gerekçe** yazın ("Başka uygun makinist yok."), **Onayla**. | "Sistem kural dışı atamaya izin vermiyor, uyarılıda ise gerekçe istiyor." Gerekçe yazılana kadar Onayla düğmesi kapalı kalır. Kayıt altına alınır (Adım 11). |
| 5b | 1:00 | **Ayarlar** → "Çalışma ve dinlenme" grubunda **"Nöbet içinde aktarma süresi" kuralı (K-23, varsayılan ENGEL)** ve hemen altındaki **"Nöbet içinde asgari aktarma süresi" = 20 dk**. Sonra masada aday paneline bakın: ardışık seferi çok yakın olan makinist soluktur ve nedeni "Aktarma süresi 8 dk < 20 dk; önceki görev … bitiyor, sonraki … kalkıyor" yazar. | "Bu bir iş kuralı sorusu: dönüş trenine kaç dakikada binilir? Varsayılan 20 dk, siz değiştirirsiniz; 0 yaparsanız ya da kuralı KAPALI yaparsanız bekleme süresi aranmaz. Her değişiklik neden ister ve kayda geçer; "Varsayılana dön" düğmesi ilk değere getirir. Aynı trenin ardışık parçaları (yol ortasında makinist değişimi) ve yolculuk geçişleri bu kuraldan muaftır. Aynı ekranda haftalık sınırı da gösterebilirsiniz: K-14 kapalıyken haftalık 45 saat üstü engel değil, puantajda fazla mesai olur; K-14'ü ENGEL yaparsanız aynı atamalar anında kırmızı olur ("Haftalık çalışma … 47 s > 45 s"), "Varsayılana dön" ile kapanır." |
| 6 | 1:00 | Dolu bir seferin satırında **Gecikme** → "Kalkış gecikmesi" kutusuna 120 yazın, Neden: Gecikme, **Kaydet**. | Satırda "+120 dk (yeni kalkış …)" görünür. Aynı makinistin hemen ardından işi varsa satır **kırmızı, KONTROL** olur ve nedeni yazar ("Çakışma…, gece nöbeti…"). Kırmızı olmazsa: "Bu makinistin ardışık işi yok, o yüzden kırmızı değil" deyip bir sonraki adıma geçin. Varış kutusunu boş bırakın. Gecikmeli satır listede ve panoda PLANLANAN saatine göre durur (07:00'ye kayan 05:00 treni en üstte kalır); "bilinçli" diye anlatın. |
| 7 | 1:00 | Aynı satırda **Yerine ata** → bir ila üç öneri çıkar → birincide **Seç**, gerekçe yazın, neden seçin, **Onayla**. | "En az çalışan, kurala uyan ilk üç makinist, gerekçesiyle." Ekranda kimin geldiği ("Göreve gelecek") ve kimin çıktığı ("Görevden çıkacak") yazar. Kırmızı satır normale döner. Konum uyarısı gibi uyarılar, ilgili makinistin BİR SONRAKİ işinin satırında sarı not olarak görünür. |
| 8 | 0:30 | Atama Masası sağ üstte **Yazdır** → "Yazdır" düğmesi. | Menüsüz, A4 yatay, siyah-beyaz yazıcıya uygun liste: saat, tren no, makinist, durum (gecikme, iptal, kontrol). Altında "Son güncelleme … · Yazdıran … · Kayıt sayısı". "Bugünkü gibi panoya asılabilir." |
| 9 | 0:45 | Üst menüden **Adalet Raporu** (ay otomatik gelir). | Makinist başına sefer, görev saati, km, gece saati, izin günü, "ortalamadan fark %". "Kim fazla yüklenmiş, kim az kalmış, tek bakışta." "0 saat" rozeti: belgesi dolmuş, çalıştırılamıyor. Gece saati hem burada hem Puantajda aynı hesaplanır. |
| 10 | 0:45 | **Puantaj**. | Üstte oranlar (saat ücreti, fazla mesai çarpanı, gece oranı, km tutarı): hepsi ayarlanabilir. **"DEMO – gerçek değil"** ve "ücret bordrosu değildir" uyarısı. Fazla mesai bir iki makinistte görünür. "Hesap kontrol amaçlıdır, bordro yerine geçmez." |
| 11 | 0:30 | **Belgeler**. | Süresi dolmuş (kırmızı) ve yaklaşan (sarı) sağlık, psikoteknik, hat sınavı ve 18 aylık sürüş süresi uyarıları. Makinist adına tıklayınca kartı açılır. Tarih, belgenin BİTİŞ tarihidir ("61 gün geçti" = bitişten bu yana). |

### Özel kural kurma (2 dakika, önerilir: "kural koda gömülü değil, siz tanımlıyorsunuz")

Adım 3'ten sonra (atamalar yapılmışken) gösterin. Üst menüden **Özel kurallar** → **Yeni kural**.

| # | Yapın | Dikkat çekin |
|---|-------|--------------|
| a | Kural adı: "Dizel hatlarda nöbet en fazla 8 saat". Ölçü: **Toplam görev süresi**; Zaman aralığı: **nöbet**; Koşul: **en fazla 8 s**; Kapsam → Araç tipi: **Dizel Lokomotif**; Gerekçe yazın (örn. "Demo"). | Her seçeneğin altında tek cümlelik açıklama ve "Kural özeti" cümlesi çıkar: "Toplam görev süresi (nöbet) en fazla 8 s · Araç tipi: Dizel Lokomotif". |
| b | **Dene**. | "Bu kural kaydedilirse N atama kırmızı olur (8 personel)" ve örnek satırlar; sayı atanan aralığa göre değişir (28 günlük planda 224 atama, 8 personel; yaklaşık 4 saniye). Dene hiçbir şey yazmaz. |
| c | **Kaydet** → "Evet, kaydet". Sonra **Atama Masası**'nda dizel bir günü (örn. yarın) açın. | Dizel seferlerin satırı kırmızı olur: "Toplam görev süresi (nöbet 14 Eki 05:00): 9 s 9 dk > 8 s (Kural: Dizel hatlarda nöbet en fazla 8 saat)". Anasayfada "Kontrol bekleyen atamalar" artar. Yeni kural yeni atamalarda da engel olur. |
| d | İsteğe bağlı: **Denetim Raporu** → Tür: "Kural eklendi". | Kuralın ne olduğu okunur cümleyle, kim ne zaman hangi gerekçeyle tanımladı. |

Alternatif ("Tek sefer süresi en fazla 4 s", Dizel): son turda yeniden denenmedi; sayılar değişebilir.

Aynı ekrandan "Gece nöbeti üst üste en fazla 3 gün" (Ölçü: Ardışık gece günü) ve "Ayda en az 8 gün izin" (Ölçü: İzin günü sayısı, Pencere: ay, "en az") de kurulur; kısa demo verisinde bunlar kimseyi kırmızı yapmayabilir (Dene "hiçbiri etkilenmez" der), bu da göstermeniz gereken bir sonuçtur: "Yanlış kurala karşı güvenli: kaydetmeden önce etkisini görüyorsunuz."

**Not:** "Demo veriyi sıfırla" özel kuralları da siler (demo sonrası temiz başlangıç). Kuralın gün içi gösterimde silinmesini istemiyorsanız sıfırlamayı gösterimden önce yapın.

İsteğe bağlı (süre kalırsa): **Denetim Raporu** → "Raporu getir". "Kim, ne zaman, hangi gerekçeyle atadı/değiştirdi, kayıtlar silinemez." Gerekçeyle yapılan uyarılı atamalar ayrı bölümde görünür; Sayıştay veya iş müfettişi için Excel'e aktarılabilir.

## 2b. Makinist olarak giriş (telefon görünümü, 3 dakika)

Demo veri yüklenince Ana Sayfa'da **Demo makinist hesapları** kutusu çıkar: ilk üç makinistin (D001, D002, D003) sicili ve **geçici parolası** (yalnız o anda gösterilir; not alın,
sayfayı yenilerseniz kaybolur: gerekirse makinist kartında **Parola sıfırla**). Yeni bir tarayıcı penceresinde (ya da gizli pencerede, atamacı oturumu açıkken karışmasın) ve tercihen
telefon genişliğinde (375 piksel) **http://localhost:5174** açın. Yeni parola en az 10 karakter olmalı ve sicili içermemelidir.

| # | Yapın | Dikkat çekin |
|---|-------|--------------|
| a | Giriş: **E-posta veya sicil** alanına `D001`, parola olarak kutudaki geçici parola. | Aynı giriş ekranı, ama alan sicili de kabul eder. |
| b | Hemen **Parolam** ekranı açılır: geçici parola + en az 10 karakterli yeni parola (iki kez). **Parolayı değiştir** → yeniden giriş ekranı. Yeni parolayla girin. | Geçici parolayla başka hiçbir ekran açılmaz; makinist kendi parolasını kendi belirler. |
| c | Alt sekmelerde gezin: **Görevler** (bugün / yarın / sonraki günler; iptal ve gecikme etiketli), **Belgeler** (kalan gün, süresi dolan/yaklaşan simge + metinle), **İzin** (talep formu), **Özet** (ay seçilebilir). | Telefonda tek elle kullanılır; hedefler en az 44 piksel, yazı büyüyünce taşmaz. Başka makinistin verisi hiçbir yerde yok. |
| d | **İzin**: başlangıç ve bitiş seçip **Talep gönder**. Sonra atamacı penceresinde **İzinler** sayfasında talebi **Onayla**. | Talep makinistin listesinde "Onay bekliyor" olarak durur, atamacıya düşer; onaylanınca "Onaylı" olur. |
| e | Makinist penceresinde masaüstü sayfa adresi yazın (örn. `#/izin`): makinist ekranına geri döner. | Makinist masaüstü menüsünü ve atama ekranlarını göremez; sunucu da bu uçlarda 403 verir. |

## 2c. "Gördüm", takas ve görevi bırakma (5 dakika)

Demo veride yalnız D001, D002, D003'ün hesabı var; takas için iki hesap yeter. D001 ve D002 için ayrı birer tarayıcı penceresi açın (biri normal, biri gizli pencere ya da farklı adres: `localhost` ve `127.0.0.1`; çerezler karışmasın), atamacı üçüncüsü. Önce Adım 3'teki **Otomatik ata** yapılmış olmalı (makinistlerin görevi olsun).

| # | Kim | Yapın | Dikkat çekin |
|---|-----|-------|--------------|
| a | D001 (telefon) | **Görevler** ekranında bir görevde **Gördüm**. | Kart "✓ Gördünüz (saat)" olur. Atamacı penceresinde **Atama Masası**'nda o satır "✓ gördü", üst çubukta "Görülmeyen" sayısı düşer (60 sn içinde kendiliğinden güncellenir). Panoya asılan liste (Yazdır) bu bilgiyi taşımaz, eskisi gibidir. |
| b | Atamacı | Aynı seferde **Gecikme** girin (örn. 30 dk). | D001'in kartı "🔄 DEĞİŞTİ: yeniden onaylayın" olur, eski ve yeni saat yan yana yazar; atamacıda satır yeniden "görmedi" olur. Tebliğ, değişiklikten sonra yenilenir. |
| c | D001 | Kalkışına **12 saatten fazla** olan bir görevde **Takas iste** → listeden D002'nin bir görevini seçin, Neden: İzin → **Talebi gönder**. | Listede yalnız sicil ve sefer saati var; karşı makinistin belge ya da dinlenme bilgisi görünmez. Kurala uymayan seçimde "Bu takas kurallara uymuyor: ..." der ve YALNIZ talebi açanın kendi tarafındaki nedeni yazar (örn. "Dinlenme 10 s 29 dk < 11 s"); karşı makinistin durumu asla yazılmaz. Yoğun planda (28 günlük otomatik atama sonrası) bazı görevler için hiçbir hedef uymayabilir: başka bir görevi deneyin; 7 günlük planda ilk denemede çıkar. Kalkışa 12 saatten az kalan görevde talep açılamaz ("Atamacınıza telefonla bildirin"); hastalık nedenli **Bırak** bu sınırdan muaftır. |
| d | D002 (ikinci pencere) | **Takas taleplerim** (üstte "Takas: 1" rozeti) → **Kabul et** (ya da **Reddet**). | Reddederse D001'in listesinde "Reddedildi" görünür, kimsenin görevi değişmez. Kabulden sonra talep "Atamacı onayı bekleniyor" olur. |
| e | Atamacı | Üst menüden **Takas Talepleri** (Atama Masası'nda "Bekleyen talep: 1" rozeti) → **İncele**: iki tarafın kural kontrolü sicille yazılır (sarı uyarı: gerekçe yazın; kırmızı ENGEL: onaylanamaz, yalnız Reddet). **Onayla**. | İki yönlü kural denetimi: "iki makinist de yeni görevini kurallara uygun üstlenebiliyor mu". Onaydan sonra Atama Masası'nda yeni kırmızı satır oluşmaz. Makinist yalnız "Reddedildi" görür, hangi kuralın bozulduğunu görmez. |
| f | D001 ve D002 | Görevler ekranını yenileyin. | İki taraf da yeni görevini "YENİ" (görülmedi) olarak görür; verdiği görev "Elinizden alınan görevler → Başkasına verildi" kaleminde durur. Eski "gördüm" yeni göreve geçmez. Pano listesi yeni sahipleri yazar. |
| g | D001 | Bir görevde **Bırak** → Neden → gönder. Atamacıda **Takas Talepleri** → **İncele** → önerilen üç adaydan birini seçin, gerekçe yazın → **Onayla**. | Bırakma atamacı onayı olmadan görevi kaldırmaz; onayla "yerine ata" tek adımda yapılır (aday yoksa "Yalnız kaldır"). Yeni makinist atamacıda "görmedi" görünür. |
| h | Atamacı | Bir seferi **İptal** edin (D001'in "gördüm" dediği bir sefer iyi olur). | İptal edilen seferde de satırda "görmedi / gördü" yazar; makinist iptal kartında yeniden **Gördüm** der (makinistin boşuna gara gelmemesi için). |

Takas açma, kabul, ret ve onay kayıtları **Denetim Raporu**'nda "Diğer kayıtlar" bölümünde okunur; "gördüm" kayıtları varsayılan raporda yoktur, yalnız Tür: GORDUM ile ayrı bölümde gelir.

Makinist kartından (Hesap bölümü) **Parola sıfırla** yeni geçici parola verir ve açık oturumları düşürür; **Kilidi aç**, 5 hatalı denemeden sonra kilitlenen hesabı açar.

## 2d. Yolcu olarak gitme ve konaklama (3 dakika)

Makinist başka bir gara **yolcu olarak** (görevsiz, başka bir trende) gidip oradan sefer sürebilir; dış garda kalacağı yer **konaklama** olarak kaydedilir. Örnek veri isteğe bağlıdır
(temel demo yüklemesi değişmez): Ana Sayfa → "Demo veri" kartında **Yolculuk örneklerini ekle** düğmesi (ya da yönetici oturumundayken `POST /api/demo/yolculuk-ornekleri`) (3. günün ilk üç Ankara–Eskişehir seferine D001..D003 yolcu, D004'e Sivas'ta bir gecelik otel).

| # | Yapın | Dikkat çekin |
|---|-------|--------------|
| a | **Atama Masası** → sefer seçin → adaylarda **Rol: Yolcu** → bir makinist → **Onayla**. | Satırda "Yolcu: …" görünür; sefer "BOŞ" kalır (yolcu sürüş değildir). Yolcunun belgesi/yetkisi sorulmaz. |
| b | Boş bir seferde (örn. 7 günlük planda 14 Eki AK-066, aday uyarılı) adayın yanındaki **Yolculuk öner** → bir öneriyi **seçin** (seçilmeden düğme kapalıdır) → uyarı varsa gerekçe yazın → **Yolculukla ata**. | "Konum uyuşmazlığı" uyarısı tek işlemde çözülür: önce yolcu, sonra asıl yazılır; biri reddedilirse ikisi de yazılmaz. |
| c | **Otomatik ata** → **Garda aday yoksa yolculukla ara** kutusunu işaretleyin → **Önizle**. | Varsayılan kapalıdır. Açıkken boş kalacak bazı seferler "+ Yolcu: …" satırıyla dolar; **Uygula** her biri için iki satır yazar. Yolcunun çalışma süresinden sayılması `yolculuk.calisma_sayilir` ayarındadır (yönetici, `/api/ayar`). |
| d | **Konaklama** menüsü → öneri tablosu (28 günlük planda 178 öneri; kalabalıktır, tarih aralığını daraltın) → satırdaki **Kaydet** formu doldurur (kaydetmez) → sayfanın altındaki formda yer tipini seçip **Kaydet**. **Konaklama Raporu**: ay seç, **Excel indir**. | Öneri otomatik yazılmaz. Gece = süre / 24 saat yukarı yuvarlanır (en az 1), ay = giriş günü. Makinist telefonda **Görevler → Konaklamam** ile yalnız kendi kaydını görür. |

## 2d-2. Servis ile intikal (sefer olmayan yolla gar değiştirme) (2 dakika)

Makinist, sistemde sefer olarak kayıtlı OLMAYAN bir yolla (servis, otobüs, taksi) başka bir gara gidebilir: **intikal**. Kural motoru onu yolculuk gibi görür (konum, dinlenme, çalışma süresi `yolculuk.calisma_sayilir`). Örnek isteğe bağlıdır: Ana Sayfa → "Demo veri" → **İntikal örneği ekle** (3 gar çifti süresi + bağlı garı Eskişehir olan ilk makinistin 2. gün Eskişehir→Ankara servisi). **Otomatik ata'dan ÖNCE** basın.

| # | Yapın | Dikkat çekin |
|---|-------|--------------|
| a | **Atama Masası** → sefer listesinin üstündeki **İntikaller** şeridi (2. gün) → **İntikal ekle** / satırda **Düzelt**, **İptal**. | Kural ihlali (ENGEL) varsa yazılmaz, ihlal listesi görünür; kırmızı intikal "KONTROL" rozeti ve nedenleriyle, atama kırmızı sayacından AYRI ("Kırmızı intikal") sayılır. İptal silmez, satır "İPTAL" kalır. |
| b | Boş bir seferde adayın **Yolculuk öner** diyaloğu → sondaki **Servis ile intikal (N dk)** → **İntikalle ata**. | Saatler sunucuda süre tablosundan hesaplanır (Tanımlar → Garlar → **Gar süreleri**); tanımsız gar çiftinde bu satır çıkmaz. Intikal ve atama tek işlemde yazılır; biri reddedilirse ikisi de yazılmaz. |
| c | Makinist telefonu → **Görevlerim**: kartlar sefer ve intikal karışık, kalkış sırasıyla; intikal kartı **Gördüm** ister. | Atamacının notu makinistte görünmez; takas / bırak yok. |

## 2e. Ara istasyonda makinist değişimi (2 dakika)

Uzun bir trende makinist yol ortasında değişir: tren iki **sefer satırı** olur ("DEMO-P1 · 1/2" Eskişehir→Ankara, "DEMO-P1 · 2/2" Ankara→Sivas); her parça ayrı atanır ve kurallar parçanın kendi gar ve saatiyle çalışır. Gerçek veride bu bilgi Excel'in **"Değişim Noktaları"** sayfasından gelir (sefer şablonunda boş sayfa vardır). Örnek isteğe bağlıdır (temel demo yüklemesi değişmez): Ana Sayfa → "Demo veri" kartında **Parçalı örnek ekle** düğmesi (2. gün, DEMO-P1; ikinci basışta "zaten var" der).

| # | Yapın | Dikkat çekin |
|---|-------|--------------|
| a | **Atama Masası** → demo yüklemenin 2. günü → "DEMO-P1 · 1/2" ve "· 2/2" satırları. Örnek, Otomatik ata'dan ÖNCE eklenmişse parçaları otomatik atama doldurur; sonra eklendiyse konum nedeniyle adaylar yalnız uyarılı olur (gerekçe yazarak atayın). Elle seçecekseniz: 1. parça YHT olduğundan hızlı tren ekibinden, **2. parçaya Sivas ekibinden D001–D003** (Elektrikli Dizi yetkili, hesaplı). | Parçalar bağımsız seferdir; konum kuralı parçanın kendi kalkış garına bakar. Hızlı tren ekibi 2. parçada "Araç tipi yetkisi yok" gösterir. D001–D003 hesaplı olduğundan "Benim" ekranı (b adımı) yalnız onlarla görülür. |
| b | 1. parçada **Gecikme** → 40 dk, "Sonraki parçalara da yansıt" işaretli → **Kaydet**. | 2. parça da +40 dk kayar; makinistlerin "Benim" ekranında özet değişir ("Gördüm" yeniden istenir). Kutu yalnız **artırır**: gecikmeyi azaltınca sonraki parça elle düzeltilir (diyalog "Sonraki parçalar değişmedi" der). |

## 2e-2. Seferi ekrandan böl ve birleştir (3 dakika)

Excel'de "Değişim Noktaları" olmayan (parçasız) bir sefer, atamacı tarafından ekrandan bir ara istasyonda bölünebilir; yanlışsa birleştirilir. Mevcut satır 1. parça olur (atama, gecikme, "Gördüm" kaydı o satırda kalır); 2. parça BOŞ açılır.

| # | Yapın | Dikkat çekin |
|---|-------|--------------|
| a | **Atama Masası** → atanmış, kalkışı geçmemiş bir parçasız sefer (ör. Ankara→Sivas) → **Böl** → ara istasyon (ör. Sivas) ve varış/kalkış saatleri (+ sefer KM'si doluysa KM) → **Önizle**. | Önizleme sunucudan gelir ve hiçbir şey yazmaz: parça tablosu (1/2, 2/2, süre, km), "Mevcut atamalar 1. parçada kalır; 2. parça boş açılır; açık takas/bırakma talepleri geçersiz olur" uyarısı ve bölmenin kırmızı yaptığı atamalar (varsa). Hatalı saat/gar Excel ile aynı mesajı verir. |
| b | **Böl** ile uygulayın. | Gün listesinde 1/2 dolu, 2/2 BOŞ. Makinistin "Benim" ekranında özet "Değişti" olur (yeniden "Gördüm" istenir); bu sefere açık takas/bırakma talebi varsa bayatlar. 2. parçaya normal akışla makinist atayın. |
| c | Parçalı satırda **Birleştir** → önizleme → onay. | Koşullar sunucuda: iptal/başlamış parça yok, süre 24 s altı, hat/araç aynı, 2. parçada atanmış her rol 1. parçadaki aynı makinist (yoksa neden yazılır). Silinen parça satırları ve atamalar geri alınamaz; denetim kaydında saklanır. Böl → Birleştir özgün satırı birebir geri verir. |

## 2f. Kondüktör (tren şefi) (2 dakika)

Aynı kural motoru kondüktörü de planlar (bir yolcu trenine bir kondüktör). Ana Sayfa → "Demo veri" kartında **Kondüktör örneği ekle** (4 kondüktör K001..K004; 2.-4. günlerin ilk yolcu trenine K001..K003 atanır; "yolcu trenine kondüktör gerekli" ayarı açılır; temel demo değişmez, ikinci basışta "zaten var" der). K001 hesabının geçici parolası ekranda bir kez gösterilir (telefonda Görevler: rol "Kondüktör").

| # | Yapın | Dikkat çekin |
|---|-------|--------------|
| a | **Atama Masası** → demo yüklemenin 5. günü → yolcu trenlerinde **Kondüktör eksik** rozeti ve üstte sayaç. Sefer seçip rol olarak **Kondüktör** → aday listesi → atayın. | Rozet bilgidir: kırmızı değil, atamayı engellemez. Bröve (araç tipi/hat) sorulmaz; kişisel emniyet belgesi, sağlık, psikoteknik ve zaman kuralları (dinlenme, haftalık süre) çalışır. K004'ün belgesi 14 gün içinde biter: **Belge Durumu**'nda görünür. |
| b | **Otomatik ata** → "Personel türü" = **Kondüktör** → **Önizle** → **Uygula**. | Yalnız kondüktörü olmayan yolcu trenleri doldurulur; yolculuk ve "daha çok sefer doldur" bu türde kapalıdır. Makinistin otomatik atama çıktısı kondüktör kartlarından etkilenmez. |
| c | **Adalet Raporu** ve **Puantaj** → "Personel türü" = Kondüktör. **Takas**'ta kondüktör yalnız kondüktörle takas eder. | Makinist ve kondüktör ayrı gruptur: ortalamalar karışmaz. |

## 2g. Kadro ihtiyacı tahmini (3 dakika)

Üst menüden **Kadro İhtiyacı** (yalnız yönetici). Varsayılanlar: başlangıç bugün, 4 hafta, devamsızlık %13. **Hesapla** (15-20 sn; ekranda "Hesaplanıyor…" ve İptal düğmesi). Atamalardan bağımsız, "temiz sayfa" üzerinde hesaplar; veriyi değiştirmez.

| # | Yapın | Dikkat çekin |
|---|-------|--------------|
| a | **Hesapla**, sonucu bekleyin. | Üstte başlık cümlesi: "Mevcut 66 makinistle (%13 devamsızlık varsayımıyla) bu tarifenin %95,2'si dolduruluyor (134 boş / 2800)". Altında hedef tablosu: **%98 için +4, %99 için +16, %100 ulaşılamadı** (sayılar demo yüklenen güne göre birkaç birim oynar; haftalık 45 saat sınırı kapalı olduğundan kişi başı kapasite 57,4 saat sayılır) ve doluluk eğrisi ("eklenen kişi başına kapanan sefer" hızla düşer: son yüzdeler pahalı). Aynı girdi aynı sonucu verir. |
| b | Aşağı inin: dağılım, bröve kümeleri, garlar, talep. | "Eklenecek makinist" hangi gar / araç tipi / hatta gerektiği ayrı tabloda; küme kullanım yüzdeleri gerçek plandan gelir (%59-84). Boş sefer nedenleri en altta. |
| c | İsteğe bağlı: **Yeni sefer senaryosu** → satır ekle (Ankara→Sivas, Elektrikli Dizi, 06:00 ve 14:00, 330 dk; dönüş 10:00 ve 18:00) → **Hesapla**. | "Mevcut kadro yetmez: aynı 66 kişiyle boş 107 → 135" gibi toplam fark ve gerekçe; "ek talep 3,2 kişi-eşdeğeri". |
| d | **Excel indir**. | 8 sayfa: özet, talep, garlar, bröve kümeleri, varsayımlar. |

**Söylenecek önemli not:** Başlık artık "(%13 devamsızlık varsayımıyla)" der ve altında tek cümleyle açıklar: kadro tahmini temiz sayfadan, devamsızlıklı bir plandır; masadaki gerçek plan mevcut atamalarla ve demo verideki yalnız 15 izinle yapıldığı için daha dolu (%99+) görünür. Çelişki değil, varsayım farkı: "Devamsızlık (%)" kutusunu 0 yaparsanız fark kapanır. "Ek hesap süresi (sn)" yalnız ek hesapların (iyileştirmeli plan, konum kuralı kapalı plan) bütçesidir; başlıktaki eğri ondan etkilenmez. Aynı anda ikinci hesap "Başka bir kadro hesabı sürüyor" (429) der.

## 2h. İlk kurulum, kurum adı/logosu ve Excel'den personel yükleme (4 dakika)

Boş kurulumda (demo yüklemeden) yönetici olarak girin. Ürün hiçbir kurumun markasını taşımaz: kurum adı ve logo **müşterinin kendi verisidir** ve yalnız yönetici girer. Girilmemişken her yerde ürün adı ("Makinist Atama") görünür.

| # | Yapın | Dikkat çekin |
|---|-------|--------------|
| a | **Ayarlar → Kurulum** (ya da Ana Sayfa'daki "Kurulumu tamamlayın" kartı → **Kuruluma devam et**). | Beş adım: kurum adı ve logo, tanımlar (en az 2 gar, 1 hat, 1 araç tipi), personel listesi, kural profili, ilk seferler. Durum **veriden hesaplanır**: sayfayı yenileyince kaldığı yerden devam eder; yapılan adım "Tamam", yapılmayan "Bekliyor" yazar. "Hatırlatmayı gizle" yalnız Ana Sayfa kartını kapatır. |
| b | Birinci adımda kurum adı yazıp **Adı kaydet** (ör. "Örnek İşletme (demo)"), isterseniz küçük bir PNG/JPEG logo seçin. | Ad üst çubukta, giriş sayfasında, personel ekranında ve yazdırılan listelerin başlığında görünür. Logo yalnız PNG/JPEG, en fazla 256 KB ve 1024 × 1024 piksel; SVG/GIF/WebP kabul edilmez. Kendi kurumunuzun dışında bir logo yüklemeyin. |
| c | **Tanımlar**'da garları, hat ve araç tiplerini ekleyin, **Kurulum**'a dönün. | Tanım ürettirmeyiz: Excel'deki ad Tanımlar'da yoksa satır hata olur ve eksik adlar ayrıca listelenir. |
| d | Üçüncü adım: **Personeli Excel'den yükle** → **Şablonu indir**, doldurun (ya da şablonu inceleyin), dosyayı seçin. | Şablon üç sayfa: Makinistler, Araç Tipi Bröveleri, Hat Bröveleri. Dosya seçilince **önizleme** gelir; **Onayla ve yükle** denmeden hiçbir kayıt yazılmaz. Hatalı satırlar sayfa + satır no + sicil + neden ile listelenir ve atlanır; geçerli satırlar yüklenir. Şablondaki "ORNEK-" sicilli satırlar silinmeden yüklenemez. |
| e | Aynı dosyayı **ikinci kez** seçin. | "Eklenecek: 0 · Atlanacak (zaten var): N": sicili sistemde olan personel **güncellenmez**, satırında "Atlandı: zaten var" yazar. Belge bitiş tarihi geçmişte olan satırda "belge süresi dolmuş" uyarısı çıkar (atama zaten engellenir). |
| f | Dördüncü adım: **Kural profiline git**. | Hazır profiller "örnek başlangıç, kurum mevzuatıyla doğrulanmalı" etiketlidir; yasal uyum iddiası yoktur. Atlamak isterseniz varsayılan kurallarla devam edip hatırlatmayı gizleyebilirsiniz. |

Personel yükleme yalnız **yönetici** içindir; atamacı personeli tek tek ekleyebilir. Yükleme **Denetim Raporu**'nda "Makinist Excel yüklemesi" ve her personel için "Makinist eklendi" olarak görünür.

## 2i. Sınıra yakın bilgisi ve yorgunluk göstergesi (2 dakika)

Atama Masası'nda boş bir sefere tıklayıp sağdaki aday paneline bakın. **Yalnız bilgidir**: atamayı engellemez, uyarı ya da kırmızı üretmez, sıralamayı değiştirmez (Ayarlar'daki "Sınıra yakınlık ve yorgunluk (bilgi)" grubundan kapatılır, eşikler oradadır).

| # | Yapın | Dikkat çekin |
|---|-------|--------------|
| a | Aday satırına bakın. | Durum rozetinin yanında kısa **"Sınıra yakın"** rozeti (birden fazlaysa "Sınıra yakın (2)") ve tek satırlık yorgunluk özeti ("7 g: 38 s · 2 gece · en kısa dinlenme 11 s"). Ölçüm metinleri satırı uzatmaz. |
| b | Aday satırına ok tuşlarıyla ya da tıklayarak gelin. | Yalnız **odaktaki** adayda ölçüm metinleri ve eşikler açılır ("Günlük süre: 10 s 20 dk / 11 s"). Ekran okuyucu rozetin adında tüm ölçümleri okur. |
| c | Masada aynı kişinin satırına bakın. | Satırda görünür "Sınıra yakın" rozeti; klavyeyle odaklanınca ölçüm metni altında açılır. |

Not: yorgunluk "bilimsel bir model" değildir, son 7 günün üç ham değeridir; makinistin kendi ekranında görünmez.

## 2j. Aylık çizelge, resmi tatil ve ilan baskısı (3 dakika)

Üst menüden **Aylık Çizelge** (Raporlar). Salt okunur ızgara: satırlarda personel, sütunlarda ayın günleri; hücrede kısa görev kodu, izin "İ/R/D" harfleri, gece görevi ay simgesi + metin. Sağdaki toplamlar **Adalet Raporu ile aynı** sayıdır.

| # | Yapın | Dikkat çekin |
|---|-------|--------------|
| a | Çizelgeyi açın, bir hücrede **Enter**'a basın. | Hücre Atama Masası'nda o gün ve o sefer seçili olarak açılır. Klavyeyle ok tuşları hücreler arasında gezer; sayfa 50 personelde bir sayfalanır. |
| b | **Tanımlar → Resmi tatiller**'den bir tarih ekleyin (ör. 29 Ekim). | Tatil yalnız gösterimdir: çizelge başlığında "Tatil" yazar, hiçbir hesabı (adalet, kırmızı, gün listesi) etkilemez. Kayıt Denetim Raporu'nda "Resmi tatil eklendi" olarak görünür. |
| c | Çizelgede **Yazdır (A4)** ya da **A3** bağlantısı. | Menüsüz, yatay, başlıkta kurum adı; satır bölünmez, başlık her sayfada yinelenir. Panoya asılacak ilan için A3. Gerçek yazıcıda denemeden önce önizlemeye bakın. |

## 2k. Yönetim özeti ve denetim paketi (3 dakika, yalnız yönetici)

Üst menüden **Yönetim Özeti** (Raporlar; atamacı ve makinist menüde görmez, adrese yazsa da sunucu reddeder). Ay seçilir (takvim ayı), sayfa kendi kendine gelir.

| # | Yapın | Dikkat çekin |
|---|-------|--------------|
| a | Özeti açın. | Sekiz kutu: doluluk, kural dışı (kırmızı) atama, uyarılı atama ve gerekçeleri, adalet dağılımı (ortalama, standart sapma, en az, en çok), fazla mesai, belge uyumu (**"Bugünkü durum"**: dönem bilgisi değil), değişiklik kayıtları ve nedenleri, yedek açığı. Dağılım çubukları süstür, sayı her zaman yanında yazar. Demo veride üstte "DEMO – gerçek değil" etiketi çıkar. |
| b | Bir sayıyı ilgili raporla karşılaştırın (ör. Adalet Raporu'nun ortalaması, Atama Masası'ndaki kırmızı sayısı, Denetim Raporu'nun kayıt sayısı). | **Yeni formül yok**: özetteki her sayı mevcut raporun aynı sayısıdır. Standart sapma popülasyon sapmasıdır (n'e bölünür), formülü kutuda yazılıdır. |
| c | **Yazdırılabilir sayfa**. | Menüsüz A4 yatay, siyah-beyaz; başlıkta kurum adı, altbilgide son güncelleme, yazdıran ve kayıt sayısı. |
| d | **Denetim paketi (Excel)**. | Yedi sayfa: Özet, Değişiklik kayıtları (Denetim Raporu'yla aynı satırlar: atama, parametre, tatil, kurum ve profil değişiklikleri dahil), Uyarılı atamalar, Ayar değişiklikleri, Kural değerleri (o anki parametre, şiddet, ayar ve özel kurallar; kaynak ve güvenilirlikle), Adalet, Rapor bilgisi. Hücreler formül çalıştırmaz; demo veride "DEMO – gerçek değil" satırı vardır. Denetçiye "kim, ne zaman, hangi gerekçeyle, hangi kuralla" cevabı tek dosyada verir. |

Önerilen sıra (kısa senaryoda 10. adımın yerine): Otomatik ata → bir uyarılı atama (gerekçeli) → **Yönetim Özeti** → **Denetim paketi (Excel)**.

## 3. Takılırsanız

| Gördüğünüz | Ne yapın |
|------------|----------|
| "Oturumunuz sona erdi" | Sunucu yeniden başladı. Yeniden giriş yapın, kaldığınız sayfaya döner. |
| Ana Sayfada "Demo veriyi yükle" düğmesi yok / "Demo veri yüklü" yazıyor | Veri zaten var. "Demo veriyi sıfırla" → "Evet, sıfırla" → "Demo veriyi yükle". |
| Önizleme/Uygula 5-6 saniye bekletiyor, düğmeler soluk | Normal, hesaplama sürüyor. "700 sefer için tüm kuralları tek tek deniyor" diyebilirsiniz. |
| Masada bugünün sabah seferleri BOŞ görünüyor | Atanmamış geçmiş seferler masada BOŞ kalır; Otomatik ata bunları saymaz ve atamaz. Günü yarın seçerek temiz bir liste gösterebilirsiniz. |
| Gecikme girince "Varış, kalkıştan sonra olmalı…" | Varış kutusunu boş bırakın ya da kalkıştan büyük bir değer yazın. |
| İzinler sayfasında kendi açtığınız talebi onaylayamıyorsunuz | Tasarım gereği: kimse kendi talebini onaylayamaz ("başka bir yetkili onaylamalı"). Gösterimde izin onayı yapmayın; bunu güvenlik özelliği olarak anlatabilirsiniz. |
| Takas iste: "Şu an takas edilebilecek görev yok." | Ya karşı tarafın (hesaplı, aktif) görevi yok, ya da kalkışa 12 saatten az kaldı; demo veride yalnız D001–D003 hesaplıdır. Daha uzak bir gün seçin ya da makinist kartından hesap açın. |
| Takas onayında "ENGEL varken onaylanamaz" | Beklenen: iki makinistten biri yeni görevi üstlenirse kural bozulur. Reddedin ya da başka bir takas deneyin. |
| Yazdır'a basınca pencere açılmıyor | Tarayıcının "Yazdır" komutunu (Ctrl+P) kullanın. |
| Takas iste: tüm hedefler "Bu takas kurallara uymuyor" | Yoğun plan; başka bir görevi deneyin ya da demoyu sıfırlayıp yalnız 7 gün atayın. |
| Kadro İhtiyacı "Süre sınırı nedeniyle bazı EK hesaplar yapılmadı" | Beklenen (mavi bilgi bandı): eğri ve hedefler etkilenmez; yalnız iyileştirmeli / konum kuralı kapalı ek satırlar atlanmış. "Ek hesap süresi"ni 30 sn yapıp yeniden hesaplayabilirsiniz. |
| Kadro İhtiyacı "Başka bir kadro hesabı sürüyor" | Aynı anda tek hesap çalışır; birkaç saniye bekleyip yeniden deneyin. |
| Makinist ekranından Çıkış'ta "Oturumunuz sona erdi" yazıyor | Seyrek görülür (arka plandaki yenileme isteğiyle yarışıyor); yeniden girin, veri etkilenmez. |

## 4. Gösterimde söylememeniz gerekenler

- "Mevzuata uygundur" demeyin: ekran kendisi "karar destek aracıdır, mevzuata uyum garantisi vermez" diyor (Ayarlar sayfasında).
- Puantaj ve tutarlar demo oranlarıyladır, gerçek ücret değildir.
- Yönetim özeti ve denetim paketi mevcut raporların sayılarıdır; yeni bir "başarı puanı" ya da uyum skoru yoktur, söylemeyin. Belge uyumu bugünkü durumdur, seçilen ayın değil.
- Bu sürümde Excel'den sefer yükleme var (Seferler sayfası), ama hazır bir kurum formatı yok; kendi Excel'iniz için sütunlar şablonda. Personel listesi için de şablon vardır (Adım 2h).
