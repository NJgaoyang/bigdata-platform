<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusBadge from '../../components/StatusBadge.vue'
import { assetApi, metadataApi, type AssetItem, type ColumnView, type LineageView, type TablePreview, type TableProfile } from '../../api/domain'
import { dataSourceApi, type DataSourceView } from '../../api/platform'
import { formatDateTime } from '../../utils/display'
import { assetTypeLabel, parseTableAssetRef } from './asset-utils'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detailLoading = ref(false)
const previewLoading = ref(false)
const item = ref<AssetItem>()
const source = ref<DataSourceView>()
const columns = ref<ColumnView[]>([])
const profile = ref<TableProfile>()
const lineage = ref<LineageView[]>([])
const preview = ref<TablePreview>()
const previewError = ref('')
const activeTab = ref('overview')
const previewLimit = ref(20)

const assetRef = computed(() => String(route.query.ref || ''))
const tableRef = computed(() => parseTableAssetRef(assetRef.value))
const isTable = computed(() => item.value?.type === 'TABLE' && Boolean(tableRef.value))
const fieldCommented = computed(() => columns.value.filter(column => column.comment?.trim()).length)
const fieldCommentCoverage = computed(() => columns.value.length ? Math.round(fieldCommented.value / columns.value.length * 100) : 0)
const governanceChecks = computed(() => {
  const checks = [{ label: '资产描述', ok: Boolean(item.value?.description?.trim()), detail: item.value?.description?.trim() ? '已完善' : '缺少业务描述' }]
  if (isTable.value) {
    checks.push({ label: '负责人', ok: Boolean(profile.value?.owner?.trim()), detail: profile.value?.owner?.trim() ? profile.value.owner! : '未设置负责人' })
    checks.push({ label: '字段说明', ok: fieldCommentCoverage.value === 100, detail: `${fieldCommentCoverage.value}% 字段已填写说明` })
  } else {
    checks.push({ label: '负责人', ok: Boolean(item.value?.owner?.trim()), detail: item.value?.owner?.trim() || '未设置负责人' })
  }
  return checks
})
const governanceScore = computed(() => governanceChecks.value.length ? Math.round(governanceChecks.value.filter(check => check.ok).length / governanceChecks.value.length * 100) : 0)
const upstream = computed(() => {
  const name = tableRef.value ? `${tableRef.value.database}.${tableRef.value.table}` : ''
  return [...new Set(lineage.value.filter(edge => normalize(edge.targetTable) === normalize(name)).map(edge => edge.sourceTable))]
})
const downstream = computed(() => {
  const name = tableRef.value ? `${tableRef.value.database}.${tableRef.value.table}` : ''
  return [...new Set(lineage.value.filter(edge => normalize(edge.sourceTable) === normalize(name)).map(edge => edge.targetTable))]
})

function normalize(value?: string) {
  return (value || '').replace(/`/g, '').toLowerCase()
}

function fmtSize(value?: number) {
  if (value == null) return '—'
  if (value >= 1024 ** 3) return `${(value / 1024 ** 3).toFixed(2)} GB`
  if (value >= 1024 ** 2) return `${(value / 1024 ** 2).toFixed(1)} MB`
  if (value >= 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${value} B`
}

async function load() {
  if (!assetRef.value) {
    router.replace('/assets/catalog')
    return
  }
  loading.value = true
  activeTab.value = 'overview'
  preview.value = undefined
  columns.value = []
  profile.value = undefined
  lineage.value = []
  try {
    const catalog = await assetApi.catalog()
    item.value = catalog.find(asset => asset.ref === assetRef.value)
    if (!item.value) {
      ElMessage.warning('该资产不存在或当前账号不可访问')
      router.replace('/assets/catalog')
      return
    }
    if (item.value.type === 'TABLE' && tableRef.value) await loadTableDetail()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '资产详情加载失败')
  } finally {
    loading.value = false
  }
}

async function loadTableDetail() {
  const parsed = tableRef.value
  if (!parsed) return
  detailLoading.value = true
  try {
    const sources = await dataSourceApi.list()
    source.value = sources.find(value => value.id === parsed.dataSourceId)
    const [cols, prof, lin] = await Promise.allSettled([
      metadataApi.columns(parsed.dataSourceId, parsed.database, parsed.table),
      metadataApi.profile(parsed.dataSourceId, parsed.database, parsed.table),
      metadataApi.lineage(`${parsed.database}.${parsed.table}`)
    ])
    columns.value = cols.status === 'fulfilled' ? cols.value : []
    profile.value = prof.status === 'fulfilled' ? prof.value : undefined
    lineage.value = lin.status === 'fulfilled' ? lin.value : []
  } finally {
    detailLoading.value = false
  }
}

