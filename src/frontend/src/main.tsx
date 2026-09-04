import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

function App() {
  return <main><h1>Plataforma SAST</h1><p>Fundação da CP1 pronta para análise estática.</p></main>
}

createRoot(document.getElementById('root')!).render(
  <StrictMode><App /></StrictMode>,
)
