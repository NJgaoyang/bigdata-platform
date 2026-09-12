export interface SideNavItem { label: string; path: string }
export interface ProductNavItem { key: string; label: string; path: string; side?: SideNavItem[] }

export const productNavigation: ProductNavItem[] = [
  { key: 'workbench', label: '工作台', path: '/' },
  { key: 'integration', label: '数据集成', path: '/integration/overview', side: [
    { label: '集成概览', path: '/integration/overview' },
    { label: '数据源管理', path: '/integration/datasources' },
    { label: '离线同步', path: '/integration/batch' },
    { label: '实时同步', path: '/integration/realtime' },
    { label: '运行实例', path: '/integration/instances' }
  ]},
  { key: 'development', label: '数据开发', path: '/development/workspace', side: [
    { label: '开发工作台', path: '/development/workspace' },
    { label: '历史版本', path: '/development/versions' }
  ]},
  { key: 'workflow', label: '工作流', path: '/workflow/definitions', side: [
    { label: '工作流定义', path: '/workflow/definitions' }
  ]},
  { key: 'operations', label: '运维中心', path: '/operations/overview', side: [
    { label: '运维总览', path: '/operations/overview' },
    { label: '运行实例', path: '/operations/instances' },
    { label: '失败任务', path: '/operations/failures' },
    { label: '告警中心', path: '/operations/alerts' }
  ]},
  { key: 'metadata', label: '元数据', path: '/metadata/catalog', side: [
    { label: '元数据目录', path: '/metadata/catalog' }
  ]},
  { key: 'metrics', label: '指标中心', path: '/metrics/overview', side: [
    { label: '指标总览', path: '/metrics/overview' },
    { label: '指标管理', path: '/metrics/manage' },
    { label: '维度管理', path: '/metrics/dimensions' },
    { label: '指标血缘', path: '/metrics/lineage' }
  ]},
  { key: 'assets', label: '数据资产', path: '/assets/catalog', side: [
    { label: '资产目录', path: '/assets/catalog' },
    { label: '我的收藏', path: '/assets/favorites' }
  ]}
]

export const releaseNavigation: SideNavItem[] = [
  { label: '发布记录', path: '/release/history' },
  { label: '审批队列', path: '/release/queue' },
  { label: '发布策略', path: '/release/policy' }
]

export const systemNavigation: SideNavItem[] = [
  { label: '用户管理', path: '/system/users' },
  { label: '角色权限', path: '/system/roles' },
  { label: '运行环境', path: '/system/environments' },
  { label: '审计日志', path: '/system/audit' }
]
