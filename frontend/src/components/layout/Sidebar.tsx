import { useState } from 'react'
import {
  LayoutDashboard,
  Activity,
  AlertTriangle,
  CalendarClock,
  Factory,
  Building2,
  Zap,
  Cable,
  GitBranch,
  Router,
  Users,
  UsersRound,
  FileBarChart,
  Settings,
  ChevronDown,
  ChevronRight,
} from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'

type NavItem = {
  label: string
  path: string
  icon: React.ElementType
}

type NavGroup = {
  label: string
  items: NavItem[]
}

const navGroups: NavGroup[] = [
  {
    label: 'Visão Geral',
    items: [
      { label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
      { label: 'Monitoramento', path: '/monitoramento', icon: Activity },
      { label: 'Alertas', path: '/alertas', icon: AlertTriangle },
      { label: 'Eventos', path: '/eventos', icon: CalendarClock },
    ],
  },
  {
    label: 'Infraestrutura',
    items: [
      { label: 'Usinas', path: '/usinas', icon: Factory },
      { label: 'Subestações', path: '/subestacoes', icon: Building2 },
      { label: 'Transformadores', path: '/transformadores', icon: Zap },
      { label: 'Linhas', path: '/linhas', icon: Cable },
      { label: 'Alimentadores', path: '/alimentadores', icon: GitBranch },
      { label: 'Smartmeters', path: '/smartmeters', icon: Router },
    ],
  },
  {
    label: 'Gestão',
    items: [
      { label: 'Consumidores', path: '/consumidores', icon: Users },
      { label: 'Equipes', path: '/equipes', icon: UsersRound },
      { label: 'Relatórios', path: '/relatorios', icon: FileBarChart },
    ],
  },
  {
    label: 'Sistema',
    items: [
      { label: 'Configurações', path: '/configuracoes', icon: Settings },
    ],
  },
]

function Sidebar() {
  const location = useLocation()
  const [openGroups, setOpenGroups] = useState<string[]>(
    navGroups.map((g) => g.label) // todos abertos por padrão
  )

  function toggleGroup(label: string) {
    setOpenGroups((prev) =>
      prev.includes(label) ? prev.filter((l) => l !== label) : [...prev, label]
    )
  }

  return (
    <aside className="flex h-screen w-64 flex-col border-r border-slate-800 bg-slate-900">
      <div className="flex h-16 items-center gap-2 border-b border-slate-800 px-6">
        <Zap className="h-6 w-6 text-blue-400" />
        <span className="text-lg font-bold text-white">GridControl</span>
      </div>

      <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
        {navGroups.map((group) => {
          const isOpen = openGroups.includes(group.label)

          return (
            <div key={group.label} className="mb-2">
              <button
                onClick={() => toggleGroup(group.label)}
                className="flex w-full items-center justify-between px-3 py-2 text-xs font-semibold uppercase tracking-wider text-slate-500 transition hover:text-slate-300"
              >
                {group.label}
                {isOpen ? (
                  <ChevronDown className="h-3.5 w-3.5" />
                ) : (
                  <ChevronRight className="h-3.5 w-3.5" />
                )}
              </button>

              {isOpen && (
                <div className="mt-1 space-y-0.5">
                  {group.items.map((item) => {
                    const Icon = item.icon
                    const active = location.pathname === item.path

                    return (
                      <Link
                        key={item.path}
                        to={item.path}
                        className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition ${
                          active
                            ? 'bg-blue-500/10 text-blue-400'
                            : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
                        }`}
                      >
                        <Icon className="h-4 w-4 shrink-0" />
                        {item.label}
                      </Link>
                    )
                  })}
                </div>
              )}
            </div>
          )
        })}
      </nav>
    </aside>
  )
}

export default Sidebar