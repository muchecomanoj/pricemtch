import { APP_NAME } from '../../constants'

export default function Footer() {
  return (
    <footer className="px-4 py-3 text-muted small border-top d-flex justify-content-between flex-wrap gap-2">
      <span>© {new Date().getFullYear()} {APP_NAME}. All rights reserved.</span>
      <span>v1.0.0</span>
    </footer>
  )
}
