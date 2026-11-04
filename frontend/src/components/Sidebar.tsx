interface SidebarProps {
  activeView: string
  onChangeView: (view: string) => void
  userName: string
  userEmail: string
  onLogout: () => void
}

export default function Sidebar({
  activeView,
  onChangeView,
  userName,
  userEmail,
  onLogout,
}: SidebarProps) {
  const initials = userName.split(' ').filter(Boolean).slice(0, 2).map(part => part[0]).join('').toUpperCase()

  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="brand-icon">S</div>
        <span>ShipFlow</span>
      </div>
      <div className="sidebar-label">WORKSPACE</div>
      <nav>
        <button className={activeView === 'dashboard' ? 'nav-item active' : 'nav-item'} onClick={() => onChangeView('dashboard')}>
          <span>▦</span> Dashboard
        </button>
        <button className={activeView === 'projects' ? 'nav-item active' : 'nav-item'} onClick={() => onChangeView('projects')}>
          <span>◫</span> Proyectos
        </button>
      </nav>
      <div className="sidebar-bottom">
        <div className="user-avatar">{initials || 'U'}</div>
        <div className="sidebar-user-info">
          <strong>{userName}</strong>
          <small>{userEmail}</small>
          <button className="logout-button" onClick={onLogout}>Cerrar sesión</button>
        </div>
      </div>
    </aside>
  )
}
