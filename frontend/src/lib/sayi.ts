/** Sayıyı Türkçe gösterir: en çok 2 ondalık, ondalık ayracı virgül, gereksiz sıfır ve binlik ayraç yok. */
export function sayiBicimle(n: number): string {
  return String(Number(n.toFixed(2))).replace('.', ',')
}
