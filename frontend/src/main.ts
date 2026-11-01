import { createApp } from 'vue'
import App from './App.vue'
import router, { oturumDustu } from './router'
import { yetkisizIsleyiciKaydet } from '@/api/istemci'
import { kurumYukle } from '@/composables/kurum'
import { oturumuTemizle } from '@/composables/oturum'
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
