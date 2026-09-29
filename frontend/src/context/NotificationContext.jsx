import { createContext, useContext } from 'react'
import { toast } from 'react-toastify'
import Swal from 'sweetalert2'

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
