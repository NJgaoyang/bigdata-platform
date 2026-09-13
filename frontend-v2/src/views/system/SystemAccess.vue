<script setup lang="ts">
import { computed,onMounted,reactive,ref,watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage,ElMessageBox } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { accessApi, type AuditLog, type PlatformRole, type PlatformUser } from '../../api/access'
import { authApi, type CurrentUser } from '../../api/auth'
import { hasPermission } from '../../auth/permissions'

const route=useRoute(), loading=ref(true), me=ref<CurrentUser|null>(null)
const users=ref<PlatformUser[]>([]), roles=ref<PlatformRole[]>([]), audits=ref<AuditLog[]>([])
const userDialog=ref(false), roleDialog=ref(false), permissionDialog=ref(false)
const userForm=reactive({username:'',displayName:'',password:'',roleCode:'USER',status:'ACTIVE'})
const roleForm=reactive({roleCode:'',roleName:''})
const permissionTarget=ref<{kind:'user'|'role';id:number;name:string}|null>(null), selectedPermissions=ref<string[]>([])
const mode=computed(()=>route.path.endsWith('/roles')?'roles':route.path.endsWith('/audit')?'audit':'users')
const canEdit=computed(()=>hasPermission(me.value,'SYSTEM_SETTINGS_EDIT'))

const modules=[
  ['WORKBENCH','工作台'],['DATA_INTEGRATION','数据集成'],['DATA_DEVELOPMENT','数据开发'],['WORKFLOW','工作流'],
  ['OPERATIONS','运维中心'],['METADATA','元数据'],['METRICS','指标中心'],['DATA_ASSETS','数据资产'],
  ['RELEASE','发布中心'],['SYSTEM_SETTINGS','系统管理']
] as const

async function load(){
  loading.value=true
  try{
    me.value=await authApi.me()
    if(mode.value==='users') [users.value,roles.value]=await Promise.all([accessApi.users(),accessApi.roles()])
    if(mode.value==='roles') roles.value=await accessApi.roles()
    if(mode.value==='audit') audits.value=await accessApi.auditLogs()
  }catch(e){ElMessage.error(e instanceof Error?e.message:'系统数据加载失败')}
  finally{loading.value=false}
}
function openUser(){Object.assign(userForm,{username:'',displayName:'',password:'',roleCode:roles.value.find(r=>r.roleCode==='USER')?.roleCode||roles.value[0]?.roleCode||'USER',status:'ACTIVE'});userDialog.value=true}
async function createUser(){try{await accessApi.createUser({...userForm});ElMessage.success('用户已创建');userDialog.value=false;await load()}catch(e){ElMessage.error(msg(e))}}
function openRole(){Object.assign(roleForm,{roleCode:'',roleName:''});roleDialog.value=true}
async function createRole(){try{await accessApi.createRole({roleCode:roleForm.roleCode.trim().toUpperCase(),roleName:roleForm.roleName.trim()});ElMessage.success('角色已创建');roleDialog.value=false;await load()}catch(e){ElMessage.error(msg(e))}}
async function toggle(u:PlatformUser){try{await accessApi.setUserStatus(u.id,u.status!=='ACTIVE');await load()}catch(e){ElMessage.error(msg(e))}}
async function remove(u:PlatformUser){try{await ElMessageBox.confirm(`删除用户“${u.displayName}”？`,'删除用户',{type:'warning'});await accessApi.removeUser(u.id);await load()}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(msg(e))}}
async function editUserPermissions(u:PlatformUser){try{selectedPermissions.value=await accessApi.userPermissions(u.id);permissionTarget.value={kind:'user',id:u.id,name:u.displayName};permissionDialog.value=true}catch(e){ElMessage.error(msg(e))}}
function editRolePermissions(r:PlatformRole){selectedPermissions.value=(r.permissions||[]).filter(p=>/_VIEW$|_EDIT$/.test(p));permissionTarget.value={kind:'role',id:r.id,name:r.roleName};permissionDialog.value=true}
function normalizePermissions(){const set=new Set(selectedPermissions.value);for(const [key] of modules)if(set.has(`${key}_EDIT`))set.add(`${key}_VIEW`);return [...set]}
async function savePermissions(){if(!permissionTarget.value)return;try{const values=normalizePermissions();if(permissionTarget.value.kind==='user')await accessApi.setUserPermissions(permissionTarget.value.id,values);else await accessApi.setRolePermissions(permissionTarget.value.id,values);ElMessage.success('权限已更新');permissionDialog.value=false;await load()}catch(e){ElMessage.error(msg(e))}}
function rolePermissionSummary(r:PlatformRole){const values=(r.permissions||[]).filter(x=>/_VIEW$|_EDIT$/.test(x));return values.length?`${values.length} 项权限`:'未配置（用户使用默认查看权限）'}
function fmt(v?:string){return v?v.replace('T',' ').slice(0,16):'—'}
function msg(e:unknown){return e instanceof Error?e.message:'操作失败'}
watch(()=>route.path,load);onMounted(load)
</script>

