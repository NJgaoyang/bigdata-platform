export function formatDateTime(value?: string | null) {
  if (!value) return '—'
  return String(value).replace('T', ' ').replace(/\.\d+(?=$|[+-])/,'').replace(/Z$/,'').slice(0, 19)
}

export function statusLabel(status?: string | null) {
  const value = String(status || 'UNKNOWN').trim().toUpperCase()
  const labels: Record<string,string> = {
    SUCCESS:'成功', SUCCEEDED:'成功', FINISHED:'成功', COMPLETED:'成功',
    RUNNING:'运行中', STARTING:'启动中', SUBMITTED:'已提交', QUEUED:'等待运行', PENDING:'等待中', WAITING:'等待中',
    FAILED:'失败', FAIL:'失败', ERROR:'失败', LOST:'已丢失', UNKNOWN:'未知',
    STOPPED:'已停止', STOP:'已停止', CANCELLED:'已取消', CANCELED:'已取消', KILLED:'已终止',
    ACTIVE:'启用', ENABLED:'启用', ONLINE:'已上线', OFFLINE:'已下线', DISABLED:'禁用', INACTIVE:'禁用',
    HEALTHY:'健康', UP:'正常', DOWN:'异常', UNHEALTHY:'异常',
    DRAFT:'草稿', PUBLISHED:'已发布', RELEASED:'已发布', UNPUBLISHED:'未发布', CERTIFIED:'已认证',
    PENDING_APPROVAL:'待审批', APPROVED:'已通过', REJECTED:'已驳回',
    READY:'就绪', PAUSED:'已暂停', CREATED:'已创建', OPEN:'待处理', ACKNOWLEDGED:'已确认', RESOLVED:'已解决',
    MANUAL:'手动', SCHEDULED:'调度', WORKFLOW:'工作流', BACKFILL:'补数'
  }
  return labels[value] || value
}
