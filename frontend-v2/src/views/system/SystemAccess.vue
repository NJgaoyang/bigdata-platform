<script setup lang="ts">
import { computed,onMounted,reactive,ref,watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage,ElMessageBox } from 'element-plus'
import { ArrowDown, Delete, EditPen, Key, SwitchButton, View } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import { formatDateTime } from '../../utils/display'
import { accessApi, type AuditLog, type PlatformRole, type PlatformUser } from '../../api/access'
import { authApi, type CurrentUser } from '../../api/auth'
import { hasPermission } from '../../auth/permissions'

const route=useRoute(), loading=ref(true), me=ref<CurrentUser|null>(null)
const users=ref<PlatformUser[]>([]), roles=ref<PlatformRole[]>([]), audits=ref<AuditLog[]>([])
const userDialog=ref(false), roleDialog=ref(false), permissionDialog=ref(false), editingUser=ref<PlatformUser|null>(null)
const detailVisible=ref(false), detailUser=ref<PlatformUser|null>(null), resetDialog=ref(false), resetTarget=ref<PlatformUser|null>(null)
const resetForm=reactive({password:'',confirmPassword:''})
const userForm=reactive({username:'',displayName:'',phone:'',password:'',confirmPassword:'',roleCode:'USER',status:'ACTIVE'})
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
function openUser(){editingUser.value=null;Object.assign(userForm,{username:'',displayName:'',password:'',roleCode:roles.value.find(r=>r.roleCode==='USER')?.roleCode||roles.value[0]?.roleCode||'USER',status:'ACTIVE'});userDialog.value=true}
function openEditUser(u:PlatformUser){editingUser.value=u;Object.assign(userForm,{username:u.username,displayName:u.displayName,phone:u.phone||'',password:'',confirmPassword:'',roleCode:u.roleCode==='ADMIN'?'ADMIN':'USER',status:u.status});userDialog.value=true}
async function saveUser(){
  const username=userForm.username.trim(),displayName=userForm.displayName.trim(),phone=userForm.phone.trim()
  if(!username||!displayName)return ElMessage.warning('请填写账号和真实姓名')
  if(!editingUser.value&&!userForm.password)return ElMessage.warning('请输入初始密码')
  if(userForm.password!==userForm.confirmPassword)return ElMessage.warning('两次输入的密码不一致')
  if(phone&&!/^\+?[0-9 -]{6,20}$/.test(phone))return ElMessage.warning('手机号格式不正确')
  const payload={username,displayName,phone,password:userForm.password,roleCode:userForm.roleCode,status:userForm.status}
  try{if(editingUser.value){await accessApi.updateUser(editingUser.value.id,payload);ElMessage.success('用户已更新')}else{await accessApi.createUser(payload);ElMessage.success('用户已创建')}userDialog.value=false;editingUser.value=null;await load()}catch(e){ElMessage.error(msg(e))}
}
function openRole(){Object.assign(roleForm,{roleCode:'',roleName:''});roleDialog.value=true}
async function createRole(){try{await accessApi.createRole({roleCode:roleForm.roleCode.trim().toUpperCase(),roleName:roleForm.roleName.trim()});ElMessage.success('角色已创建');roleDialog.value=false;await load()}catch(e){ElMessage.error(msg(e))}}
async function toggle(u:PlatformUser){try{await accessApi.setUserStatus(u.id,u.status!=='ACTIVE');await load()}catch(e){ElMessage.error(msg(e))}}
async function remove(u:PlatformUser){try{await ElMessageBox.confirm(`删除用户“${u.displayName}”？`,'删除用户',{type:'warning'});await accessApi.removeUser(u.id);await load()}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(msg(e))}}
function openDetail(u:PlatformUser){detailUser.value=u;detailVisible.value=true}
function openResetPassword(u:PlatformUser){resetTarget.value=u;Object.assign(resetForm,{password:'',confirmPassword:''});resetDialog.value=true}
async function resetPassword(){
  if(!resetTarget.value)return
  if(resetForm.password.length<6)return ElMessage.warning('新密码至少需要 6 位')
  if(resetForm.password!==resetForm.confirmPassword)return ElMessage.warning('两次输入的密码不一致')
  try{await accessApi.resetUserPassword(resetTarget.value.id,resetForm.password);ElMessage.success('密码已重置，用户现有登录会话已失效');resetDialog.value=false}catch(e){ElMessage.error(msg(e))}
}
async function forceLogout(u:PlatformUser){
  try{await ElMessageBox.confirm(`确认强制下线“${u.displayName}”？该用户当前登录会话将立即失效。`,'强制下线',{type:'warning',confirmButtonText:'强制下线',cancelButtonText:'取消'});await accessApi.forceLogoutUser(u.id);ElMessage.success('用户已强制下线')}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(msg(e))}
}
function handleUserMore(command:string,u:PlatformUser){if(command==='reset')openResetPassword(u);if(command==='logout')void forceLogout(u);if(command==='delete')void remove(u)}
async function editUserPermissions(u:PlatformUser){try{selectedPermissions.value=await accessApi.userPermissions(u.id);permissionTarget.value={kind:'user',id:u.id,name:u.displayName};permissionDialog.value=true}catch(e){ElMessage.error(msg(e))}}
function editRolePermissions(r:PlatformRole){selectedPermissions.value=(r.permissions||[]).filter(p=>/_VIEW$|_EDIT$/.test(p));permissionTarget.value={kind:'role',id:r.id,name:r.roleName};permissionDialog.value=true}
function normalizePermissions(){const set=new Set(selectedPermissions.value);for(const [key] of modules)if(set.has(`${key}_EDIT`))set.add(`${key}_VIEW`);return [...set]}
async function savePermissions(){if(!permissionTarget.value)return;try{const values=normalizePermissions();if(permissionTarget.value.kind==='user')await accessApi.setUserPermissions(permissionTarget.value.id,values);else await accessApi.setRolePermissions(permissionTarget.value.id,values);ElMessage.success('权限已更新');permissionDialog.value=false;await load()}catch(e){ElMessage.error(msg(e))}}
function userRoleName(roleCode:string){return roleCode==='ADMIN'?'管理员':'普通用户'}
function userStatusName(status:string){return status==='ACTIVE'?'启用':'禁用'}
function rolePermissionSummary(r:PlatformRole){const values=(r.permissions||[]).filter(x=>/_VIEW$|_EDIT$/.test(x));return values.length?`${values.length} 项权限`:'未配置（用户使用默认查看权限）'}
function fmt(v?:string){return formatDateTime(v)}
function msg(e:unknown){return e instanceof Error?e.message:'操作失败'}
watch(()=>route.path,load);onMounted(load)
</script>

