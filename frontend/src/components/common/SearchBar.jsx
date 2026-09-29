import { FiSearch, FiX } from 'react-icons/fi'

// The single search input for list pages — Products, Competitor Listings,
// Clients, Master Data. Pages previously hand-rolled their own with an
// absolutely positioned icon, which drifted visually from this one.
//
// Filter DROPDOWNS follow a companion rule:
//   • short fixed list (3–5 options) → native <select>, nothing to search
//   • data-driven or long list        → <ComboInput strict clearable>
export default function SearchBar({
  value, onChange, placeholder = 'Search…', className = '', style, grow = false,
}) {
  return (
    <div
      className={`input-group ${grow ? 'flex-grow-1' : ''} ${className}`}
      style={{ maxWidth: grow ? undefined : 320, minWidth: grow ? 220 : undefined, ...style }}
    >
      <span className="input-group-text bg-transparent"><FiSearch size={15} /></span>
      <input
        className="form-control"
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
      />
      {value && (
        <button type="button" className="btn btn-light" onClick={() => onChange('')} aria-label="Clear search">
          <FiX size={14} />
        </button>
      )}
    </div>
  )
}
