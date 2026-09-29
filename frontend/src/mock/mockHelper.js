// Simulates a network round-trip so mock services behave like real async REST calls.
export const delay = (ms = 500) => new Promise((res) => setTimeout(res, ms))

export async function mockResponse(data, ms = 500) {
  await delay(ms)
  // Shape mirrors a typical Spring Boot ResponseEntity body.
  return { data: structuredClone(data) }
}

// Simple client-side pagination/filter used by list mocks.
export function paginate(items, { page = 1, size = 10, search = '' } = {}) {
  let filtered = items
  if (search) {
    const q = search.toLowerCase()
    filtered = items.filter((i) =>
      JSON.stringify(i).toLowerCase().includes(q)
    )
  }
  const total = filtered.length
  const start = (page - 1) * size
  return {
    content: filtered.slice(start, start + size),
    totalElements: total,
    totalPages: Math.max(1, Math.ceil(total / size)),
    page,
    size,
  }
}
