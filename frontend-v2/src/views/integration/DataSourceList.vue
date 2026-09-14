<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { dataSourceApi, type DataSourcePayload, type DataSourceView } from '../../api/platform'

const loading = ref(true)
const error = ref('')
const rows = ref<DataSourceView[]>([])
const dialogVisible = ref(false)
const saving = ref(false)
const testingId = ref<number | null>(null)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<DataSourcePayload>({ name:'', type:'MYSQL', host:'', port:3306, databaseName:'', timezone:'Asia/Shanghai', username:'', password:'', metadataVisible:true })
const rules: FormRules<DataSourcePayload> = {
  name:[{ required:true, message:'请输入数据源名称', trigger:'blur' }],
  type:[{ required:true, message:'请选择类型', trigger:'change' }],
  host:[{ required:true, message:'请输入主机地址', trigger:'blur' }],
  port:[{ required:true, message:'请输入端口', trigger:'blur' }],
  username:[{ required:true, message:'请输入用户名', trigger:'blur' }]
}

async function load(){ loading.value=true; error.value=''; try{ rows.value=await dataSourceApi.list() }catch(e){ error.value=e instanceof Error?e.message:'数据源加载失败' }finally{ loading.value=false } }
function resetForm(){ editingId.value=null; Object.assign(form,{ name:'',type:'MYSQL',host:'',port:3306,databaseName:'',timezone:'Asia/Shanghai',username:'',password:'',metadataVisible:true }) }
function openCreate(){ resetForm(); dialogVisible.value=true }
function openEdit(row:DataSourceView){ editingId.value=row.id; Object.assign(form,{name:row.name,type:row.type,host:row.host,port:row.port,databaseName:row.databaseName||'',timezone:row.timezone||'Asia/Shanghai',username:row.username,password:'',metadataVisible:row.metadataVisible}); dialogVisible.value=true }
function onTypeChange(){ if(!editingId.value) form.port=form.type==='MYSQL'?3306:9030 }
async function save(){ if(!formRef.value || !(await formRef.value.validate().catch(()=>false))) return; saving.value=true; try{ if(editingId.value) await dataSourceApi.update(editingId.value,{...form}); else await dataSourceApi.create({...form}); ElMessage.success(editingId.value?'数据源已更新':'数据源已创建'); dialogVisible.value=false; await load() }catch(e){ ElMessage.error(e instanceof Error?e.message:'保存失败') }finally{ saving.value=false } }
async function test(row:DataSourceView){ testingId.value=row.id; try{ await dataSourceApi.test(row.id); ElMessage.success(`${row.name} 连接成功`); await load() }catch(e){ ElMessage.error(e instanceof Error?e.message:'连接测试失败') }finally{ testingId.value=null } }
async function remove(row:DataSourceView){ try{ await ElMessageBox.confirm(`确认删除数据源“${row.name}”？此操作不会删除源端或 StarRocks 中的数据。`,'删除数据源',{type:'warning',confirmButtonText:'删除',cancelButtonText:'取消'}); await dataSourceApi.remove(row.id); ElMessage.success('已删除'); await load() }catch(e){ if(e!=='cancel' && e!=='close') ElMessage.error(e instanceof Error?e.message:'删除失败') } }
async function setMetadataVisible(row:DataSourceView, visible:boolean){ try{ await dataSourceApi.setMetadataVisible(row.id, visible); row.metadataVisible=visible; ElMessage.success(visible?'已开启元数据展示':'已关闭元数据展示') }catch(e){ row.metadataVisible=!visible; ElMessage.error(e instanceof Error?e.message:'设置失败') } }
function address(row:DataSourceView){ return `${row.host}:${row.port}${row.databaseName?` / ${row.databaseName}`:''}` }
onMounted(load)
</script>

<template>
  <div class="ds-page">
    <PageHeader title="数据源设置" subtitle="集中维护平台允许使用的 MySQL 与 StarRocks 连接，并控制是否在元数据中展示。">
      <template #actions><el-button type="primary" @click="openCreate">+ 新建数据源</el-button></template>
    </PageHeader>
    <div v-if="error" class="ds-error">{{ error }} <span class="ds-link" @click="load">重新加载</span></div>
    <div class="ds-card source-card">
      <div class="ds-toolbar"><div><div class="ds-card__title">数据源</div><div class="ds-card__sub">密码仅在保存时提交，页面不回显明文。</div></div><div class="ds-spacer"/><el-button @click="load" :loading="loading">刷新</el-button></div>
      <el-skeleton v-if="loading" :rows="5" animated class="source-skeleton" />
      <div v-else-if="!rows.length" class="ds-empty"><div><div class="ds-empty__title">暂无数据源</div><div>创建 MySQL 或 StarRocks 连接后即可开始使用。</div></div></div>
      <table v-else class="ds-table">
        <thead><tr><th style="width:15%">名称</th><th style="width:9%">类型</th><th style="width:25%">地址</th><th style="width:12%">用途</th><th style="width:12%">状态</th><th style="width:13%">元数据展示</th><th>操作</th></tr></thead>
        <tbody><tr v-for="row in rows" :key="row.id"><td class="ds-resource">{{ row.name }}</td><td>{{ row.type }}</td><td>{{ address(row) }}</td><td>{{ row.type==='MYSQL'?'同步源':'同步目标 / 查询' }}</td><td><StatusBadge :status="row.status" /></td><td><el-switch :model-value="row.metadataVisible" @change="(value:any)=>setMetadataVisible(row, Boolean(value))" /></td><td><span class="ds-link" @click="test(row)">{{ testingId===row.id?'测试中...':'测试' }}</span><span class="op-sep">·</span><span class="ds-link" @click="openEdit(row)">编辑</span><span class="op-sep">·</span><span class="danger-link" @click="remove(row)">删除</span></td></tr></tbody>
      </table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId?'编辑数据源':'新建数据源'" width="560px" destroy-on-close @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="数据源名称" prop="name"><el-input v-model="form.name" placeholder="例如 mysql-prod" /></el-form-item>
          <el-form-item label="类型" prop="type"><el-select v-model="form.type" style="width:100%" @change="onTypeChange"><el-option label="MySQL" value="MYSQL"/><el-option label="StarRocks" value="STARROCKS"/></el-select></el-form-item>
          <el-form-item label="主机地址" prop="host"><el-input v-model="form.host" placeholder="IP 或域名" /></el-form-item>
          <el-form-item label="端口" prop="port"><el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" style="width:100%" /></el-form-item>
          <el-form-item label="数据库"><el-input v-model="form.databaseName" placeholder="可选" /></el-form-item>
          <el-form-item label="时区"><el-input v-model="form.timezone" placeholder="Asia/Shanghai" /></el-form-item>
          <el-form-item label="用户名" prop="username"><el-input v-model="form.username" /></el-form-item>
        </div>
        <el-form-item :label="editingId?'密码（留空表示不修改）':'密码'"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item>
        <el-form-item label="元数据展示"><el-switch v-model="form.metadataVisible" active-text="开启" inactive-text="关闭" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.source-card { overflow:hidden; }
.source-skeleton { padding:16px; }
.op-sep { margin:0 7px; color:#d0d5dd; }
.danger-link { color:#d92d20; cursor:pointer; }
.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:0 14px; }
</style>
