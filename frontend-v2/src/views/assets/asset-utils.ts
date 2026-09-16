import type { AssetItem } from '../../api/domain'

export interface TableAssetRef {
  dataSourceId: number
  database: string
  table: string
}

export function parseTableAssetRef(ref: string): TableAssetRef | undefined {
  if (!ref.startsWith('TABLE:')) return undefined
  const payload = ref.slice('TABLE:'.length)
  const colon = payload.indexOf(':')
  if (colon <= 0) return undefined
  const dataSourceId = Number(payload.slice(0, colon))
  const qualified = payload.slice(colon + 1)
  const dot = qualified.lastIndexOf('.')
  if (!Number.isFinite(dataSourceId) || dot <= 0 || dot === qualified.length - 1) return undefined
  return { dataSourceId, database: qualified.slice(0, dot), table: qualified.slice(dot + 1) }
}

export function assetTypeLabel(type: string) {
  if (type === 'TABLE') return '数据表'
  if (type === 'DATASET') return '数据集'
  if (type === 'METRIC') return '认证指标'
  return type || '资产'
}

export function assetTypeClass(type: string) {
  if (type === 'TABLE') return 'table'
  if (type === 'DATASET') return 'dataset'
  if (type === 'METRIC') return 'metric'
  return 'other'
}

export function assetSearchText(asset: AssetItem) {
  return `${asset.name} ${asset.description || ''} ${asset.source || ''} ${asset.detail || ''} ${asset.owner || ''}`.toLowerCase()
}

export function assetDetailPath(asset: AssetItem) {
  return { path: '/assets/detail', query: { ref: asset.ref } }
}
