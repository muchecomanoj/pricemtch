import { createContext, useContext, useEffect, useState } from 'react'

const ThemeContext = createContext(null)

export function ThemeProvider({ children }) {
  // Dark is the default so the app matches the marketing site the user
  // just came from. The toggle still works and still wins: anyone who
  // has picked a mode keeps it, because the stored value takes priority.
  const [theme, setTheme] = useState(() => localStorage.getItem('theme') || 'dark')

  // Bootstrap 5.3 dark mode is driven by data-bs-theme on <html>.
  useEffect(() => {
    document.documentElement.setAttribute('data-bs-theme', theme)
    localStorage.setItem('theme', theme)
  }, [theme])

  const toggleTheme = () => setTheme((t) => (t === 'light' ? 'dark' : 'light'))

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme, setTheme }}>
      {children}
    </ThemeContext.Provider>
  )
}

export const useTheme = () => useContext(ThemeContext)
