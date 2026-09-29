// Client-side store for master-data entities that have no CRUD endpoints yet.
//
// Records live in localStorage so they survive navigation and reloads and the
// pages behave like the real thing. The four methods below deliberately mirror
// a REST service (list / create / update / remove, all async), so wiring the
// backend later means replacing the bodies with api.get/post/put/delete —
// no page changes.

const key = (name) => `pi.masterdata.${name}`

function read(name) {
  try {
    const raw = localStorage.getItem(key(name))
    return raw ? JSON.parse(raw) : null
  } catch {
    return null   // corrupt entry — fall back to seeding
  }
}

function write(name, rows) {
  try { localStorage.setItem(key(name), JSON.stringify(rows)) } catch { /* quota — keep in memory */ }
  return rows
}

const nextId = (rows) => (rows.length ? Math.max(...rows.map((r) => r.id || 0)) + 1 : 1)

export function createLocalStore(name) {
  return {
    // `seed` is used only on first use, so a user's edits are never overwritten
    // by data derived from elsewhere (e.g. categories read off the catalog).
    //
    // An EMPTY seed is never persisted: consumers that only read the list (the
    // product form's suggestions) must not lock in an empty store before the
    // page that owns the seed has run.
    async list(seed) {
      const existing = read(name)
      if (existing) return existing
      if (!seed?.length) return []
      return write(name, seed.map((r, i) => ({ id: i + 1, ...r })))
    },
    async create(row) {
      const rows = read(name) || []
      const created = { ...row, id: nextId(rows) }
      write(name, [...rows, created])
      return created
    },
    async update(id, row) {
      const rows = read(name) || []
      const updated = rows.map((r) => (r.id === id ? { ...r, ...row, id } : r))
      write(name, updated)
      return updated.find((r) => r.id === id)
    },
    async remove(id) {
      const rows = read(name) || []
      write(name, rows.filter((r) => r.id !== id))
      return { ok: true }
    },
    // Drops local edits and re-seeds on the next list().
    async reset() {
      try { localStorage.removeItem(key(name)) } catch { /* nothing to clear */ }
      return { ok: true }
    },
  }
}

// Only the entities the FRD treats as tenant reference data. `currency` lives on
// the tenant (§11) and is edited on Company Profile, so it has no store here.
export const categoryStore = createLocalStore('categories')
export const brandStore = createLocalStore('brands')
export const costTypeStore = createLocalStore('costTypes')
