import type { CurrentUser } from '../api/auth'

export const moduleViewPermission: Record<string,string> = {
  workbench:'WORKBENCH_VIEW', integration:'DATA_INTEGRATION_VIEW', development:'DATA_DEVELOPMENT_VIEW',
  workflow:'WORKFLOW_VIEW', operations:'OPERATIONS_VIEW', metadata:'METADATA_VIEW', metrics:'METRICS_VIEW',
  assets:'DATA_ASSETS_VIEW', release:'RELEASE_VIEW', system:'SYSTEM_SETTINGS_VIEW'
}

export const moduleEditPermission: Record<string,string> = {
  workbench:'WORKBENCH_EDIT', integration:'DATA_INTEGRATION_EDIT', development:'DATA_DEVELOPMENT_EDIT',
  workflow:'WORKFLOW_EDIT', operations:'OPERATIONS_EDIT', metadata:'METADATA_EDIT', metrics:'METRICS_EDIT',
  assets:'DATA_ASSETS_EDIT', release:'RELEASE_EDIT', system:'SYSTEM_SETTINGS_EDIT'
}

export function hasPermission(me:CurrentUser|undefined|null, permission?:string) {
  return !!me?.authenticated && (!!me.superAdmin || !permission || me.permissions.includes(permission))
}
