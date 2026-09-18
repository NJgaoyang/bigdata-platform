<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import { developmentApi, type DevFile, type DevProject, type FileVersion } from '../../api/domain'
const projects=ref<DevProject[]>([]), files=ref<DevFile[]>([]), versions=ref<FileVersion[]>([]), projectId=ref<number>(), selected=ref<DevFile>(), loading=ref(false)
async function boot(){projects.value=await developmentApi.projects();if(projects.value[0]){projectId.value=projects.value[0].id;await loadFiles()}}
async function loadFiles(){if(!projectId.value)return;files.value=await developmentApi.files(projectId.value);if(files.value[0])await select(files.value[0])}
async function select(file:DevFile){selected.value=file;loading.value=true;try{versions.value=await developmentApi.versions(file.id)}finally{loading.value=false}}
onMounted(boot)
</script>
<template><div class="ds-page"><PageHeader title="历史版本" subtitle="查看任务统一版本；代码、调度和依赖任一变化都会形成新的 Vx。"/><div class="version-grid"><div class="ds-card"><div class="ds-toolbar"><el-select v-model="projectId" style="width:100%" @change="loadFiles"><el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id"/></el-select></div><button v-for="f in files" :key="f.id" class="file-row" :class="{active:selected?.id===f.id}" @click="select(f)"><strong>{{f.name}}</strong><small>当前 V{{f.currentVersion}}</small></button></div><div class="ds-card"><div class="ds-card__head"><div><div class="ds-card__title">{{selected?.name||'文件版本'}}</div><div class="ds-card__sub">任务版本代表一次完整开发状态；工作流绑定任务本身，发布时冻结对应生产 Vx。</div></div></div><el-table :data="versions" v-loading="loading"><el-table-column prop="versionNo" label="版本" width="100"><template #default="s">V{{s.row.versionNo}}</template></el-table-column><el-table-column prop="checksum" label="Checksum" min-width="180" show-overflow-tooltip/><el-table-column label="状态" width="120"><template #default="s"><el-tag :type="s.row.publishFlag?'success':'info'">{{s.row.publishFlag?'已发布':'历史版本'}}</el-tag></template></el-table-column><el-table-column label="内容" min-width="320"><template #default="s"><code>{{s.row.content}}</code></template></el-table-column></el-table></div></div></div></template>
<style scoped>.version-grid{display:grid;grid-template-columns:260px minmax(0,1fr);gap:12px}.file-row{display:flex;flex-direction:column;width:100%;border:0;border-top:1px solid var(--ds-border-soft);background:#fff;padding:11px 14px;text-align:left;cursor:pointer}.file-row.active{background:var(--ds-brand-soft);color:var(--ds-brand)}.file-row small{margin-top:4px;color:var(--ds-text-tertiary)}code{font-size:11px;white-space:nowrap}

/* DataSphere light product theme — visual overrides only */
.ds-page{padding-top:28px;padding-bottom:36px;background:linear-gradient(180deg,#fbfdff 0%,#f8fbff 100%)}
.version-grid{grid-template-columns:260px minmax(0,1fr);gap:14px}.version-grid>.ds-card{overflow:hidden;border:1px solid #e4ebf5;border-radius:16px;background:rgba(255,255,255,.97);box-shadow:0 10px 30px rgba(42,83,163,.04)}
.version-grid .ds-toolbar{padding:12px;border-bottom:1px solid #edf2f7;background:#fbfdff}.version-grid :deep(.el-select__wrapper){border-radius:9px;box-shadow:0 0 0 1px #dfe8f3 inset}
.file-row{border-top-color:#eef2f7;padding:12px 14px;color:#42566f}.file-row:hover{background:#f7faff}.file-row.active{background:#eaf2ff;color:#2468d8;box-shadow:inset 3px 0 0 #3b82f6}.file-row strong{font-size:12px}.file-row small{color:#8b99ad}
.version-grid :deep(.el-table){--el-table-header-bg-color:#fbfcfe;--el-table-row-hover-bg-color:#f7faff;--el-table-border-color:#eef2f7}.version-grid :deep(.el-table th.el-table__cell){height:44px;background:#fbfcfe;color:#78889f;font-size:11px;font-weight:650}.version-grid :deep(.el-table td.el-table__cell){border-bottom-color:#f0f3f7}
.version-grid code{color:#41536b;background:#f7f9fc;border:1px solid #edf1f6;border-radius:5px;padding:3px 6px}

</style>
