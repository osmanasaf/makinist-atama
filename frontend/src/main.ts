import { createApp } from 'vue'
import App from './App.vue'
import router, { oturumDustu } from './router'
import { yetkisizIsleyiciKaydet } from '@/api/istemci'
import { kurumYukle } from '@/composables/kurum'
import { oturumuTemizle } from '@/composables/oturum'
// IBM Plex uygulamayla birlikte paketlenir (kurum ağı dış yazı tipi sunucusuna erişemeyebilir)
import '@fontsource/ibm-plex-sans/400.css'
import '@fontsource/ibm-plex-sans/600.css'
import '@fontsource/ibm-plex-sans/700.css'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/600.css'
import '@fontsource/ibm-plex-mono/700.css'
import './styles/belirtecler.css'
import './styles/temel.css'
import './styles/yazdir.css'

// Oturum düştüğünde (herhangi bir istekte 401) giriş sayfasına dön, dönüş adresini sakla.
yetkisizIsleyiciKaydet(() => {
  oturumuTemizle()
  void oturumDustu(router)
})

// Kurum adı + logosu (anonim uç; hata ekranı bozmaz): giriş sayfası da kullanır, bu yüzden oturumdan bağımsız ve bir kez yüklenir.
void kurumYukle()

createApp(App).use(router).mount('#app')
