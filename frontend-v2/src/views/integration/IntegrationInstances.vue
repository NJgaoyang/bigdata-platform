<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { operationsApi, type OperationInstance } from '../../api/domain'
const rows=ref<OperationInstance[]>([]),loading=ref(false),type=ref('ALL')
async function load(){loading.value=true;try{rows.value=(await operationsApi.instances()).filter(x=>['OFFLINE','REALTIME'].includes(x.type))}catch(e){ElMessage.error(e instanceof Error?e.message:'加载失败')}finally{loading.value=false}}
async function stop(row:OperationInstance){try{await operationsApi.stop(row.type,row.id);ElMessage.success('停止请求已提交');await load()}catch(e){ElMessage.error(e instanceof Error?e.message:'停止失败')}}
onMounted(load)
</script>
<template><div class="ds-page"><PageHeader title="运行实例" subtitle="统一查看 SeaTunnel 离线实例与 Flink CDC 实时实例。"/><div class="ds-card"><div class="ds-toolbar"><el-segmented v-model="type" :options="[{label:'全部',value:'ALL'},{label:'离线',value:'OFFLINE'},{label:'实时',value:'REALTIME'}]"/><div class="ds-spacer"/><el-button :loading="loading" @click="load">刷新</el-button></div><el-table :data="rows.filter(r=>type==='ALL'||r.type===type)" v-loading="loading"><el-table-column prop="name" label="任务" min-width="180"/><el-table-column prop="type" label="类型" width="110"/><el-table-column prop="engine" label="引擎" width="130"/><el-table-column label="状态" width="130"><template #default="s"><StatusBadge :status="s.row.status"/></template></el-table-column><el-table-column prop="externalId" label="运行 ID" min-width="190" show-overflow-tooltip/><el-table-column prop="startedAt" label="开始时间" min-width="170"/><el-table-column prop="finishedAt" label="结束时间" min-width="170"/><el-table-column label="操作" width="90"><template #default="s"><el-button v-if="['RUNNING','STARTING','QUEUED'].some(x=>s.row.status?.includes(x))" link type="danger" @click="stop(s.row)">停止</el-button></template></el-table-column></el-table></div></div></template>
