/** Arama için metni sadeleştirir: Türkçe küçük harf (İ→i), ı→i, aksan işaretleri silinir (ç ş ğ ö ü → c s g o u). */
export function normalle(metin: string): string {
  return metin.toLocaleLowerCase('tr-TR').replace(/ı/g, 'i').normalize('NFD').replace(/[̀-ͯ]/g, '')
}

/** Sorgudaki boşlukla ayrılmış TÜM sözcükler metinde geçiyorsa true (boş sorgu her şeyi eşler). */
export function eslesir(metin: string, sorgu: string): boolean {
  const sozcukler = normalle(sorgu).split(/\s+/).filter(Boolean)
  const hedef = normalle(metin)
  return sozcukler.every((s) => hedef.includes(s))
}
