/** Elde olan blob'u dosya olarak indirtir (geçici bağlantı oluşturulur ve hemen bırakılır). */
export function blobIndir(blob: Blob, ad: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = ad
  a.click()
  URL.revokeObjectURL(url)
}
