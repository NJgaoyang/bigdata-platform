<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, Delete, EditPen, Promotion } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import { accessApi, type AlertSetting, type AlertSettingPayload } from '../../api/access'
import { authApi, type CurrentUser } from '../../api/auth'
import { hasPermission } from '../../auth/permissions'
import { formatDateTime } from '../../utils/display'

const loading=ref(true),saving=ref(false),testingId=ref<number|null>(null),rows=ref<AlertSetting[]>([]),me=ref<CurrentUser|null>(null)
const drawer=ref(false),editing=ref<AlertSetting|null>(null)
const form=reactive<AlertSettingPayload>({name:'',channelType:'DINGTALK',triggerEvent:'FAILURE_ONLY',webhook:'',secret:'',keyword:'告警',customTemplate:'',enabled:true})
const canEdit=computed(()=>hasPermission(me.value,'SYSTEM_SETTINGS_EDIT'))
const placeholders=['{taskName}','{status}','{message}','{duration}','{rows}','{qps}','{time}']

async function load(){loading.value=true;try{[me.value,rows.value]=await Promise.all([authApi.me(),accessApi.alertSettings()])}catch(e){ElMessage.error(msg(e))}finally{loading.value=false}}
function reset(){Object.assign(form,{name:'',channelType:'DINGTALK',triggerEvent:'FAILURE_ONLY',webhook:'',secret:'',keyword:'告警',customTemplate:'',enabled:true})}
function openCreate(){editing.value=null;reset();drawer.value=true}
function openEdit(row:AlertSetting){editing.value=row;Object.assign(form,{name:row.name,channelType:'DINGTALK',triggerEvent:row.triggerEvent as AlertSettingPayload['triggerEvent'],webhook:'',secret:'',keyword:row.keyword||'',customTemplate:row.customTemplate||'',enabled:row.enabled});drawer.value=true}
async function save(){if(!form.name.trim())return ElMessage.warning('请输入配置名称');if(!editing.value&&!form.webhook.trim())return ElMessage.warning('请输入钉钉 Webhook 地址');saving.value=true;try{if(editing.value)await accessApi.updateAlertSetting(editing.value.id,{...form});else await accessApi.createAlertSetting({...form});ElMessage.success(editing.value?'告警配置已更新':'告警配置已创建');drawer.value=false;await load()}catch(e){ElMessage.error(msg(e))}finally{saving.value=false}}
async function toggle(row:AlertSetting){try{await accessApi.setAlertSettingEnabled(row.id,!row.enabled);ElMessage.success(!row.enabled?'已启用告警':'已停用告警');await load()}catch(e){ElMessage.error(msg(e))}}
async function test(row:AlertSetting){testingId.value=row.id;try{await accessApi.testAlertSetting(row.id);ElMessage.success('钉钉测试消息已发送')}catch(e){ElMessage.error(msg(e))}finally{testingId.value=null}}
async function remove(row:AlertSetting){try{await ElMessageBox.confirm(`删除告警配置“${row.name}”？`,'删除告警配置',{type:'warning'});await accessApi.deleteAlertSetting(row.id);ElMessage.success('告警配置已删除');await load()}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(msg(e))}}
function triggerLabel(v:string){return v==='ALL'?'全部事件':v==='SUCCESS_AND_FAILURE'?'成功 + 失败':'仅失败'}
function fmt(v?:string){return formatDateTime(v)}
function msg(e:unknown){return e instanceof Error?e.message:'操作失败'}
onMounted(load)
</script>

