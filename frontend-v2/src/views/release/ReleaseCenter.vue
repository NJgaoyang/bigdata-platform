<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { formatDateTime } from '../../utils/display'
import { releaseApi, type ReleasePolicy, type ReleaseRecord, type ReleaseRequestView } from '../../api/domain'
const route=useRoute();const loading=ref(true);const policy=ref<ReleasePolicy|null>(null),requests=ref<ReleaseRequestView[]>([]),records=ref<ReleaseRecord[]>([])
const mode=computed(()=>route.path.split('/').pop()||'history')
const title=computed(()=>mode.value==='history'?'发布记录':mode.value==='queue'?'审批队列':'发布策略')
const subtitle=computed(()=>mode.value==='history'?'查看所有进入生产环境的版本和结果。':mode.value==='queue'?'仅在开启人工审批后产生待审核发布。':'人工审批默认关闭；关闭后系统自动审批通过并保留完整发布记录。')
async function load(){loading.value=true;try{policy.value=await releaseApi.policy();if(mode.value==='history')records.value=await releaseApi.records();if(mode.value==='queue')requests.value=await releaseApi.requests('PENDING_APPROVAL')}catch(e){ElMessage.error(e instanceof Error?e.message:'发布中心加载失败')}finally{loading.value=false}}
async function toggle(v:boolean){try{policy.value=await releaseApi.updatePolicy(v);ElMessage.success(v?'人工审批已开启':'人工审批已关闭，后续发布自动审批通过')}catch(e){ElMessage.error(e instanceof Error?e.message:'更新失败')}}
async function approve(r:ReleaseRequestView){try{const comment=await ElMessageBox.prompt('可填写审批意见','审批通过',{confirmButtonText:'通过',cancelButtonText:'取消',inputType:'textarea'});await releaseApi.approve(r.id,comment.value||'');ElMessage.success('已审批并发布');await load()}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(e instanceof Error?e.message:'审批失败')}}
async function reject(r:ReleaseRequestView){try{const comment=await ElMessageBox.prompt('请输入驳回原因','驳回发布',{confirmButtonText:'驳回',cancelButtonText:'取消',inputValidator:v=>!!String(v||'').trim()||'请填写驳回原因'});await releaseApi.reject(r.id,comment.value||'');ElMessage.success('已驳回');await load()}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(e instanceof Error?e.message:'驳回失败')}}
function resourceTypeLabel(v:string){return ({WORKFLOW:'工作流',REALTIME:'实时同步',DEVELOPMENT:'数据开发',METRIC:'业务指标'} as Record<string,string>)[v]||v}
function fmt(v?:string){return formatDateTime(v)}
watch(()=>route.path,load);onMounted(load)
</script><template><div class="ds-page"><PageHeader :title="title" :subtitle="subtitle"/><el-skeleton v-if="loading" :rows="7" animated/>
<div v-else-if="mode==='history'" class="ds-card"><div class="ds-toolbar"><strong>生产发布记录</strong><div class="ds-spacer"/><span class="policy-tip">{{policy?.approvalRequired?'人工审批已开启':'自动审批'}}</span></div><div v-if="!records.length" class="ds-empty"><div><div class="ds-empty__title">暂无发布记录</div></div></div><table v-else class="ds-table"><thead><tr><th>资源</th><th>类型</th><th>版本</th><th>结果</th><th>发布人</th><th>发布时间</th><th>详情</th></tr></thead><tbody><tr v-for="r in records" :key="r.id"><td class="ds-resource">{{r.resourceName||`${r.resourceType} #${r.resourceId}`}}</td><td>{{resourceTypeLabel(r.resourceType)}}</td><td>{{r.releasedVersion?`V${r.releasedVersion}`:'—'}}</td><td><StatusBadge :status="r.resultStatus"/></td><td>{{r.operatorName}}</td><td>{{fmt(r.releasedAt)}}</td><td :title="r.detail">{{r.detail||'—'}}</td></tr></tbody></table></div>
<div v-else-if="mode==='queue'" class="ds-card"><div v-if="!policy?.approvalRequired" class="ds-empty"><div><div class="ds-empty__title">当前不需要人工审批</div><div>有发布权限的用户执行发布后，系统自动审批通过并直接创建生产版本。</div></div></div><div v-else-if="!requests.length" class="ds-empty"><div><div class="ds-empty__title">暂无待审核发布</div></div></div><table v-else class="ds-table"><thead><tr><th>资源</th><th>类型</th><th>目标版本</th><th>提交人</th><th>提交时间</th><th>操作</th></tr></thead><tbody><tr v-for="r in requests" :key="r.id"><td class="ds-resource">{{r.resourceName}}</td><td>{{resourceTypeLabel(r.resourceType)}}</td><td>{{r.requestedVersion?`V${r.requestedVersion}`:'—'}}</td><td>{{r.requestedBy}}</td><td>{{fmt(r.requestedAt)}}</td><td><span class="ds-link" @click="approve(r)">通过</span><span class="sep">·</span><span class="danger" @click="reject(r)">驳回</span></td></tr></tbody></table></div>
<div v-else class="ds-card policy-card"><div class="policy-row"><div><strong>需要人工审批</strong><p>默认关闭。关闭后，有发布权限的用户点击发布时系统自动审批通过并立即发布。</p></div><el-switch :model-value="policy?.approvalRequired||false" @change="(v:any)=>toggle(Boolean(v))"/></div><div class="audit-note">无论是否开启人工审批，生产发布都会记录资源、版本、发布人、时间和执行结果。关闭审批不等于关闭审计。</div></div>
</div></template><style scoped>
.sep{margin:0 7px;color:#c6d0dc}.danger{color:#d64545;cursor:pointer}.danger:hover{color:#b42318}.policy-tip{display:inline-flex;align-items:center;height:26px;padding:0 10px;border:1px solid #d5e3f7;border-radius:999px;background:#f3f8ff;color:#3568ad;font-size:11px;font-weight:600}.policy-card{max-width:860px;overflow:hidden}.policy-row{display:flex;align-items:center;justify-content:space-between;padding:24px 26px;background:#fff}.policy-row strong{font-size:15px;color:#17304f}.policy-row p{margin:7px 0 0;color:#718198;font-size:12px;line-height:1.7}.audit-note{margin:0 26px 26px;padding:13px 14px;border:1px solid #dce8f8;border-radius:10px;background:#f5f9ff;color:#60748d;font-size:12px;line-height:1.7}

/* DataSphere light product theme — visual overrides only */
.ds-page{padding-top:28px;padding-bottom:36px;background:linear-gradient(180deg,#fbfdff 0%,#f8fbff 100%)}
.ds-page>.ds-card{border:1px solid #e4ebf5;border-radius:16px;background:rgba(255,255,255,.98);box-shadow:0 10px 30px rgba(42,83,163,.04);overflow:hidden}
.ds-toolbar{min-height:58px;padding:0 18px;border-bottom:1px solid #edf2f7;background:#fff}.ds-toolbar strong{color:#17304f;font-size:13px;font-weight:700}
.ds-table{width:100%;border-collapse:collapse}.ds-table thead th{height:44px;padding:0 16px;background:#fbfcfe;border-bottom:1px solid #edf2f7;color:#78889f;font-size:11px;font-weight:650;text-align:left}.ds-table tbody td{height:52px;padding:0 16px;border-bottom:1px solid #f0f3f7;color:#52657c;font-size:12px}.ds-table tbody tr:hover{background:#f7faff}.ds-table tbody tr:last-child td{border-bottom:0}.ds-resource{color:#213b5a;font-weight:650}.ds-link{color:#2f6fed;font-weight:550;cursor:pointer}.ds-link:hover{color:#1f5dcc}
.ds-empty{min-height:250px;background:linear-gradient(180deg,#fff,#fbfdff);color:#8b99ad}.ds-empty__title{color:#36506d;font-weight:650}
.policy-card{border-color:#dfe8f3!important}.policy-card:before{content:'';display:block;height:3px;background:linear-gradient(90deg,#3b82f6,#93c5fd)}
.policy-row :deep(.el-switch){--el-switch-on-color:#3b82f6}.policy-row :deep(.el-switch__core){box-shadow:0 0 0 1px rgba(59,130,246,.08)}
:deep(.el-message-box){border-radius:14px}:deep(.el-message-box__header){padding-top:20px}:deep(.el-message-box__content){color:#60718a}:deep(.el-textarea__inner){border-radius:9px}
@media(max-width:1000px){.ds-page{padding-left:16px;padding-right:16px}.policy-card{max-width:none}}
</style>