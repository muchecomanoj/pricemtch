import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'

// Global styles: SCSS (which imports Bootstrap) + third-party CSS.
import './styles/main.scss'
import 'react-toastify/dist/ReactToastify.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>
)
