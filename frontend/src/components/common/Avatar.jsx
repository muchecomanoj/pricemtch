// The signed-in user's picture, falling back to their initial.
//
// One component so the navbar, sidebar and anywhere else stay in step — a
// profile photo uploaded in Settings should appear everywhere, not just where
// someone remembered to check for it.
export default function Avatar({ user, size = 36, className = '' }) {
  const source = user?.name || user?.firstName || ''
  const initial = source.trim()[0]?.toUpperCase() || 'U'

  if (user?.profileImage) {
    return (
      <img
        src={user.profileImage}
        alt={source ? `${source}'s profile picture` : 'Profile picture'}
        width={size}
        height={size}
        className={`rounded-circle flex-shrink-0 ${className}`}
        style={{ objectFit: 'cover' }}
      />
    )
  }

  return (
    <div
      className={`d-grid rounded-circle text-white fw-semibold flex-shrink-0 ${className}`}
      style={{
        width: size,
        height: size,
        placeItems: 'center',
        background: 'var(--grad-primary)',
        fontSize: size * 0.4,
      }}
      aria-hidden="true"
    >
      {initial}
    </div>
  )
}
