<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { platformApi, type DataSource } from '../api'

const saved = ref(false)
const sources = ref<DataSource[]>([])
const userCount = ref(0)
const roleCount = ref(0)
const auditCount = ref(0)
const showSource = ref(false)
const editingSourceId = ref<number | null>(null)
const sourceForm = ref({ name: 'starrocks_prod', type: 'STARROCKS', host: '81.69.15.136', port: 9030, databaseName: 'analytics', username: 'root', password: '' })
const sourceModalTitle = computed(() => editingSourceId.value ? '编辑数据源' : '新增数据源')
const accessVisible = ref(false)
const accessTab = ref('users')
const users = ref<Record<string, unknown>[]>([])
const roles = ref<Record<string, unknown>[]>([])
const audits = ref<Record<string, unknown>[]>([])
const channels = ref<Record<string, unknown>[]>([])
const operationLogs = ref<string[]>([])
onMounted(async () => {
  try { sources.value = (await platformApi.dataSources()).data.data || [] } catch { /* prototype remains usable if API is unavailable */ }
  try { userCount.value = ((await platformApi.systemUsers()).data.data || []).length } catch { /* use zero in offline mode */ }
  try { roleCount.value = ((await platformApi.systemRoles()).data.data || []).length } catch { /* use zero in offline mode */ }
  try { auditCount.value = ((await platformApi.systemAuditLogs()).data.data || []).length } catch { /* use zero in offline mode */ }
})
function save() { saved.value = true; ElMessage.success('配置已保存') }
function openCreateSource() {
  editingSourceId.value = null
  sourceForm.value = { name: 'starrocks_prod', type: 'STARROCKS', host: '81.69.15.136', port: 9030, databaseName: 'analytics', username: 'root', password: '' }
  showSource.value = true
}
function openEditSource(source: DataSource) {
  editingSourceId.value = source.id
  sourceForm.value = { name: source.name, type: source.type, host: source.host, port: source.port, databaseName: source.databaseName, username: source.username, password: '' }
  showSource.value = true
}
async function createSource() {
  try {
    const result = editingSourceId.value
      ? await platformApi.updateDataSource(editingSourceId.value, sourceForm.value)
      : await platformApi.createDataSource(sourceForm.value)
    if (editingSourceId.value) sources.value = sources.value.map(item => item.id === editingSourceId.value ? result.data.data : item)
    else sources.value.unshift(result.data.data)
    showSource.value = false
    ElMessage.success(editingSourceId.value ? '数据源已更新' : '数据源已创建')
  } catch (error: any) { ElMessage.error(error?.response?.data?.message || '数据源保存失败') }
}
async function deleteSource(source: DataSource) {
  try {
    await platformApi.deleteDataSource(source.id)
    sources.value = sources.value.filter(item => item.id !== source.id)
    ElMessage.success('数据源已删除')
  } catch { ElMessage.error('数据源删除失败') }
}
async function testSource(source: DataSource) {
  try {
    const result = await platformApi.testDataSource(source.id)
    result.data.data.success ? ElMessage.success(result.data.data.message) : ElMessage.warning(result.data.data.message)
  } catch { ElMessage.error('数据源连接测试失败') }
}
async function openAccess() {
  const results = await Promise.allSettled([platformApi.systemUsers(), platformApi.systemRoles(), platformApi.systemAuditLogs(), platformApi.systemAlertChannels(), platformApi.systemOperationLogs()])
  users.value = results[0].status === 'fulfilled' ? results[0].value.data.data || [] : []
  roles.value = results[1].status === 'fulfilled' ? results[1].value.data.data || [] : []
  audits.value = results[2].status === 'fulfilled' ? results[2].value.data.data || [] : []
  channels.value = results[3].status === 'fulfilled' ? results[3].value.data.data || [] : []
  operationLogs.value = results[4].status === 'fulfilled' ? results[4].value.data.data || [] : []
  accessVisible.value = true
}
</script>

