import { describe, expect, it } from 'vitest'
import { ilkHataliyaOdaklan } from './odak'

describe('ilkHataliyaOdaklan', () => {
  it('ilk aria-invalid alanına odaklanır; hata yoksa ya da kök yoksa sessizce biter', async () => {
    document.body.innerHTML =
      '<form><input id="a" aria-invalid="false"><input id="b" aria-invalid="true"><input id="c" aria-invalid="true"></form>'
    await ilkHataliyaOdaklan(document.querySelector('form'))
    expect(document.activeElement?.id).toBe('b')
    ;(document.activeElement as HTMLElement).blur()
    document.body.innerHTML = '<form><input id="a"></form>'
    await ilkHataliyaOdaklan(document.querySelector('form'))
    expect(document.activeElement).toBe(document.body)
    await expect(ilkHataliyaOdaklan(null)).resolves.toBeUndefined()
  })
})
