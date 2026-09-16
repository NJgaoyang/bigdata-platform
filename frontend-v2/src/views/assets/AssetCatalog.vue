<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { assetApi, type AssetItem } from '../../api/domain'
import { assetDetailPath, assetSearchText, assetTypeClass, assetTypeLabel } from './asset-utils'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const rows = ref<AssetItem[]>([])
const keyword = ref('')
const typeFilter = ref('')
const sourceFilter = ref('')

const mode = computed(() => route.path.endsWith('/favorites') ? 'favorites' : 'catalog')
const sources = computed(() => [...new Set(rows.value.map(item => item.source).filter((v): v is string => Boolean(v)))].sort())
const typeCounts = computed(() => ({
  ALL: rows.value.length,
  TABLE: rows.value.filter(item => item.type === 'TABLE').length,
  DATASET: rows.value.filter(item => item.type === 'DATASET').length,
  METRIC: rows.value.filter(item => item.type === 'METRIC').length
}))
const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return rows.value.filter(item =>
    (!typeFilter.value || item.type === typeFilter.value) &&
    (!sourceFilter.value || item.source === sourceFilter.value) &&
    (!q || assetSearchText(item).includes(q))
  )
})

function syncFiltersFromRoute() {
  keyword.value = String(route.query.q || '')
  typeFilter.value = String(route.query.type || '')
  sourceFilter.value = String(route.query.source || '')
}

async function load() {
  loading.value = true
  syncFiltersFromRoute()
  try {
    rows.value = mode.value === 'favorites' ? await assetApi.favorites() : await assetApi.catalog()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '资产加载失败')
  } finally {
    loading.value = false
  }
}