<template>
  <section class="page">
    <div class="module-bar"><div class="module-title">系统配置 <span class="crumb">/ 平台设置</span></div><div class="module-actions"><button class="btn-primary" @click="save">保存配置</button></div></div>
    <div class="page-body settings-body">
      <div class="settings-grid">
        <div class="card"><div class="card-head"><span>基础配置</span></div><div class="settings-form"><div class="form-item"><label>平台名称</label><input value="BigData Dev Platform"></div><div class="form-item"><label>默认项目</label><select><option>数仓开发项目</option><option>用户增长项目</option></select></div><div class="form-item"><label>时区</label><select><option>Asia/Shanghai (UTC+8)</option></select></div><div class="form-item"><label>审计保留天数</label><input value="180"></div></div></div>
        <div class="card"><div class="card-head"><span>功能开关</span><span class="muted">安全默认值</span></div><div class="switch-list"><div class="switch-row"><div><strong>实时同步</strong><p>第一期暂不开放实时链路</p></div><span class="switch off">OFF</span></div><div class="switch-row"><div><strong>SQL 危险操作拦截</strong><p>DROP / TRUNCATE / 无条件更新删除</p></div><span class="switch on">ON</span></div><div class="switch-row"><div><strong>自动血缘解析</strong><p>保存 SQL 时自动更新表级血缘</p></div><span class="switch on">ON</span></div></div></div>
      </div>
      <div class="card source-card"><div class="card-head"><span>数据源连接</span><button class="btn-primary" @click="openCreateSource">＋ 新增数据源</button></div><div v-if="sources.length" class="source-list"><div v-for="source in sources" :key="source.id" class="source-row"><div><strong>{{ source.name }}</strong><p>{{ source.type }} · {{ source.host }}:{{ source.port }}/{{ source.databaseName }}</p></div><div class="source-actions"><span class="switch on">{{ source.status }}</span><button class="link-action" @click="testSource(source)">测试连接</button><button class="link-action" @click="openEditSource(source)">编辑</button><button class="link-action danger-text" @click="deleteSource(source)">删除</button></div></div></div><div v-else class="source-empty">尚未配置数据源，可新增 MySQL 或 StarRocks 连接。</div></div>
      <div class="card"><div class="card-head"><span>外部服务连接</span></div><div class="settings-form service-form"><div class="form-item"><label>StarRocks</label><input value="3.3.22 · 81.69.15.136:9030" readonly></div><div class="form-item"><label>SeaTunnel</label><input value="2.3.12 · /data/software/seatunnel" readonly></div><div class="form-item"><label>DolphinScheduler</label><input value="3.1.9 · /data/software/dolphinscheduler · admin" readonly></div></div><div class="service-note">外部服务已按最终验收阶段启用；平台通过后端网关访问，不由前端直连。</div></div>
      <div class="card access-card"><div class="card-head"><span>权限与审计</span><span class="muted">Phase 10</span></div><div class="access-summary"><div><strong>{{ userCount }}</strong><span>用户</span></div><div><strong>{{ roleCount }}</strong><span>角色</span></div><div><strong>{{ auditCount }}</strong><span>审计记录</span></div><button class="btn-default" @click="openAccess">管理权限</button></div></div>
      <div v-if="saved" class="save-note">✓ 已保存本地开发配置</div>
    </div>
    <div v-if="showSource" class="modal-mask" @click.self="showSource = false"><div class="modal"><div class="modal-head"><span>{{ sourceModalTitle }}</span><button class="modal-close" @click="showSource = false">×</button></div><div class="modal-body"><div class="form-item"><label>名称</label><input v-model="sourceForm.name"></div><div class="form-row"><div class="form-item"><label>类型</label><select v-model="sourceForm.type"><option>STARROCKS</option><option>MYSQL</option></select></div><div class="form-item"><label>端口</label><input v-model.number="sourceForm.port" type="number"></div></div><div class="form-item"><label>主机</label><input v-model="sourceForm.host"></div><div class="form-item"><label>数据库</label><input v-model="sourceForm.databaseName"></div><div class="form-row"><div class="form-item"><label>用户名</label><input v-model="sourceForm.username"></div><div class="form-item"><label>密码</label><input v-model="sourceForm.password" type="password" placeholder="编辑时留空表示保留原密码"></div></div></div><div class="modal-foot"><button class="btn-default" @click="showSource = false">取消</button><button class="btn-primary" @click="createSource">保存</button></div></div></div>
    <el-dialog v-model="accessVisible" title="权限与审计" width="min(920px, 92vw)"><div class="access-tabs"><button :class="{active: accessTab === 'users'}" @click="accessTab = 'users'">用户（{{ users.length }}）</button><button :class="{active: accessTab === 'roles'}" @click="accessTab = 'roles'">角色（{{ roles.length }}）</button><button :class="{active: accessTab === 'audits'}" @click="accessTab = 'audits'">审计（{{ audits.length }}）</button><button :class="{active: accessTab === 'channels'}" @click="accessTab = 'channels'">告警渠道（{{ channels.length }}）</button><button :class="{active: accessTab === 'operations'}" @click="accessTab = 'operations'">操作日志（{{ operationLogs.length }}）</button></div><div v-if="accessTab === 'users'" class="access-table"><table class="data-table"><thead><tr><th>ID</th><th>用户名</th><th>显示名</th><th>状态</th></tr></thead><tbody><tr v-for="item in users" :key="String(item.id)"><td>{{ item.id }}</td><td>{{ item.username }}</td><td>{{ item.displayName }}</td><td>{{ item.status }}</td></tr><tr v-if="!users.length"><td colspan="4" class="empty-state">暂无用户</td></tr></tbody></table></div><div v-else-if="accessTab === 'roles'" class="access-table"><table class="data-table"><thead><tr><th>编码</th><th>名称</th><th>权限</th></tr></thead><tbody><tr v-for="item in roles" :key="String(item.id)"><td>{{ item.roleCode }}</td><td>{{ item.roleName }}</td><td>{{ Array.isArray(item.permissions) ? item.permissions.join('、') : '—' }}</td></tr><tr v-if="!roles.length"><td colspan="3" class="empty-state">暂无角色</td></tr></tbody></table></div><div v-else-if="accessTab === 'audits'" class="access-table"><table class="data-table"><thead><tr><th>时间</th><th>动作</th><th>资源</th><th>详情</th></tr></thead><tbody><tr v-for="item in audits" :key="String(item.id)"><td>{{ item.createdAt || '—' }}</td><td>{{ item.action }}</td><td>{{ item.resourceType }} / {{ item.resourceId }}</td><td>{{ item.detail }}</td></tr><tr v-if="!audits.length"><td colspan="4" class="empty-state">暂无审计记录</td></tr></tbody></table></div><div v-else-if="accessTab === 'channels'" class="access-table"><table class="data-table"><thead><tr><th>名称</th><th>类型</th><th>启用</th></tr></thead><tbody><tr v-for="item in channels" :key="String(item.id)"><td>{{ item.name }}</td><td>{{ item.channelType }}</td><td>{{ item.enabled ? '是' : '否' }}</td></tr><tr v-if="!channels.length"><td colspan="3" class="empty-state">暂无告警渠道</td></tr></tbody></table></div><div v-else class="access-table operation-list"><pre>{{ operationLogs.join('\n') || '暂无操作日志' }}</pre></div></el-dialog>
  </section>
</template>

<style scoped>
.access-tabs { display:flex; gap:6px; border-bottom:1px solid var(--line); margin-bottom:12px; }
.access-tabs button { border:0; background:transparent; padding:8px 10px; color:#667085; cursor:pointer; }
.access-tabs button.active { color:#1677ff; border-bottom:2px solid #1677ff; }
.access-table { max-height:52vh; overflow:auto; }
.operation-list { background:#101722; color:#d7e4f5; border-radius:6px; padding:14px; }
.operation-list pre { margin:0; white-space:pre-wrap; word-break:break-word; font:12px/1.6 Consolas,Monaco,monospace; }
.danger-text { color:#d64545; }
</style>