async function loadPreview() {
  const parsed = tableRef.value
  if (!parsed) return
  previewLoading.value = true
  previewError.value = ''
  try {
    preview.value = await metadataApi.preview(parsed.dataSourceId, parsed.database, parsed.table, previewLimit.value)
  } catch (e) {
    preview.value = undefined
    previewError.value = e instanceof Error ? e.message : '数据预览失败'
  } finally {
    previewLoading.value = false
  }
}

async function switchTab(name: string) {
  activeTab.value = name
  if (name === 'preview' && isTable.value && !preview.value) await loadPreview()
}

async function toggleFavorite() {
  if (!item.value) return
  try {
    if (item.value.favorite) await assetApi.unfavorite(item.value.ref)
    else await assetApi.favorite(item.value.type, item.value.ref)
    item.value.favorite = !item.value.favorite
    ElMessage.success(item.value.favorite ? '已收藏' : '已取消收藏')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '收藏操作失败')
  }
}

async function editOwner() {
  const parsed = tableRef.value
  if (!parsed || !profile.value?.ownerEditable) return
  try {
    const { value } = await ElMessageBox.prompt('请输入数据表负责人', '设置负责人', {
      inputValue: profile.value.owner || '',
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })
    profile.value = await metadataApi.updateOwner(parsed.dataSourceId, parsed.database, parsed.table, value || '')
    ElMessage.success('负责人已更新')
  } catch {}
}

function openMap() {
  router.push({ path: '/assets/map', query: { ref: assetRef.value } })
}

watch(assetRef, load)
onMounted(load)
</script>

