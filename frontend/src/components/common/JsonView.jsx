// Pretty, scrollable JSON block for rendering loosely-typed API responses.
export default function JsonView({ data, maxHeight = 320 }) {
  if (data == null) return null
  return (
    <pre
      className="mb-0 p-3 rounded"
      style={{
        maxHeight, overflow: 'auto', fontSize: 12.5, lineHeight: 1.5,
        background: 'var(--app-bg)', border: '1px solid var(--border-soft)',
      }}
    >
      {typeof data === 'string' ? data : JSON.stringify(data, null, 2)}
    </pre>
  )
}
