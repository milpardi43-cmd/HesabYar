import { describe, expect, it } from 'vitest'
import { makeLedger, makeTransaction } from '../test/fixtures'
import { createEmptyLedger, loadLedger, parseBackup, parseLedger, saveLedger, STORAGE_KEY } from './storage'

function memoryStorage() {
  const map = new Map<string, string>()
  return {
    map,
    getItem: (key: string) => map.get(key) ?? null,
    setItem: (key: string, value: string) => { map.set(key, value) },
  }
}

describe('ledger persistence and import validation', () => {
  it('round-trips a valid backup', () => {
    const data = makeLedger()
    expect(parseBackup(JSON.stringify(data))).toEqual(data)
  })

  it('seeds a clearly labelled demo ledger on first run and persists it', () => {
    const storage = memoryStorage()
    const loaded = loadLedger(storage)
    expect(loaded.issue).toBeNull()
    expect(loaded.data.isDemo).toBe(true)
    expect(loaded.data.transactions.length).toBeGreaterThan(0)
    expect(storage.map.has(STORAGE_KEY)).toBe(true)
    expect(loadLedger(storage).data).toEqual(loaded.data)
  })

  it('keeps corrupt persisted bytes untouched for recovery', () => {
    const storage = memoryStorage()
    storage.setItem(STORAGE_KEY, '{broken json')
    const loaded = loadLedger(storage)
    expect(loaded.issue).toBe('corrupt')
    expect(loaded.recoveryRaw).toBe('{broken json')
    expect(loaded.data).toEqual(createEmptyLedger())
    expect(storage.map.get(STORAGE_KEY)).toBe('{broken json')
  })

  it('reports unavailable storage without crashing', () => {
    const loaded = loadLedger({
      getItem: () => { throw new Error('blocked') },
      setItem: () => { throw new Error('blocked') },
    })
    expect(loaded.issue).toBe('unavailable')
    expect(loaded.data.isDemo).toBe(true)
  })

  it('rejects invalid money, dates, categories, duplicate ids and versions', () => {
    expect(() => parseLedger({ ...makeLedger(), schemaVersion: 2 })).toThrow()
    expect(() => parseLedger({ ...makeLedger(), currency: 'USD' })).toThrow()
    expect(() => parseLedger(makeLedger({ transactions: [makeTransaction({ amount: -5 })] }))).toThrow()
    expect(() => parseLedger(makeLedger({ transactions: [makeTransaction({ amount: 1.5 })] }))).toThrow()
    expect(() => parseLedger(makeLedger({ transactions: [makeTransaction({ date: '2026-02-30' })] }))).toThrow()
    expect(() => parseLedger(makeLedger({ transactions: [makeTransaction({ categoryId: 'salary' })] }))).toThrow()
    expect(() => parseLedger(makeLedger({ transactions: [makeTransaction(), makeTransaction()] }))).toThrow()
    expect(() => parseLedger(makeLedger({ budgets: { salary: 100 } }))).toThrow()
    expect(() => parseBackup('not json')).toThrow()
  })

  it('saves validated data as JSON', () => {
    const storage = memoryStorage()
    const data = makeLedger()
    saveLedger(data, storage)
    expect(JSON.parse(storage.map.get(STORAGE_KEY)!)).toEqual(data)
  })
})
