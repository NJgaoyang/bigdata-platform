<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { assetApi, type AssetItem } from '../../api/domain'
import { assetDetailPath, assetTypeLabel } from './asset-utils'

const router=useRouter(),loading=ref(false),assets=ref<AssetItem[]>([]),filter=ref<'ALL'|'DESCRIPTION'|'OWNER'>('ALL')
type Issue={asset:AssetItem;kind:'DESCRIPTION'|'OWNER';message:string}
const issues=computed<Issue[]>(()=>{
  const out:Issue[]=[]
  for(const asset of assets.value){
    if(!asset.description?.trim())out.push({asset,kind:'DESCRIPTION',message:'缺少资产业务描述'})
    if(asset.type!=='TABLE'&&!asset.owner?.trim())out.push({asset,kind:'OWNER',message:'缺少资产负责人'})
  }
  return out
})
const filtered=computed(()=>filter.value==='ALL'?issues.value:issues.value.filter(i=>i.kind===filter.value))
const missingDescription=computed(()=>issues.value.filter(i=>i.kind==='DESCRIPTION').length)
const missingOwner=computed(()=>issues.value.filter(i=>i.kind==='OWNER').length)
const descriptionCoverage=computed(()=>assets.value.length?Math.round((assets.value.length-missingDescription.value)/assets.value.length*100):0)
async function load(){loading.value=true;try{assets.value=await assetApi.catalog()}catch(e){ElMessage.error(e instanceof Error?e.message:'治理数据加载失败')}finally{loading.value=false}}
onMounted(load)
</script>
<template>
<div class="ds-page governance-page" v-loading="loading">
  <PageHeader title="资产治理" subtitle="先从可验证的元数据完整性开始治理；不把尚未接入的数据质量、热度和标签统计伪装成生产数据。"/>
  <section class="metrics">
    <div class="ds-card metric"><span>资产总数</span><strong>{{assets.length}}</strong><small>当前账号可访问</small></div>
    <div class="ds-card metric"><span>描述覆盖率</span><strong>{{descriptionCoverage}}%</strong><small>{{missingDescription}} 项待完善</small></div>
    <div class="ds-card metric"><span>非表资产负责人缺失</span><strong>{{missingOwner}}</strong><small>数据集 / 认证指标</small></div>
    <div class="ds-card metric"><span>基础治理问题</span><strong>{{issues.length}}</strong><small>按当前真实字段计算</small></div>
  </section>
  <section class="ds-card truth-banner"><strong>治理口径说明</strong><span>数据表负责人存储在 metadata table profile 中，当前资产目录接口未批量返回该字段，因此此页不会错误地把所有数据表判定为“负责人缺失”。表负责人请进入资产详情核查。</span></section>
  <section class="ds-card issue-card">
    <div class="issue-toolbar"><strong>待治理项</strong><el-radio-group v-model="filter" size="small"><el-radio-button value="ALL">全部 {{issues.length}}</el-radio-button><el-radio-button value="DESCRIPTION">描述缺失 {{missingDescription}}</el-radio-button><el-radio-button value="OWNER">负责人缺失 {{missingOwner}}</el-radio-button></el-radio-group><div class="ds-spacer"/><el-button :loading="loading" @click="load">重新扫描</el-button></div>
    <el-table :data="filtered" height="calc(100vh - 360px)" @row-click="row=>router.push(assetDetailPath(row.asset))">
      <el-table-column label="资产" min-width="280"><template #default="scope"><div class="asset-name"><strong>{{scope.row.asset.name}}</strong><small>{{scope.row.asset.detail||scope.row.asset.ref}}</small></div></template></el-table-column>
      <el-table-column label="类型" width="110"><template #default="scope">{{assetTypeLabel(scope.row.asset.type)}}</template></el-table-column>
      <el-table-column prop="asset.source" label="来源" min-width="150"><template #default="scope">{{scope.row.asset.source||'—'}}</template></el-table-column>
      <el-table-column label="问题类型" width="130"><template #default="scope"><span :class="['issue-tag',scope.row.kind.toLowerCase()]">{{scope.row.kind==='DESCRIPTION'?'描述缺失':'负责人缺失'}}</span></template></el-table-column>
      <el-table-column prop="message" label="问题说明" min-width="240"/>
      <el-table-column label="资产状态" width="110"><template #default="scope"><StatusBadge :status="scope.row.asset.status"/></template></el-table-column>
      <el-table-column label="操作" width="100" fixed="right"><template #default="scope"><el-button link type="primary" @click.stop="router.push(assetDetailPath(scope.row.asset))">查看详情</el-button></template></el-table-column>
      <template #empty><div class="empty">当前筛选下暂无待治理项</div></template>
    </el-table>
  </section>
</div>
</template>
<style scoped>
.governance-page{height:calc(100vh - var(--ds-topbar));display:flex;flex-direction:column;gap:12px;overflow:hidden}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.metric{padding:14px 16px}.metric span,.metric small{display:block;color:#98a2b3;font-size:10px}.metric strong{display:block;margin:6px 0;font-size:23px;color:#252b3a}.truth-banner{padding:10px 13px;border-left:3px solid #f59e0b;background:#fffbf2;display:flex;gap:10px;align-items:flex-start}.truth-banner strong{font-size:11px;white-space:nowrap;color:#8a6116}.truth-banner span{font-size:10px;line-height:1.6;color:#8a6116}.issue-card{flex:1;min-height:0;overflow:hidden}.issue-toolbar{height:50px;padding:0 12px;border-bottom:1px solid #edf0f3;display:flex;align-items:center;gap:14px}.issue-toolbar>strong{font-size:13px}.asset-name strong,.asset-name small{display:block}.asset-name strong{font-size:11px}.asset-name small{margin-top:3px;color:#98a2b3;font-size:9px}.issue-tag{display:inline-flex;padding:3px 6px;border-radius:3px;font-size:9px}.issue-tag.description{background:#fff7ed;color:#c2410c}.issue-tag.owner{background:#eef4ff;color:#175cd3}:deep(.el-table__row){cursor:pointer}.empty{padding:30px;color:#98a2b3}@media(max-width:1000px){.metrics{grid-template-columns:repeat(2,1fr)}}
</style>