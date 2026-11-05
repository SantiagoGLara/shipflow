import { useState } from 'react'
import { authApi } from '../services/api'
import type { AuthUser } from '../types'

interface Props {
  onAuthenticated: (user: AuthUser) => void
}

export default function AuthScreen({ onAuthenticated }: Props) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      const result = mode === 'login'
        ? await authApi.login(email, password)
        : await authApi.register(name, email, password)
      onAuthenticated(result.user)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo autenticar')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="auth-screen">
      <form className="auth-card" onSubmit={submit}>
        <div className="auth-brand"><span className="brand-icon">S</span> ShipFlow</div>
        <span className="eyebrow">WORKSPACE</span>
        <h1>{mode === 'login' ? 'Bienvenido de nuevo' : 'Crear cuenta'}</h1>
        <p>{mode === 'login' ? 'Inicia sesión para acceder a tus proyectos.' : 'Regístrate para comenzar a gestionar tus entregas.'}</p>
        {error && <div className="error-banner">{error}</div>}
        {mode === 'register' && (
          <>
            <label htmlFor="auth-name">Nombre</label>
            <input id="auth-name" value={name} onChange={e => setName(e.target.value)} required maxLength={120} autoComplete="name" />
          </>
        )}
        <label htmlFor="auth-email">Correo electrónico</label>
        <input id="auth-email" type="email" value={email} onChange={e => setEmail(e.target.value)} required maxLength={180} autoComplete="email" />
        <label htmlFor="auth-password">Contraseña</label>
        <input id="auth-password" type="password" value={password} onChange={e => setPassword(e.target.value)} required minLength={8} maxLength={72} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} />
        <button className="primary-button auth-submit" disabled={busy} type="submit">
          {busy ? 'Procesando...' : mode === 'login' ? 'Iniciar sesión' : 'Crear cuenta'}
        </button>
        <button className="auth-switch" type="button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }}>
          {mode === 'login' ? '¿No tienes cuenta? Regístrate' : '¿Ya tienes cuenta? Inicia sesión'}
        </button>
      </form>
    </main>
  )
}
