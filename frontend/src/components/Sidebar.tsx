interface SidebarProps {
  activeView: string
  onChangeView: (view: string) => void
}

export default function Sidebar({
  activeView,
  onChangeView,
}: SidebarProps) {
  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="brand-icon">S</div>
        <span>ShipFlow</span>
      </div>

      <div className="sidebar-label">WORKSPACE</div>

      <nav>
        <button
          className={activeView === 'dashboard' ? 'nav-item active' : 'nav-item'}
          onClick={() => onChangeView('dashboard')}
        >
          <span>▦</span> Dashboard
        </button>

        <button
          className={activeView === 'projects' ? 'nav-item active' : 'nav-item'}
          onClick={() => onChangeView('projects')}
        >
          <span>◫</span> Proyectos
        </button>
      </nav>

      <div className="sidebar-bottom">
        <div className="user-avatar">SF</div>
        <div>
          <strong>ShipFlow Admin</strong>
          <small>Administrador</small>
        </div>
      </div>
    </aside>
  )
}