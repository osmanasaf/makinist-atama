import { describe, expect, it } from 'vitest'
import { trenAdi } from './trenAdi'

describe('trenAdi', () => {
  it('parçalıda tren no + parça etiketi', () => {
    expect(trenAdi({ trenNo: 'DE-1503', parcaNo: 1, parcaSayisi: 2 })).toBe('DE-1503 · 1/2')
    expect(trenAdi({ trenNo: 'DE-1503', parcaNo: 2, parcaSayisi: 2 })).toBe('DE-1503 · 2/2')
  })
  it('parçasızda yalnız tren no (null, undefined, alan yok)', () => {
    expect(trenAdi({ trenNo: 'T1', parcaNo: null, parcaSayisi: null })).toBe('T1')
    expect(trenAdi({ trenNo: 'T1' })).toBe('T1')
    expect(trenAdi({ trenNo: 'T1', parcaNo: undefined, parcaSayisi: undefined })).toBe('T1')
  })
})