async function toggle(item: AssetItem, event?: Event) {
  event?.stopPropagation()
  try {
    if (item.favorite) await assetApi.unfavorite(item.ref)
    else await assetApi.favorite(item.type, item.ref)
    ElMessage.success(item.favorite ? '已取消收藏' : '已收藏')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

function selectType(type: string) {
  typeFilter.value = type
}

function open(item: AssetItem) {
  router.push(assetDetailPath(item))
}

watch(() => [route.path, route.query.q, route.query.type, route.query.source], load)
onMounted(load)
</script>

<template>
  <div class="ds-page asset-catalog">
    <PageHeader
      :title="mode === 'catalog' ? '资产目录' : '我的收藏'"
      :subtitle="mode === 'catalog' ? '从统一目录发现真实数据表、数据集与已认证指标。' : '集中访问自己收藏的常用数据资产。'"
    />

    <div class="catalog-shell ds-card">
      <aside class="catalog-aside">
        <div class="aside-title">资产类型</div>
        <button :class="['facet-row',{active:!typeFilter}]" @click="selectType('')"><span>全部资产</span><b>{{ typeCounts.ALL }}</b></button>
        <button :class="['facet-row',{active:typeFilter==='TABLE'}]" @click="selectType('TABLE')"><span>数据表</span><b>{{ typeCounts.TABLE }}</b></button>
        <button :class="['facet-row',{active:typeFilter==='DATASET'}]" @click="selectType('DATASET')"><span>数据集</span><b>{{ typeCounts.DATASET }}</b></button>
        <button :class="['facet-row',{active:typeFilter==='METRIC'}]" @click="selectType('METRIC')"><span>认证指标</span><b>{{ typeCounts.METRIC }}</b></button>

        <div class="aside-title source-title">数据来源</div>
        <button :class="['facet-row',{active:!sourceFilter}]" @click="sourceFilter=''">全部来源</button>
        <button v-for="source in sources" :key="source" :class="['facet-row source-row',{active:sourceFilter===source}]" @click="sourceFilter=source">
          <span :title="source">{{ source }}</span>
        </button>
      </aside>

      <main class="catalog-main">
        <div class="catalog-toolbar">
          <el-input v-model="keyword" clearable placeholder="搜索资产名称、描述、数据库或数据源" class="catalog-search" />
          <el-select v-model="typeFilter" clearable placeholder="全部类型" style="width:140px">
            <el-option label="数据表" value="TABLE" />
            <el-option label="数据集" value="DATASET" />
            <el-option label="认证指标" value="METRIC" />
          </el-select>
          <el-select v-model="sourceFilter" clearable filterable placeholder="全部来源" style="width:180px">
            <el-option v-for="source in sources" :key="source" :label="source" :value="source" />
          </el-select>
          <span class="result-count">{{ filtered.length }} 项</span>
          <div class="ds-spacer" />
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>

        <div class="catalog-table" v-loading="loading">
          <div v-if="!loading && !filtered.length" class="ds-empty">
            <div><div class="ds-empty__title">{{ mode === 'favorites' ? '暂无匹配的收藏资产' : '暂无匹配资产' }}</div><div>调整关键词、资产类型或来源筛选条件。</div></div>
          </div>
          <el-table v-else :data="filtered" height="100%" row-class-name="asset-table-row" @row-click="open">
            <el-table-column label="资产" min-width="300">
              <template #default="scope">
                <div class="asset-cell">
                  <span :class="['asset-icon',assetTypeClass(scope.row.type)]">{{ assetTypeLabel(scope.row.type).slice(0,1) }}</span>
                  <div class="asset-copy"><button @click.stop="open(scope.row)">{{ scope.row.name }}</button><small>{{ scope.row.description || '暂无描述' }}</small></div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="105"><template #default="scope"><span class="type-pill">{{ assetTypeLabel(scope.row.type) }}</span></template></el-table-column>
            <el-table-column prop="source" label="来源" min-width="150"><template #default="scope">{{ scope.row.source || '—' }}</template></el-table-column>
            <el-table-column prop="detail" label="位置 / 编码" min-width="210" show-overflow-tooltip><template #default="scope">{{ scope.row.detail || '—' }}</template></el-table-column>
            <el-table-column prop="owner" label="负责人" width="120"><template #default="scope">{{ scope.row.owner || (scope.row.type==='TABLE'?'详情中查看':'—') }}</template></el-table-column>
            <el-table-column label="状态" width="105"><template #default="scope"><StatusBadge :status="scope.row.status" /></template></el-table-column>
            <el-table-column label="操作" width="135" fixed="right">
              <template #default="scope">
                <el-button link type="primary" @click.stop="open(scope.row)">查看详情</el-button>
                <el-button link @click="toggle(scope.row,$event)">{{ scope.row.favorite ? '取消收藏' : '收藏' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.asset-catalog{height:calc(100vh - var(--ds-topbar));display:flex;flex-direction:column;overflow:hidden}.catalog-shell{flex:1;min-height:0;display:grid;grid-template-columns:220px minmax(0,1fr);overflow:hidden}.catalog-aside{border-right:1px solid #e8eaee;padding:14px 10px;overflow:auto;background:#fbfcfd}.aside-title{padding:0 9px 8px;color:#98a2b3;font-size:10px;font-weight:600;text-transform:uppercase}.source-title{margin-top:18px}.facet-row{width:100%;height:34px;border:0;background:transparent;border-radius:4px;padding:0 9px;display:flex;align-items:center;justify-content:space-between;color:#525866;font-size:12px;cursor:pointer;text-align:left}.facet-row:hover{background:#f0f5fb}.facet-row.active{background:#eaf3ff;color:#1677ff;font-weight:600}.facet-row b{font-size:10px;font-weight:500;color:#98a2b3}.source-row span{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.catalog-main{min-width:0;display:flex;flex-direction:column}.catalog-toolbar{height:50px;flex:0 0 50px;padding:0 12px;border-bottom:1px solid #edf0f3;display:flex;align-items:center;gap:8px}.catalog-search{width:min(420px,40vw)}.result-count{font-size:11px;color:#98a2b3}.catalog-table{flex:1;min-height:0}.asset-cell{display:flex;align-items:center;gap:10px;min-width:0}.asset-icon{width:30px;height:30px;flex:0 0 30px;border-radius:5px;display:grid;place-items:center;font-size:11px;font-weight:700}.asset-icon.table{background:#eaf3ff;color:#1677ff}.asset-icon.dataset{background:#edf9f1;color:#16a34a}.asset-icon.metric{background:#fff6e8;color:#d97706}.asset-copy{min-width:0}.asset-copy button{display:block;max-width:100%;border:0;background:none;padding:0;color:#344054;font-size:12px;font-weight:600;cursor:pointer;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.asset-copy button:hover{color:#1677ff}.asset-copy small{display:block;margin-top:3px;color:#98a2b3;font-size:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.type-pill{display:inline-flex;height:22px;align-items:center;padding:0 7px;border:1px solid #e4e7ec;border-radius:4px;color:#667085;font-size:10px;background:#fff}:deep(.asset-table-row){cursor:pointer}:deep(.asset-table-row:hover>td.el-table__cell){background:#f8fbff}.ds-empty{height:100%;display:grid;place-items:center}@media(max-width:1000px){.catalog-shell{grid-template-columns:180px minmax(0,1fr)}}
</style>
