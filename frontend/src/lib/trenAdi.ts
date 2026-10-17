/** Tren adı (F2c): ara istasyonda makinist değişen trenin parçası "DE-1503 · 1/2" gösterilir; parçasızda yalnız tren no (piksel/metin farkı yok). */
export function trenAdi(s: {
  trenNo: string
  parcaNo?: number | null
  parcaSayisi?: number | null
}): string {
  return s.parcaNo != null && s.parcaSayisi != null
    ? `${s.trenNo} · ${s.parcaNo}/${s.parcaSayisi}`
    : s.trenNo
}
