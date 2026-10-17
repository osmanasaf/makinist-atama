import { describe, expect, it, vi } from 'vitest'

vi.mock('@/api/istemci', async (orijinal) => ({
  ...(await orijinal<typeof import('@/api/istemci')>()),
  api: { post: vi.fn().mockResolvedValue({}), get: vi.fn() },
}))

import { api, UZUN_ISLEM_ZAMAN_ASIMI_MS } from '@/api/istemci'
import { atamaApi } from '@/features/atama/atamaApi'
import { ozelKuralApi, type OzelKuralIstegi } from '@/features/parametre/ozelKuralApi'

// QA BUG-5: sunucuda 30 sn tavanına yaklaşan çağrılar (otomatik atama önizle/uygula, kuralı dene) en az 120 sn beklenir.
describe('uzun süren çağrıların zaman aşımı', () => {
  it('tavan en az 120 sn', () => expect(UZUN_ISLEM_ZAMAN_ASIMI_MS).toBeGreaterThanOrEqual(120_000))

  it('otomatik atama uzun zaman aşımıyla gider', async () => {
    await atamaApi.otomatik({ bas: '2026-10-12', bit: '2026-10-18', uygula: true })
    expect(api.post).toHaveBeenLastCalledWith(
      '/api/atama/otomatik',
      { bas: '2026-10-12', bit: '2026-10-18', uygula: true },
      UZUN_ISLEM_ZAMAN_ASIMI_MS,
    )
  })

  it('kuralı dene uzun zaman aşımıyla gider', async () => {
    const istek = { ad: 'x' } as OzelKuralIstegi
    await ozelKuralApi.dene(istek, 5)
    expect(api.post).toHaveBeenLastCalledWith(
      '/api/ozel-kural/dene?id=5',
      istek,
      UZUN_ISLEM_ZAMAN_ASIMI_MS,
    )
  })
})