<template><div class="ds-page">
  <PageHeader :title="mode==='users'?'用户管理':mode==='roles'?'角色权限':'审计日志'" :subtitle="mode==='users'?'管理平台账号及用户级模块权限。':mode==='roles'?'角色权限用于未单独配置权限的用户。':'查看关键操作和生产变更记录。'">
    <template #actions><el-button v-if="canEdit&&mode==='users'" type="primary" @click="openUser">+ 新增用户</el-button><el-button v-if="canEdit&&mode==='roles'" type="primary" @click="openRole">+ 新增角色</el-button></template>
  </PageHeader>
  <el-skeleton v-if="loading" :rows="7" animated/>
  <div v-else-if="mode==='users'" class="ds-card"><table class="ds-table"><thead><tr><th>用户</th><th>账号</th><th>角色</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead><tbody><tr v-for="u in users" :key="u.id"><td class="ds-resource">{{u.displayName}}</td><td>{{u.username}}</td><td>{{u.roleCode}}</td><td><StatusBadge :status="u.status"/></td><td>{{fmt(u.createdAt)}}</td><td><template v-if="canEdit"><span class="ds-link" @click="editUserPermissions(u)">模块权限</span><span class="sep">·</span><span class="ds-link" @click="toggle(u)">{{u.status==='ACTIVE'?'禁用':'启用'}}</span><span class="sep">·</span><span class="danger" @click="remove(u)">删除</span></template><span v-else>—</span></td></tr></tbody></table></div>
  <div v-else-if="mode==='roles'" class="ds-card"><table class="ds-table"><thead><tr><th>角色</th><th>编码</th><th>权限</th><th>操作</th></tr></thead><tbody><tr v-for="r in roles" :key="r.id"><td class="ds-resource">{{r.roleName}}</td><td>{{r.roleCode}}</td><td>{{rolePermissionSummary(r)}}</td><td><span v-if="canEdit&&r.roleCode!=='ADMIN'" class="ds-link" @click="editRolePermissions(r)">配置权限</span><span v-else>—</span></td></tr></tbody></table></div>
  <div v-else class="ds-card"><table class="ds-table"><thead><tr><th>动作</th><th>资源</th><th>操作人</th><th>详情</th><th>时间</th></tr></thead><tbody><tr v-for="a in audits" :key="a.id"><td>{{a.action}}</td><td>{{a.resourceType}} {{a.resourceId||''}}</td><td>{{a.operatorName}}</td><td class="detail">{{a.detail||'—'}}</td><td>{{fmt(a.createdAt)}}</td></tr></tbody></table></div>

  <el-dialog v-model="userDialog" title="新增用户" width="520px"><el-form label-position="top"><el-form-item label="账号"><el-input v-model="userForm.username"/></el-form-item><el-form-item label="显示名称"><el-input v-model="userForm.displayName"/></el-form-item><el-form-item label="初始密码"><el-input v-model="userForm.password" type="password" show-password/></el-form-item><el-form-item label="角色"><el-select v-model="userForm.roleCode" style="width:100%"><el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.roleCode"/></el-select></el-form-item></el-form><template #footer><el-button @click="userDialog=false">取消</el-button><el-button type="primary" @click="createUser">创建</el-button></template></el-dialog>
  <el-dialog v-model="roleDialog" title="新增角色" width="480px"><el-form label-position="top"><el-form-item label="角色编码"><el-input v-model="roleForm.roleCode" placeholder="例如 DEVELOPER"/></el-form-item><el-form-item label="角色名称"><el-input v-model="roleForm.roleName" placeholder="例如 数据开发者"/></el-form-item></el-form><template #footer><el-button @click="roleDialog=false">取消</el-button><el-button type="primary" @click="createRole">创建</el-button></template></el-dialog>
  <el-dialog v-model="permissionDialog" :title="`配置权限 · ${permissionTarget?.name||''}`" width="760px"><div class="permission-note">VIEW 控制模块可见性，EDIT 控制新增、修改、发布等写操作。用户级配置保存后优先于角色权限。</div><table class="permission-table"><thead><tr><th>模块</th><th>查看</th><th>编辑</th></tr></thead><tbody><tr v-for="m in modules" :key="m[0]"><td>{{m[1]}}</td><td><el-checkbox v-model="selectedPermissions" :value="`${m[0]}_VIEW`"/></td><td><el-checkbox v-model="selectedPermissions" :value="`${m[0]}_EDIT`"/></td></tr></tbody></table><template #footer><el-button @click="permissionDialog=false">取消</el-button><el-button type="primary" @click="savePermissions">保存权限</el-button></template></el-dialog>
</div></template>

<style scoped>
.sep{margin:0 7px;color:#d0d5dd}.danger{color:#d92d20;cursor:pointer}.detail{max-width:420px;white-space:normal!important;word-break:break-word}.permission-note{margin-bottom:12px;padding:10px 12px;border:1px solid var(--ds-border);border-radius:7px;background:#f8fafc;color:#667085;font-size:12px}.permission-table{width:100%;border-collapse:collapse}.permission-table th,.permission-table td{height:42px;padding:0 12px;border-bottom:1px solid var(--ds-border-soft);text-align:left}.permission-table th{background:#f8fafc;color:#667085;font-size:11px}.permission-table td:first-child{font-weight:600;color:#344054}
</style>