<template><div class="ds-page">
  <PageHeader :title="mode==='users'?'用户管理':mode==='roles'?'角色权限':'审计日志'" :subtitle="mode==='users'?'管理平台账号及用户级模块权限。':mode==='roles'?'角色权限用于未单独配置权限的用户。':'查看关键操作和生产变更记录。'">
    <template #actions><el-button v-if="canEdit&&mode==='users'" type="primary" @click="openUser">+ 新增用户</el-button><el-button v-if="canEdit&&mode==='roles'" type="primary" @click="openRole">+ 新增角色</el-button></template>
  </PageHeader>
  <el-skeleton v-if="loading" :rows="7" animated/>
  <div v-else-if="mode==='users'" class="ds-card user-table-card"><table class="ds-table user-table"><colgroup><col class="col-real-name"/><col class="col-account"/><col class="col-phone"/><col class="col-role"/><col class="col-status"/><col class="col-created"/><col class="col-actions"/></colgroup><thead><tr><th>真实姓名</th><th>账号</th><th>手机号</th><th>角色</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead><tbody><tr v-for="u in users" :key="u.id"><td class="ds-resource">{{u.displayName}}</td><td>{{u.username}}</td><td>{{u.phone||'—'}}</td><td>{{userRoleName(u.roleCode)}}</td><td><el-switch :model-value="u.status==='ACTIVE'" :disabled="!canEdit" @change="toggle(u)"/></td><td>{{fmt(u.createdAt)}}</td><td class="user-actions-cell"><div class="user-row-actions" @click.stop><el-button class="user-inline-action" text @click="openDetail(u)"><el-icon><View /></el-icon>详情</el-button><el-button v-if="canEdit" class="user-inline-action" text @click="openEditUser(u)"><el-icon><EditPen /></el-icon>编辑</el-button><el-dropdown v-if="canEdit" trigger="click" placement="bottom-end" popper-class="user-action-popper" @command="(cmd:string)=>handleUserMore(cmd,u)"><el-button class="user-inline-action user-more-action" text>更多<el-icon class="more-arrow"><ArrowDown /></el-icon></el-button><template #dropdown><el-dropdown-menu class="user-action-menu"><el-dropdown-item command="reset"><el-icon><Key /></el-icon><span>重置密码</span></el-dropdown-item><el-dropdown-item command="logout"><el-icon><SwitchButton /></el-icon><span>强制下线</span></el-dropdown-item><el-dropdown-item command="delete" divided class="user-delete-item"><el-icon><Delete /></el-icon><span>删除</span></el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></td></tr></tbody></table></div>
  <div v-else-if="mode==='roles'" class="ds-card"><table class="ds-table"><thead><tr><th>角色</th><th>编码</th><th>权限</th><th>操作</th></tr></thead><tbody><tr v-for="r in roles" :key="r.id"><td class="ds-resource">{{r.roleName}}</td><td>{{r.roleCode}}</td><td>{{rolePermissionSummary(r)}}</td><td><span v-if="canEdit&&r.roleCode!=='ADMIN'" class="ds-link" @click="editRolePermissions(r)">配置权限</span><span v-else>—</span></td></tr></tbody></table></div>
  <div v-else class="ds-card"><table class="ds-table"><thead><tr><th>动作</th><th>资源</th><th>操作人</th><th>详情</th><th>时间</th></tr></thead><tbody><tr v-for="a in audits" :key="a.id"><td>{{a.action}}</td><td>{{a.resourceType}} {{a.resourceId||''}}</td><td>{{a.operatorName}}</td><td class="detail">{{a.detail||'—'}}</td><td>{{fmt(a.createdAt)}}</td></tr></tbody></table></div>

  <el-dialog v-model="userDialog" :title="editingUser?'编辑用户':'新增用户'" width="680px"><el-form label-position="top" class="user-form-grid"><el-form-item label="账号"><el-input v-model="userForm.username" placeholder="请输入登录账号"/></el-form-item><el-form-item label="真实姓名"><el-input v-model="userForm.displayName" placeholder="请输入真实姓名"/></el-form-item><el-form-item v-if="!editingUser" label="初始密码"><el-input v-model="userForm.password" type="password" show-password placeholder="请输入初始密码"/></el-form-item><el-form-item v-if="!editingUser" label="确认密码"><el-input v-model="userForm.confirmPassword" type="password" show-password placeholder="请再次输入密码"/></el-form-item><el-form-item label="手机号"><el-input v-model="userForm.phone" placeholder="请输入手机号"/></el-form-item><el-form-item label="角色"><el-select v-model="userForm.roleCode" style="width:100%"><el-option label="普通用户" value="USER"/><el-option label="管理员" value="ADMIN"/></el-select></el-form-item><el-form-item label="状态" class="status-form-item"><div class="status-switch"><el-switch v-model="userForm.status" active-value="ACTIVE" inactive-value="DISABLED"/><span>{{userForm.status==='ACTIVE'?'启用':'禁用'}}</span></div></el-form-item></el-form><template #footer><el-button @click="userDialog=false">取消</el-button><el-button type="primary" @click="saveUser">{{editingUser?'保存':'创建'}}</el-button></template></el-dialog>
  <el-drawer v-model="detailVisible" title="用户详情" size="430px"><el-descriptions v-if="detailUser" :column="1" border><el-descriptions-item label="真实姓名">{{detailUser.displayName}}</el-descriptions-item><el-descriptions-item label="账号">{{detailUser.username}}</el-descriptions-item><el-descriptions-item label="手机号">{{detailUser.phone||'—'}}</el-descriptions-item><el-descriptions-item label="角色">{{userRoleName(detailUser.roleCode)}}</el-descriptions-item><el-descriptions-item label="状态"><el-tag :type="detailUser.status==='ACTIVE'?'success':'info'">{{userStatusName(detailUser.status)}}</el-tag></el-descriptions-item><el-descriptions-item label="创建时间">{{fmt(detailUser.createdAt)}}</el-descriptions-item></el-descriptions></el-drawer>
  <el-dialog v-model="resetDialog" :title="`重置密码 · ${resetTarget?.displayName||''}`" width="460px"><el-form label-position="top"><el-form-item label="新密码"><el-input v-model="resetForm.password" type="password" show-password placeholder="至少 6 位"/></el-form-item><el-form-item label="确认密码"><el-input v-model="resetForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码"/></el-form-item></el-form><template #footer><el-button @click="resetDialog=false">取消</el-button><el-button type="primary" @click="resetPassword">确认重置</el-button></template></el-dialog>
  <el-dialog v-model="roleDialog" title="新增角色" width="480px"><el-form label-position="top"><el-form-item label="角色编码"><el-input v-model="roleForm.roleCode" placeholder="例如 DEVELOPER"/></el-form-item><el-form-item label="角色名称"><el-input v-model="roleForm.roleName" placeholder="例如 数据开发者"/></el-form-item></el-form><template #footer><el-button @click="roleDialog=false">取消</el-button><el-button type="primary" @click="createRole">创建</el-button></template></el-dialog>
  <el-dialog v-model="permissionDialog" :title="`配置权限 · ${permissionTarget?.name||''}`" width="760px"><div class="permission-note">VIEW 控制模块可见性，EDIT 控制新增、修改、发布等写操作。用户级配置保存后优先于角色权限。</div><table class="permission-table"><thead><tr><th>模块</th><th>查看</th><th>编辑</th></tr></thead><tbody><tr v-for="m in modules" :key="m[0]"><td>{{m[1]}}</td><td><el-checkbox v-model="selectedPermissions" :value="`${m[0]}_VIEW`"/></td><td><el-checkbox v-model="selectedPermissions" :value="`${m[0]}_EDIT`"/></td></tr></tbody></table><template #footer><el-button @click="permissionDialog=false">取消</el-button><el-button type="primary" @click="savePermissions">保存权限</el-button></template></el-dialog>