<template>
  <div class="asset-detail" v-loading="loading || detailLoading">
    <header class="detail-head" v-if="item">
      <div class="breadcrumb"><button @click="router.push('/assets/catalog')">资产目录</button><span>/</span><span>{{ assetTypeLabel(item.type) }}</span><span>/</span><span>{{ item.name }}</span></div>
      <div class="title-row">
        <span class="asset-mark">{{ assetTypeLabel(item.type).slice(0,1) }}</span>
        <div class="title-copy"><h1>{{ item.name }}</h1><p>{{ item.description || '暂无资产描述' }}</p><div class="title-meta"><span>{{ assetTypeLabel(item.type) }}</span><span v-if="item.source">来源：{{ item.source }}</span><span v-if="item.detail">{{ item.detail }}</span><span v-if="profile?.owner">负责人：{{ profile.owner }}</span><StatusBadge :status="item.status" /></div></div>
        <div class="head-actions"><el-button @click="toggleFavorite">{{ item.favorite ? '★ 已收藏' : '☆ 收藏' }}</el-button><el-button v-if="isTable" @click="openMap">查看血缘图</el-button><el-button v-if="isTable" type="primary" @click="router.push('/development/workspace')">进入数据开发</el-button></div>
      </div>
    </header>

    <template v-if="item">
      <nav class="detail-tabs">
        <button :class="{active:activeTab==='overview'}" @click="switchTab('overview')">概览</button>
        <button v-if="isTable" :class="{active:activeTab==='fields'}" @click="switchTab('fields')">字段</button>
        <button v-if="isTable" :class="{active:activeTab==='preview'}" @click="switchTab('preview')">数据预览</button>
        <button v-if="isTable" :class="{active:activeTab==='lineage'}" @click="switchTab('lineage')">血缘</button>
        <button :class="{active:activeTab==='quality'}" @click="switchTab('quality')">基础治理</button>
        <button :class="{active:activeTab==='usage'}" @click="switchTab('usage')">使用情况</button>
        <button :class="{active:activeTab==='changes'}" @click="switchTab('changes')">变更信息</button>
      </nav>

      <main class="detail-body">
        <section v-show="activeTab==='overview'" class="overview-grid">
          <div class="ds-card info-card"><div class="card-head">基本信息</div><dl><dt>资产名称</dt><dd>{{ item.name }}</dd><dt>资产类型</dt><dd>{{ assetTypeLabel(item.type) }}</dd><dt>资产状态</dt><dd><StatusBadge :status="item.status" /></dd><dt>来源</dt><dd>{{ item.source || '—' }}</dd><dt>位置 / 编码</dt><dd>{{ item.detail || '—' }}</dd><dt>负责人</dt><dd><template v-if="isTable">{{ profile?.owner || '未设置' }} <el-button v-if="profile?.ownerEditable" link type="primary" @click="editOwner">编辑</el-button></template><template v-else>{{ item.owner || '未设置' }}</template></dd><dt>描述</dt><dd>{{ item.description || '暂无描述' }}</dd></dl></div>
          <div v-if="isTable" class="ds-card info-card"><div class="card-head">物理存储</div><dl><dt>数据源</dt><dd>{{ source?.name || item.source || '—' }}</dd><dt>数据源类型</dt><dd>{{ source?.type || '—' }}</dd><dt>连接地址</dt><dd>{{ source ? `${source.host}:${source.port}` : '—' }}</dd><dt>Database</dt><dd>{{ tableRef?.database || '—' }}</dd><dt>Table</dt><dd>{{ tableRef?.table || '—' }}</dd><dt>字段数</dt><dd>{{ columns.length }}</dd><dt>数据行数</dt><dd>{{ profile?.rowCount?.toLocaleString() || '—' }}</dd><dt>估算大小</dt><dd>{{ fmtSize(profile?.estimatedSizeBytes) }}</dd></dl></div>
          <div class="ds-card info-card"><div class="card-head">资产可用信息</div><dl><dt>基础治理得分</dt><dd><strong :class="governanceScore===100?'ok-text':'warn-text'">{{ governanceScore }}%</strong></dd><template v-if="isTable"><dt>上游表</dt><dd>{{ upstream.length }}</dd><dt>下游表</dt><dd>{{ downstream.length }}</dd><dt>字段说明覆盖</dt><dd>{{ fieldCommentCoverage }}%</dd><dt>最近更新时间</dt><dd>{{ formatDateTime(profile?.updateTime) }}</dd></template><dt>收藏状态</dt><dd>{{ item.favorite ? '已收藏' : '未收藏' }}</dd></dl></div>
          <div class="ds-card description-card"><div class="card-head">资产说明</div><p>{{ item.description || '当前元数据未提供业务描述。建议在数据源表注释或对应资产定义中补充。' }}</p><div class="truth-note">本页面只展示平台当前接口能够真实提供的数据；质量、热度等尚未采集的指标不会生成 Mock 值。</div></div>
        </section>

        <section v-if="isTable" v-show="activeTab==='fields'" class="ds-card table-card">
          <div class="card-toolbar"><strong>字段列表</strong><span>共 {{ columns.length }} 个字段，{{ fieldCommented }} 个有说明</span></div>
          <el-table :data="columns" height="calc(100vh - 260px)"><el-table-column prop="name" label="字段名" min-width="220"/><el-table-column prop="dataType" label="类型" width="180"/><el-table-column prop="nullable" label="可空" width="90"><template #default="scope">{{ scope.row.nullable===false?'否':'是' }}</template></el-table-column><el-table-column prop="comment" label="字段说明" min-width="320" show-overflow-tooltip><template #default="scope">{{ scope.row.comment || '—' }}</template></el-table-column></el-table>
        </section>

        <section v-if="isTable" v-show="activeTab==='preview'" class="ds-card table-card">
          <div class="card-toolbar"><strong>数据预览</strong><span>仅用于抽样理解表数据，请遵循数据访问权限</span><div class="spacer"/><el-select v-model="previewLimit" style="width:110px" @change="loadPreview"><el-option :value="20" label="20 行"/><el-option :value="50" label="50 行"/><el-option :value="100" label="100 行"/></el-select><el-button :loading="previewLoading" @click="loadPreview">刷新</el-button></div>
          <div v-if="previewError" class="error-box">{{ previewError }}</div>
          <div v-else class="preview-wrap" v-loading="previewLoading"><table v-if="preview?.columns.length" class="preview-table"><thead><tr><th v-for="column in preview.columns" :key="column">{{ column }}</th></tr></thead><tbody><tr v-for="(row,rowIndex) in preview.rows" :key="rowIndex"><td v-for="(column,columnIndex) in preview.columns" :key="column">{{ row[columnIndex] ?? 'NULL' }}</td></tr></tbody></table><div v-else-if="!previewLoading" class="empty-panel">暂无预览数据</div></div>
        </section>

        <section v-if="isTable" v-show="activeTab==='lineage'" class="lineage-grid">
          <div class="ds-card lineage-summary"><div class="card-head">血缘概览</div><div class="lineage-nodes"><div><span>上游</span><strong>{{ upstream.length }}</strong></div><div class="current-node"><span>当前表</span><strong>{{ tableRef?.database }}.{{ tableRef?.table }}</strong></div><div><span>下游</span><strong>{{ downstream.length }}</strong></div></div><el-button type="primary" plain @click="openMap">在数据地图中查看</el-button></div>
          <div class="ds-card table-card"><div class="card-toolbar"><strong>血缘关系</strong><span>来自平台 SQL 血缘解析结果</span></div><el-table :data="lineage" height="calc(100vh - 350px)"><el-table-column prop="sourceTable" label="上游" min-width="260"/><el-table-column label="关系" width="80"><template #default>→</template></el-table-column><el-table-column prop="targetTable" label="下游" min-width="260"/><el-table-column prop="relationType" label="类型" width="140"/><template #empty><div class="empty-panel">暂无已解析表级血缘</div></template></el-table></div>
        </section>

        <section v-show="activeTab==='quality'" class="governance-grid">
          <div class="ds-card governance-score"><span>基础治理得分</span><strong :class="governanceScore===100?'ok-text':'warn-text'">{{ governanceScore }}%</strong><p>仅检查资产描述、负责人和字段说明完整度，不等同于业务数据质量。</p></div>
          <div class="ds-card governance-list"><div class="card-head">治理检查项</div><div v-for="check in governanceChecks" :key="check.label" class="check-row"><span :class="['check-dot',{ok:check.ok}]">{{ check.ok ? '✓' : '!' }}</span><div><strong>{{ check.label }}</strong><small>{{ check.detail }}</small></div><span :class="['check-state',{ok:check.ok}]">{{ check.ok ? '正常' : '待完善' }}</span></div></div>
        </section>

        <section v-show="activeTab==='usage'" class="ds-card unsupported-card"><strong>使用情况尚未接入</strong><p>当前 v8 后端没有提供按资产聚合的查询次数、访问用户、Dashboard 引用数等接口，因此这里不展示虚构热度数据。</p><p>后续可基于查询审计、BI 数据集引用和开发任务血缘补充真实使用统计。</p></section>

        <section v-show="activeTab==='changes'" class="ds-card change-card"><div class="card-head">当前可获得的变更信息</div><template v-if="isTable"><div class="change-row"><span>表创建时间</span><strong>{{ formatDateTime(profile?.createTime) }}</strong><small>来自元数据 Profile</small></div><div class="change-row"><span>表更新时间</span><strong>{{ formatDateTime(profile?.updateTime) }}</strong><small>来自元数据 Profile</small></div></template><div v-else class="empty-panel">当前资产接口未提供历史变更记录。</div><div class="truth-note">完整的字段变更历史需要新增元数据快照/审计存储后才能可靠展示。</div></section>
      </main>
    </template>
  </div>
