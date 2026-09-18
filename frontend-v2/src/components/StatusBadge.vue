<script setup lang="ts">
import { computed } from 'vue'
import { statusLabel } from '../utils/display'
const props = defineProps<{ status?: string; label?: string }>()
const normalized = computed(() => (props.status || 'UNKNOWN').toUpperCase())
const tone = computed(() => {
  const value = normalized.value
  if (value.includes('SUCCESS') || value.includes('FINISHED') || value.includes('COMPLETED') || ['ACTIVE','RUNNING','HEALTHY','UP','NORMAL','OK','PUBLISHED','CERTIFIED','APPROVED','RELEASED'].includes(value)) return 'success'
  if (value.includes('FAIL') || value.includes('ERROR') || value.includes('DOWN') || value === 'REJECTED') return 'danger'
  if (value.includes('START') || value.includes('SUBMIT') || value.includes('PENDING') || value.includes('WAIT')) return 'warning'
  return 'neutral'
})
</script>
<template><span :class="['status-badge', `status-badge--${tone}`]"><i />{{ props.label || statusLabel(normalized) }}</span></template>
<style scoped>
.status-badge { display: inline-flex; align-items: center; gap: 6px; color: #475467; font-size: 12px; }
.status-badge i { width: 7px; height: 7px; border-radius: 50%; background: #98a2b3; }
.status-badge--success { color: var(--ds-success); }
.status-badge--success i { background: var(--ds-success); }
.status-badge--danger i { background: var(--ds-danger); }
.status-badge--warning i { background: var(--ds-warning); }
</style>
