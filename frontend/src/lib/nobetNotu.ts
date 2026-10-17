/** Kural ihlali metinlerinde geçen "nöbet" kavramının tek cümlelik açıklaması (parametre `nobet.ayirma_saat`, varsayılan 4). */
export const NOBET_NOTU =
  'Nöbet: aralarındaki boşluk 4 saatten az olan görevler tek çalışma bloğu sayılır; süre ve gece kuralları blok toplamı üzerinden hesaplanır.'

/** Metinlerden biri "nöbet" içeriyorsa (büyük/küçük harf fark etmez) açıklama gösterilmelidir. */
export const nobetGeciyor = (metinler: ReadonlyArray<string | null | undefined>): boolean =>
  metinler.some((m) => !!m && /nöbet/i.test(m))
