import { api } from '@/api/istemci'

export interface DemoDurumu {
  yuklu: boolean
}

export interface DemoSonucu {
  makinist: number
  sefer: number
  izin: number
  /** Demo makinist hesapları (uydurma): geçici parola yalnız bu yanıtta; ilk girişte değiştirilmelidir. */
  hesaplar?: { sicil: string; adSoyad: string; geciciParola: string }[]
}

/** Yalnız dev profilinde ve yalnız yönetici için vardır; üretimde 404/403 döner (ekran bölümü gizlenir). */
export const demoApi = {
  durum: () => api.get<DemoDurumu>('/api/demo/durum'),
  yukle: () => api.post<DemoSonucu>('/api/demo/yukle'),
  sifirla: () => api.post('/api/demo/sifirla'),
  /** F2b: demo veriye birkaç yolculuk ve bir konaklama örneği ekler; ikinci çağrı 409 DEMO_YOLCULUK_VAR. */
  yolculukOrnekleri: () => api.post<{ yolculuk: number }>('/api/demo/yolculuk-ornekleri'),
  /** F2c: demo veriye ara istasyonda makinist değişen bir tren (iki parça) ekler; ikinci çağrı 409 DEMO_PARCALI_VAR. */
  parcaliOrnek: () => api.post<{ parca: number }>('/api/demo/parcali-ornek'),
  /** İ-2: demo veriye 3 gar çifti intikal süresi ve bir serbest intikal örneği ekler; ikinci çağrı 409 DEMO_INTIKAL_VAR. */
  intikalOrnek: () =>
    api.post<{ sure: number; intikal: number; sicil: string }>('/api/demo/intikal-ornek'),
  /** F3b: demo veriye 4 kondüktör (K001..K004), 3 kondüktör ataması ve "yolcu trenine kondüktör gerekli" ayarı ekler; ikinci çağrı 409 DEMO_KONDUKTOR_VAR. */
  konduktorOrnegi: () =>
    api.post<{
      konduktor: number
      atama: number
      hesap: { sicil: string; adSoyad: string; geciciParola: string }
    }>('/api/demo/konduktor-ornegi'),
}