<template><div class="ds-page alert-settings-page">
  <PageHeader title="告警设置" subtitle="配置平台任务告警渠道。当前支持钉钉机器人，可按任务结果自动发送告警。">
    <template #actions><el-button v-if="canEdit" type="primary" @click="openCreate"><el-icon><Bell/></el-icon>新增告警配置</el-button></template>
  </PageHeader>

  <div class="alert-summary">
    <div><span>告警配置</span><strong>{{rows.length}}</strong></div>
    <div><span>已启用</span><strong>{{rows.filter(x=>x.enabled).length}}</strong></div>
    <div><span>告警渠道</span><strong>钉钉机器人</strong></div>
    <div><span>任务范围</span><strong>离线同步 / 实时同步</strong></div>
  </div>

  <div class="ds-card alert-card">
    <div class="alert-toolbar"><div><strong>告警配置</strong><p>任务状态落地后异步发送，不会因为钉钉不可用阻塞生产任务。</p></div><el-button @click="load" :loading="loading">刷新</el-button></div>
    <el-table :data="rows" v-loading="loading">
      <el-table-column label="配置名称" min-width="180"><template #default="s"><div class="name-cell"><span class="ding-icon">钉</span><div><strong>{{s.row.name}}</strong><small>{{s.row.channelType==='DINGTALK'?'钉钉机器人':s.row.channelType}}</small></div></div></template></el-table-column>
      <el-table-column label="触发事件" width="130"><template #default="s"><span class="trigger-chip">{{triggerLabel(s.row.triggerEvent)}}</span></template></el-table-column>
      <el-table-column label="Webhook" min-width="260"><template #default="s"><span class="mono masked" :title="s.row.webhookMasked">{{s.row.webhookMasked}}</span><div class="secret-tip">签名密钥：{{s.row.secretConfigured?'已配置':'未配置'}}</div></template></el-table-column>
      <el-table-column label="关键词" width="120"><template #default="s">{{s.row.keyword||'—'}}</template></el-table-column>
      <el-table-column label="状态" width="110"><template #default="s"><el-switch v-if="canEdit" :model-value="s.row.enabled" inline-prompt active-text="启用" inactive-text="停用" @change="toggle(s.row)"/><span v-else>{{s.row.enabled?'启用':'停用'}}</span></template></el-table-column>
      <el-table-column label="更新时间" width="170"><template #default="s">{{fmt(s.row.updatedAt||s.row.createdAt)}}</template></el-table-column>
      <el-table-column label="操作" width="210" fixed="right"><template #default="s"><div class="actions"><el-button link type="primary" :loading="testingId===s.row.id" @click="test(s.row)"><el-icon><Promotion/></el-icon>测试</el-button><el-button v-if="canEdit" link @click="openEdit(s.row)"><el-icon><EditPen/></el-icon>编辑</el-button><el-button v-if="canEdit" link type="danger" @click="remove(s.row)"><el-icon><Delete/></el-icon>删除</el-button></div></template></el-table-column>
    </el-table>
    <div v-if="!loading&&!rows.length" class="empty-alert"><el-icon><Bell/></el-icon><strong>还没有告警配置</strong><span>新增钉钉机器人后，平台可以在任务失败或成功时主动通知。</span><el-button v-if="canEdit" type="primary" plain @click="openCreate">新增告警配置</el-button></div>
  </div>

  <el-drawer v-model="drawer" size="600px" destroy-on-close :close-on-click-modal="false" class="alert-drawer">
    <template #header><div class="drawer-head"><h3>{{editing?'编辑告警配置':'新增告警配置'}}</h3><p>钉钉机器人支持 Webhook、加签和关键词三种安全校验方式。</p></div></template>
    <el-form label-position="top" class="alert-form">
      <el-form-item label="配置名称"><el-input v-model="form.name" placeholder="例如：生产环境钉钉告警"/></el-form-item>
      <div class="form-grid"><el-form-item label="渠道类型"><el-select v-model="form.channelType" disabled style="width:100%"><el-option label="钉钉 (DingTalk)" value="DINGTALK"/></el-select></el-form-item><el-form-item label="触发事件"><el-select v-model="form.triggerEvent" style="width:100%"><el-option label="仅失败" value="FAILURE_ONLY"/><el-option label="成功 + 失败" value="SUCCESS_AND_FAILURE"/><el-option label="全部事件" value="ALL"/></el-select></el-form-item></div>
      <el-form-item label="Webhook 地址"><el-input v-model="form.webhook" :placeholder="editing?`当前：${editing.webhookMasked}（留空保持不变）`:'https://oapi.dingtalk.com/robot/send?access_token=xxx'"/></el-form-item>
      <el-form-item><template #label><span>签名密钥 <em>可选，用于加签安全校验</em></span></template><el-input v-model="form.secret" type="password" show-password :placeholder="editing&&editing.secretConfigured?'已配置，留空保持不变':'SECxxx...'"/></el-form-item>
      <el-form-item><template #label><span>钉钉关键词 <em>可选，用于机器人关键词安全校验</em></span></template><el-input v-model="form.keyword" placeholder="告警"/></el-form-item>
      <el-form-item><template #label><span>自定义模板 <em>留空使用默认模板</em></span></template><el-input v-model="form.customTemplate" type="textarea" :rows="7" :placeholder="`支持占位符: ${placeholders.join(' ')}`"/><div class="template-help">可用占位符：<code v-for="p in placeholders" :key="p">{{p}}</code><br/>钉钉使用 Markdown 格式，可直接填写 Markdown 文本。</div></el-form-item>
      <el-form-item label="启用状态"><el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="停用"/></el-form-item>
    </el-form>
    <template #footer><div class="drawer-footer"><el-button @click="drawer=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></div></template>
  </el-drawer>
