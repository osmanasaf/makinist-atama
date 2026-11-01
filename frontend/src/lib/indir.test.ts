import { afterEach, describe, expect, it, vi } from 'vitest'
import { blobIndir } from './indir'

afterEach(() => vi.restoreAllMocks())

describe('blobIndir', () => {
  it('geçici bağlantıyla ad verilerek tıklanır ve bağlantı bırakılır', () => {
    const olustur = vi.fn(() => 'blob:x')
    const birak = vi.fn()
    vi.stubGlobal('URL', { createObjectURL: olustur, revokeObjectURL: birak })
    const tikla = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const blob = new Blob(['x'])

    blobIndir(blob, 'dosya.xlsx')

    expect(olustur).toHaveBeenCalledWith(blob)
    expect(tikla).toHaveBeenCalledTimes(1)
    expect(tikla.mock.contexts[0]).toMatchObject({
      download: 'dosya.xlsx',
      href: expect.stringContaining('blob:x'),
    })
    expect(birak).toHaveBeenCalledWith('blob:x')
    vi.unstubAllGlobals()
  })
})
