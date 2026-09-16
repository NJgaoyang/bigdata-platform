<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import { assetApi, metadataApi, type AssetItem, type LineageView } from '../../api/domain'
import { assetDetailPath, parseTableAssetRef } from './asset-utils'

const route=useRoute(),router=useRouter()
const loading=ref(false),assets=ref<AssetItem[]>([]),lineage=ref<LineageView[]>([]),selectedRef=ref('')
const tableAssets=computed(()=>assets.value.filter(item=>item.type==='TABLE'))
const selected=computed(()=>assets.value.find(item=>item.ref===selectedRef.value))
const parsed=computed(()=>selected.value?parseTableAssetRef(selected.value.ref):undefined)
const qualified=computed(()=>parsed.value?`${parsed.value.database}.${parsed.value.table}`:'')
const upstream=computed(()=>unique(lineage.value.filter(e=>norm(e.targetTable)===norm(qualified.value)).map(e=>e.sourceTable)))
const downstream=computed(()=>unique(lineage.value.filter(e=>norm(e.sourceTable)===norm(qualified.value)).map(e=>e.targetTable)))
function norm(v?:string){return (v||'').replace(/`/g,'').toLowerCase()}
function unique(v:string[]){return [...new Set(v)]}
async function load(){loading.value=true;try{assets.value=await assetApi.catalog();const q=String(route.query.ref||'');selectedRef.value=tableAssets.value.some(i=>i.ref===q)?q:(tableAssets.value[0]?.ref||'');await loadLineage()}catch(e){ElMessage.error(e instanceof Error?e.message:'数据地图加载失败')}finally{loading.value=false}}
async function loadLineage(){lineage.value=[];if(!qualified.value)return;try{lineage.value=await metadataApi.lineage(qualified.value)}catch(e){ElMessage.error(e instanceof Error?e.message:'血缘加载失败')}}
function findAsset(name:string){const n=norm(name);return tableAssets.value.find(item=>norm(item.detail)===n||norm(item.name)===n||n.endsWith(`.${norm(item.name)}`))}
function openNode(name:string){const match=findAsset(name);if(match)router.push(assetDetailPath(match));else ElMessage.info(`当前目录中未匹配到资产：${name}`)}
onMounted(load)
</script>
<template>
<div class="ds-page asset-map" v-loading="loading">
  <PageHeader title="数据地图" subtitle="基于平台真实表级血缘关系查看上游、当前表和下游。"/>
  <div class="ds-card map-shell">
    <div class="map-toolbar"><el-select v-model="selectedRef" filterable placeholder="选择数据表" style="width:420px" @change="loadLineage"><el-option v-for="item in tableAssets" :key="item.ref" :label="`${item.detail||item.name} · ${item.source||''}`" :value="item.ref"/></el-select><el-button @click="loadLineage">刷新血缘</el-button><span>共 {{lineage.length}} 条关系</span><div class="ds-spacer"/><el-button v-if="selected" type="primary" plain @click="router.push(assetDetailPath(selected))">查看资产详情</el-button></div>
    <div v-if="selected" class="canvas">
      <div class="column"><div class="column-title">上游 {{upstream.length}}</div><button v-for="node in upstream" :key="node" class="node" @click="openNode(node)"><strong>{{node}}</strong><small>上游数据表</small></button><div v-if="!upstream.length" class="empty-node">暂无上游</div></div>
      <div class="edge">→</div>
      <div class="column current"><div class="column-title">当前资产</div><button class="node active" @click="router.push(assetDetailPath(selected))"><strong>{{qualified}}</strong><small>{{selected.source}} · {{selected.description||'暂无描述'}}</small></button></div>
      <div class="edge">→</div>
      <div class="column"><div class="column-title">下游 {{downstream.length}}</div><button v-for="node in downstream" :key="node" class="node" @click="openNode(node)"><strong>{{node}}</strong><small>下游数据表</small></button><div v-if="!downstream.length" class="empty-node">暂无下游</div></div>
    </div>
    <div v-else class="empty-map">当前没有可访问的数据表资产。</div>
    <div class="relations"><div class="relations-head">血缘明细</div><el-table :data="lineage" max-height="300"><el-table-column prop="sourceTable" label="上游" min-width="260"/><el-table-column label="关系" width="80"><template #default>→</template></el-table-column><el-table-column prop="targetTable" label="下游" min-width="260"/><el-table-column prop="relationType" label="类型" width="140"/><template #empty><div>暂无已解析血缘</div></template></el-table></div>
  </div>
</div>
</template>
<style scoped>
.asset-map{height:calc(100vh - var(--ds-topbar));display:flex;flex-direction:column;overflow:hidden}.map-shell{flex:1;min-height:0;display:flex;flex-direction:column;overflow:hidden}.map-toolbar{height:50px;flex:0 0 50px;border-bottom:1px solid #edf0f3;padding:0 12px;display:flex;align-items:center;gap:9px}.map-toolbar>span{font-size:10px;color:#98a2b3}.canvas{flex:1;min-height:310px;background:linear-gradient(#f2f4f7 1px,transparent 1px),linear-gradient(90deg,#f2f4f7 1px,transparent 1px);background-size:22px 22px;display:grid;grid-template-columns:minmax(220px,1fr) 50px minmax(260px,1fr) 50px minmax(220px,1fr);align-items:center;padding:34px 45px;overflow:auto}.column{display:flex;flex-direction:column;gap:10px}.column-title{font-size:10px;color:#98a2b3;text-align:center;margin-bottom:2px}.edge{text-align:center;color:#98a2b3;font-size:24px}.node{border:1px solid #dfe3e8;background:#fff;border-radius:6px;padding:12px;text-align:left;box-shadow:0 3px 10px rgba(16,24,40,.04);cursor:pointer}.node:hover{border-color:#84adff}.node.active{border-color:#1677ff;background:#f4f8ff;box-shadow:0 0 0 2px #e6f0ff}.node strong,.node small{display:block}.node strong{font-size:11px;color:#344054;word-break:break-all}.node small{margin-top:4px;color:#98a2b3;font-size:9px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.empty-node{padding:18px;text-align:center;border:1px dashed #dfe3e8;color:#98a2b3;background:rgba(255,255,255,.7);font-size:10px}.relations{flex:0 0 auto;border-top:1px solid #e8eaee}.relations-head{height:38px;padding:0 12px;display:flex;align-items:center;font-size:12px;font-weight:600}.empty-map{flex:1;display:grid;place-items:center;color:#98a2b3;font-size:12px}@media(max-width:1000px){.canvas{grid-template-columns:1fr;gap:12px}.edge{transform:rotate(90deg)}}
</style>