</template>

<style scoped>
.asset-detail{height:calc(100vh - var(--ds-topbar));background:#f5f6f7;display:flex;flex-direction:column;overflow:hidden}.detail-head{flex:0 0 auto;background:#fff;border-bottom:1px solid #e7e9ed;padding:13px 18px 14px}.breadcrumb{display:flex;align-items:center;gap:6px;font-size:10px;color:#98a2b3;margin-bottom:9px}.breadcrumb button{border:0;background:none;padding:0;color:#667085;cursor:pointer}.breadcrumb button:hover{color:#1677ff}.title-row{display:flex;align-items:flex-start;gap:12px}.asset-mark{width:40px;height:40px;border-radius:7px;background:#eaf3ff;color:#1677ff;display:grid;place-items:center;font-weight:700;flex:0 0 40px}.title-copy{min-width:0;flex:1}.title-copy h1{margin:0;color:#252b3a;font-size:19px}.title-copy p{margin:4px 0 8px;color:#8a8e99;font-size:11px}.title-meta{display:flex;align-items:center;gap:12px;flex-wrap:wrap;color:#667085;font-size:10px}.head-actions{display:flex;gap:8px}.detail-tabs{height:42px;flex:0 0 42px;background:#fff;border-bottom:1px solid #e7e9ed;display:flex;padding:0 18px}.detail-tabs button{height:42px;padding:0 14px;border:0;border-bottom:2px solid transparent;background:none;color:#667085;font-size:12px;cursor:pointer}.detail-tabs button.active{color:#1677ff;border-bottom-color:#1677ff;font-weight:600}.detail-body{flex:1;min-height:0;overflow:auto;padding:14px 18px 20px}.overview-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.info-card,.description-card{overflow:hidden}.card-head{min-height:42px;padding:0 14px;border-bottom:1px solid #edf0f3;display:flex;align-items:center;font-size:13px;font-weight:600}.info-card dl{display:grid;grid-template-columns:108px minmax(0,1fr);gap:10px 12px;margin:0;padding:14px;font-size:11px}.info-card dt{color:#98a2b3}.info-card dd{margin:0;color:#475467;word-break:break-all}.description-card{grid-column:1/-1}.description-card>p{margin:0;padding:16px 16px 10px;font-size:12px;color:#475467;line-height:1.7}.truth-note{margin:0 16px 16px;padding:9px 11px;background:#f8fafc;border:1px solid #eaecf0;color:#667085;font-size:10px;line-height:1.6}.ok-text{color:#16a34a}.warn-text{color:#d97706}.table-card{height:100%;overflow:hidden}.card-toolbar{height:46px;padding:0 13px;border-bottom:1px solid #edf0f3;display:flex;align-items:center;gap:9px}.card-toolbar strong{font-size:13px}.card-toolbar span{font-size:10px;color:#98a2b3}.spacer{flex:1}.preview-wrap{height:calc(100% - 46px);overflow:auto}.preview-table{border-collapse:collapse;min-width:100%;font-size:10px}.preview-table th,.preview-table td{padding:9px 11px;border-right:1px solid #edf0f3;border-bottom:1px solid #edf0f3;white-space:nowrap;text-align:left}.preview-table th{position:sticky;top:0;background:#f8fafc;color:#667085;z-index:1}.error-box{margin:14px;padding:10px;border:1px solid #fecaca;background:#fff1f2;color:#b42318;font-size:11px}.empty-panel{padding:40px;text-align:center;color:#98a2b3;font-size:11px}.lineage-grid{display:grid;grid-template-columns:1fr;gap:12px}.lineage-summary{padding-bottom:14px}.lineage-nodes{display:grid;grid-template-columns:1fr 1.5fr 1fr;gap:12px;padding:16px}.lineage-nodes>div{padding:14px;border:1px solid #e5e7eb;background:#fbfcfd}.lineage-nodes span,.lineage-nodes strong{display:block}.lineage-nodes span{font-size:10px;color:#98a2b3}.lineage-nodes strong{margin-top:6px;font-size:14px;color:#344054}.lineage-nodes .current-node{border-color:#91bfff;background:#f3f8ff}.lineage-summary>.el-button{margin-left:16px}.governance-grid{display:grid;grid-template-columns:260px minmax(0,1fr);gap:12px}.governance-score{padding:20px}.governance-score span{font-size:11px;color:#667085}.governance-score strong{display:block;margin-top:8px;font-size:34px}.governance-score p{font-size:10px;line-height:1.7;color:#98a2b3}.governance-list{overflow:hidden}.check-row{display:grid;grid-template-columns:30px minmax(0,1fr) 70px;align-items:center;gap:10px;padding:12px 14px;border-bottom:1px solid #f0f2f5}.check-dot{width:24px;height:24px;border-radius:50%;display:grid;place-items:center;background:#fff1f0;color:#d92d20;font-size:11px}.check-dot.ok{background:#ecfdf3;color:#16a34a}.check-row strong,.check-row small{display:block}.check-row strong{font-size:11px}.check-row small{margin-top:3px;color:#98a2b3;font-size:10px}.check-state{font-size:10px;color:#d97706;text-align:right}.check-state.ok{color:#16a34a}.unsupported-card{padding:28px;max-width:760px}.unsupported-card strong{font-size:15px}.unsupported-card p{font-size:11px;color:#667085;line-height:1.8}.change-card{overflow:hidden}.change-row{display:grid;grid-template-columns:130px 220px 1fr;gap:12px;padding:12px 14px;border-bottom:1px solid #f0f2f5;font-size:11px}.change-row span{color:#98a2b3}.change-row small{color:#98a2b3}@media(max-width:1200px){.overview-grid{grid-template-columns:1fr 1fr}.description-card{grid-column:1/-1}}@media(max-width:900px){.overview-grid,.governance-grid{grid-template-columns:1fr}.head-actions{display:none}}
</style>