</div></template>

<style scoped>
.alert-summary{display:grid;grid-template-columns:repeat(4,1fr);margin-bottom:14px;border:1px solid var(--ds-border);border-radius:8px;background:#fff}.alert-summary>div{padding:14px 18px;border-right:1px solid var(--ds-border)}.alert-summary>div:last-child{border-right:0}.alert-summary span,.alert-summary strong{display:block}.alert-summary span{color:var(--ds-text-tertiary);font-size:11px}.alert-summary strong{margin-top:7px;color:var(--ds-text-primary);font-size:16px}.alert-card{overflow:hidden}.alert-toolbar{display:flex;align-items:center;justify-content:space-between;padding:14px 16px;border-bottom:1px solid var(--ds-border)}.alert-toolbar p{margin:4px 0 0;color:var(--ds-text-tertiary);font-size:11px}.name-cell{display:flex;align-items:center;gap:10px}.name-cell strong,.name-cell small{display:block}.name-cell small{margin-top:3px;color:var(--ds-text-tertiary);font-size:10px}.ding-icon{width:28px;height:28px;display:grid;place-items:center;border-radius:7px;background:#eaf3ff;color:#1677ff;font-weight:700}.trigger-chip{display:inline-flex;padding:3px 8px;border-radius:12px;background:#f2f4f7;color:#475467;font-size:11px}.mono{font-family:Consolas,Monaco,monospace}.masked{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#475467;font-size:11px}.secret-tip{margin-top:4px;color:var(--ds-text-tertiary);font-size:10px}.actions{display:flex;align-items:center;gap:4px}.empty-alert{padding:56px 20px;display:flex;flex-direction:column;align-items:center;gap:10px;color:var(--ds-text-tertiary)}.empty-alert>.el-icon{font-size:30px;color:#98a2b3}.empty-alert strong{color:var(--ds-text-primary)}.drawer-head h3{margin:0;font-size:18px}.drawer-head p{margin:5px 0 0;color:var(--ds-text-tertiary);font-size:11px}.alert-form{padding:4px 4px 20px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}.alert-form em{color:var(--ds-text-tertiary);font-size:11px;font-style:normal;font-weight:400}.template-help{margin-top:7px;color:var(--ds-text-tertiary);font-size:11px;line-height:1.8}.template-help code{margin-right:6px;color:#667085}.drawer-footer{display:flex;justify-content:flex-end;gap:8px;width:100%}:deep(.alert-drawer .el-drawer__body){background:#fafbfc}:deep(.alert-drawer .el-drawer__footer){border-top:1px solid var(--ds-border);background:#fff}@media(max-width:1100px){.alert-summary{grid-template-columns:repeat(2,1fr)}.alert-summary>div:nth-child(2){border-right:0}.form-grid{grid-template-columns:1fr}}
</style>