</div></template>

<style scoped>
.user-table-card{overflow:visible}.user-table .col-real-name{width:15%}.user-table .col-account{width:14%}.user-table .col-phone{width:15%}.user-table .col-role{width:11%}.user-table .col-status{width:10%}.user-table .col-created{width:17%}.user-table .col-actions{width:250px}.user-table th:last-child,.user-table td.user-actions-cell{overflow:visible!important;text-overflow:clip!important}.user-actions-cell{padding-right:10px!important}.user-row-actions{min-width:220px;justify-content:flex-start}.sep{margin:0 7px;color:#d0d5dd}.danger{color:#d92d20;cursor:pointer}.detail{max-width:420px;white-space:normal!important;word-break:break-word}.user-row-actions{display:flex;align-items:center;gap:2px;white-space:nowrap}.user-inline-action{height:34px!important;padding:0 8px!important;color:#52637a!important;font-weight:500!important}.user-inline-action .el-icon{margin-right:4px;font-size:16px}.user-inline-action:hover{background:#f5f6f8!important;color:#344054!important}.user-more-action{gap:2px}.more-arrow{margin-left:2px!important;margin-right:0!important;font-size:13px!important}:global(.user-action-popper.el-popper){border:0!important;border-radius:16px!important;box-shadow:0 12px 30px rgba(16,24,40,.16)!important;overflow:hidden}:global(.user-action-popper .el-popper__arrow){display:none}:global(.user-action-popper .el-dropdown-menu){min-width:168px;padding:8px!important;border-radius:16px!important}:global(.user-action-popper .el-dropdown-menu__item){height:44px;padding:0 16px!important;gap:10px;border-radius:7px;font-size:14px;color:#202124}:global(.user-action-popper .el-dropdown-menu__item .el-icon){font-size:17px;color:#667085}:global(.user-action-popper .el-dropdown-menu__item:not(.is-disabled):hover){background:#f5f6f8;color:#202124}:global(.user-action-popper .el-dropdown-menu__item--divided){margin-top:7px!important;border-top:1px solid #ebeef2!important}:global(.user-action-popper .user-delete-item){color:#f04438}:global(.user-action-popper .user-delete-item .el-icon){color:#f04438}.user-form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}.user-form-grid .status-form-item{grid-column:1/-1}.status-switch{height:32px;display:flex;align-items:center;gap:10px;color:#667085}.permission-note{margin-bottom:12px;padding:10px 12px;border:1px solid var(--ds-border);border-radius:7px;background:#f8fafc;color:#667085;font-size:12px}.permission-table{width:100%;border-collapse:collapse}.permission-table th,.permission-table td{height:42px;padding:0 12px;border-bottom:1px solid var(--ds-border-soft);text-align:left}.permission-table th{background:#f8fafc;color:#667085;font-size:11px}.permission-table td:first-child{font-weight:600;color:#344054}
</style>
