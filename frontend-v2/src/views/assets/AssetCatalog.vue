<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { assetApi, type AssetItem } from '../../api/domain'
const route=useRoute();const loading=ref(true),rows=ref<AssetItem[]>([]),keyword=ref(''),typeFilter=ref('')
const mode=computed(()=>route.path.endsWith('/favorites')?'favorites':'catalog')
const filtered=computed(()=>rows.value.filter(r=>(!typeFilter.value||r.type===typeFilter.value)&&(!keyword.value||`${r.name} ${r.description||''} ${r.source||''}`.toLowerCase().includes(keyword.value.toLowerCase()))))
async function load(){loading.value=true;try{rows.value=mode.value==='favorites'?await assetApi.favorites():await assetApi.catalog()}catch(e){ElMessage.error(e instanceof Error?e.message:'资产加载失败')}finally{loading.value=false}}
async function toggle(r:AssetItem){try{r.favorite?await assetApi.unfavorite(r.ref):await assetApi.favorite(r.type,r.ref);ElMessage.success(r.favorite?'已取消收藏':'已收藏');await load()}catch(e){ElMessage.error(e instanceof Error?e.message:'操作失败')}}
watch(()=>route.path,load);onMounted(load)
</script><template><div class="ds-page"><PageHeader :title="mode==='catalog'?'资产目录':'我的收藏'" :subtitle="mode==='catalog'?'从统一目录发现数据表、数据集与已认证指标。':'快速访问自己收藏的数据资产。'"/><div class="ds-card"><div class="ds-toolbar"><el-input v-model="keyword" placeholder="搜索资产名称 / 描述" clearable style="width:260px"/><el-select v-model="typeFilter" clearable placeholder="全部类型" style="width:150px"><el-option label="数据表" value="TABLE"/><el-option label="数据集" value="DATASET"/><el-option label="认证指标" value="METRIC"/></el-select><div class="ds-spacer"/><el-button @click="load" :loading="loading">刷新</el-button></div><el-skeleton v-if="loading" :rows="7" animated/><div v-else-if="!filtered.length" class="ds-empty"><div><div class="ds-empty__title">{{mode==='favorites'?'暂无收藏资产':'暂无可用资产'}}</div><div>{{mode==='favorites'?'可在资产目录收藏常用资源。':'元数据、数据集或认证指标创建后会出现在这里。'}}</div></div></div><table v-else class="ds-table"><thead><tr><th>资产</th><th>类型</th><th>来源</th><th>Owner</th><th>状态</th><th>详情</th><th>操作</th></tr></thead><tbody><tr v-for="r in filtered" :key="r.ref"><td><span class="ds-resource">{{r.name}}</span><div class="sub">{{r.description||r.ref}}</div></td><td>{{r.type}}</td><td>{{r.source||'—'}}</td><td>{{r.owner||'—'}}</td><td><StatusBadge :status="r.status"/></td><td>{{r.detail||'—'}}</td><td><span class="ds-link" @click="toggle(r)">{{r.favorite?'取消收藏':'收藏'}}</span></td></tr></tbody></table></div></div></template><style scoped>.sub{margin-top:3px;color:#98a2b3;font-size:10px}

/* DataSphere light product theme — visual overrides only */
.ds-page{padding-top:28px;padding-bottom:36px;background:linear-gradient(180deg,#fbfdff 0%,#f8fbff 100%)}
.ds-page>.ds-card{overflow:hidden;border:1px solid #e4ebf5;border-radius:16px;background:rgba(255,255,255,.97);box-shadow:0 10px 30px rgba(42,83,163,.04)}
.ds-toolbar{min-height:62px;padding:12px 16px!important;border-bottom:1px solid #edf1f6;background:#fbfdff}.ds-toolbar :deep(.el-input__wrapper),.ds-toolbar :deep(.el-select__wrapper){min-height:36px;border-radius:9px;box-shadow:0 0 0 1px #dfe8f3 inset;background:#fff}.ds-toolbar :deep(.el-button){height:36px;border-radius:8px}
.ds-page :deep(.ds-table thead th){height:44px;background:#fbfcfe;color:#78889f;border-bottom-color:#e9eef5;font-size:11px;font-weight:650}.ds-page :deep(.ds-table tbody td){border-bottom-color:#f0f3f7;color:#42566f}.ds-page :deep(.ds-table tbody tr:hover td){background:#f7faff}.ds-resource{color:#2468d8!important;font-weight:650}.sub{margin-top:4px;color:#8b99ad;font-size:10px;line-height:1.5}.ds-link{display:inline-flex;padding:4px 8px;border-radius:7px}.ds-link:hover{background:#edf4ff;text-decoration:none}
.ds-empty{min-height:320px;background:linear-gradient(180deg,#fff,#fbfdff)}

</style>