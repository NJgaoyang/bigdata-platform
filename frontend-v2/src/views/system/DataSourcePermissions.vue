<script setup lang="ts">
import { computed,onMounted,reactive,ref } from 'vue'
import { ElMessage,ElMessageBox } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import { accessApi, type DataSourcePermission, type PlatformUser } from '../../api/access'
import { dataSourceApi, type DataSourceView } from '../../api/platform'
import { authApi, type CurrentUser } from '../../api/auth'
import { hasPermission } from '../../auth/permissions'

const loading=ref(true),dialog=ref(false),me=ref<CurrentUser|null>(null)
const users=ref<PlatformUser[]>([]),sources=ref<DataSourceView[]>([]),rows=ref<DataSourcePermission[]>([])
const form=reactive({dataSourceId:undefined as number|undefined,userId:undefined as number|undefined,permissionCode:'VIEW'})
const canEdit=computed(()=>hasPermission(me.value,'SYSTEM_SETTINGS_EDIT'))

async function load(){loading.value=true;try{[me.value,users.value,sources.value,rows.value]=await Promise.all([authApi.me(),accessApi.users(),dataSourceApi.list(),accessApi.dataSourcePermissions()])}catch(e){ElMessage.error(msg(e))}finally{loading.value=false}}
function open(){form.dataSourceId=sources.value[0]?.id;form.userId=users.value.find(u=>u.roleCode!=='ADMIN')?.id||users.value[0]?.id;form.permissionCode='VIEW';dialog.value=true}
async function grant(){if(!form.dataSourceId||!form.userId)return ElMessage.warning('请选择数据源和用户');try{await accessApi.grantDataSource(form.dataSourceId,form.userId,form.permissionCode);ElMessage.success('数据源权限已授权');dialog.value=false;await load()}catch(e){ElMessage.error(msg(e))}}
async function revoke(row:DataSourcePermission){try{await ElMessageBox.confirm(`撤销 ${row.displayName} 对 ${row.dataSourceName} 的 ${row.permissionCode} 权限？`,'撤销权限',{type:'warning'});await accessApi.revokeDataSource(row);ElMessage.success('权限已撤销');await load()}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(msg(e))}}
function permissionLabel(v:string){return ({VIEW:'查看',QUERY:'查询',EDIT:'编辑'} as Record<string,string>)[v]||v}
function msg(e:unknown){return e instanceof Error?e.message:'操作失败'}
onMounted(load)
</script>
<template><div class="ds-page"><PageHeader title="数据源权限" subtitle="按用户控制数据源查看、查询和编辑权限；QUERY 自动包含查看能力，EDIT 自动包含查看与查询能力。"><template #actions><el-button v-if="canEdit" type="primary" @click="open">+ 授权</el-button></template></PageHeader>
<el-skeleton v-if="loading" :rows="7" animated/><div v-else class="ds-card"><div class="ds-toolbar"><strong>授权关系</strong><div class="ds-spacer"/><el-button @click="load">刷新</el-button></div><div v-if="!rows.length" class="ds-empty"><div><div class="ds-empty__title">暂无数据源授权</div><div>管理员不需要单独授权；普通用户需要显式绑定数据源。</div></div></div><table v-else class="ds-table"><thead><tr><th>数据源</th><th>用户</th><th>账号</th><th>权限</th><th>操作</th></tr></thead><tbody><tr v-for="r in rows" :key="`${r.dataSourceId}-${r.userId}-${r.permissionCode}`"><td class="ds-resource">{{r.dataSourceName}}</td><td>{{r.displayName}}</td><td>{{r.username}}</td><td><span class="permission-pill">{{permissionLabel(r.permissionCode)}}</span></td><td><span v-if="canEdit" class="danger" @click="revoke(r)">撤销</span><span v-else>—</span></td></tr></tbody></table></div>
<el-dialog v-model="dialog" title="新增数据源授权" width="520px"><el-form label-position="top"><el-form-item label="数据源"><el-select v-model="form.dataSourceId" style="width:100%"><el-option v-for="s in sources" :key="s.id" :label="`${s.name} · ${s.type}`" :value="s.id"/></el-select></el-form-item><el-form-item label="用户"><el-select v-model="form.userId" style="width:100%"><el-option v-for="u in users" :key="u.id" :label="`${u.displayName} · ${u.username}`" :value="u.id"/></el-select></el-form-item><el-form-item label="权限"><el-radio-group v-model="form.permissionCode"><el-radio-button value="VIEW">查看</el-radio-button><el-radio-button value="QUERY">查询</el-radio-button><el-radio-button value="EDIT">编辑</el-radio-button></el-radio-group></el-form-item></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="grant">确认授权</el-button></template></el-dialog>
</div></template>
<style scoped>.danger{color:#d92d20;cursor:pointer}.permission-pill{display:inline-flex;align-items:center;height:24px;padding:0 8px;border:1px solid #d0d5dd;border-radius:12px;background:#f8fafc;color:#344054;font-size:11px}
/* DataSphere light product theme — visual overrides only */
.ds-page{padding-top:28px;padding-bottom:36px;background:linear-gradient(180deg,#fbfdff 0%,#f8fbff 100%)}
.ds-card{border:1px solid #e4ebf5;border-radius:16px;background:rgba(255,255,255,.98);box-shadow:0 10px 30px rgba(42,83,163,.04);overflow:hidden}.ds-toolbar{min-height:54px;padding:0 16px;border-bottom:1px solid #edf2f7;background:#fbfdff;color:#17304f}.ds-toolbar strong{font-size:13px}
.ds-table th{height:44px;background:#fbfcfe;color:#78889f;font-size:11px;font-weight:650;border-bottom:1px solid #edf2f7}.ds-table td{height:52px;border-bottom:1px solid #f0f3f7;color:#465a73}.ds-table tbody tr:hover td{background:#f7faff}.ds-resource{color:#17304f;font-weight:650}.permission-pill{height:25px;padding:0 9px;border-color:#cfe0f8;border-radius:999px;background:#edf5ff;color:#2468d8;font-weight:600}.danger{color:#dc5555}.danger:hover{color:#c33f3f}
:deep(.el-dialog){border-radius:16px;overflow:hidden;box-shadow:0 24px 64px rgba(30,60,110,.16)}:deep(.el-dialog__header){margin:0;padding:18px 22px 14px;border-bottom:1px solid #edf1f6}:deep(.el-dialog__body){padding:20px 22px;background:#fbfdff}:deep(.el-dialog__footer){padding:12px 20px 16px;border-top:1px solid #edf1f6;background:#fff}:deep(.el-input__wrapper),:deep(.el-select__wrapper){border-radius:9px;box-shadow:0 0 0 1px #dfe8f3 inset}:deep(.el-radio-button__inner){border-color:#dbe6f3}:deep(.el-button){border-radius:8px}

</style>
