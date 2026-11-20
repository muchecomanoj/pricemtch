import { createContext, useContext } from 'react'
import { toast } from 'react-toastify'

// sweetalert2 is fetched the first time a confirm opens, not with the app:
// it is 77 KB that most visits never use. The promise is kept, so it is
// fetched once; a failed fetch (offline) is forgotten so the next try refetches.
let swal
const loadSwal = () => (swal ||= import('sweetalert2').then(
  (m) => m.default,
  (e) => { swal = null; throw e },
))

const NotificationContext = createContext(null)

export function NotificationProvider({ children }) {
  const notify = {
    success: (msg) => toast.success(msg),
    error: (msg) => toast.error(msg),
    info: (msg) => toast.info(msg),
    warn: (msg) => toast.warn(msg),
  }

  // Promise-based confirm dialog for destructive actions.
  const confirm = async ({
    title = 'Are you sure?',
    text = 'This action cannot be undone.',
    confirmText = 'Yes',
  } = {}) => {
    const Swal = await loadSwal()
    const res = await Swal.fire({
      title, text, icon: 'warning',
      showCancelButton: true,
      confirmButtonText: confirmText,
      confirmButtonColor: '#dc2626',
      cancelButtonColor: '#6b7280',
    })
    return res.isConfirmed
  }

  return (
    <NotificationContext.Provider value={{ notify, confirm }}>
      {children}
    </NotificationContext.Provider>
  )
}

export const useNotification = () => useContext(NotificationContext)
