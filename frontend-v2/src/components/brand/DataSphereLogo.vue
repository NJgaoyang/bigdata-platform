<script setup lang="ts">
withDefaults(defineProps<{ subtitle?: string; size?: 'sm'|'md'|'lg'; iconOnly?: boolean }>(),{subtitle:'',size:'md',iconOnly:false})
</script>
<template>
  <span :class="['ds-logo',`ds-logo--${size}`,{'ds-logo--icon-only':iconOnly}]">
    <svg class="ds-logo__mark" viewBox="0 0 180 150" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="dsBlueMain" x1="18" y1="14" x2="164" y2="136" gradientUnits="userSpaceOnUse"><stop stop-color="#2563EB"/><stop offset=".52" stop-color="#3B82F6"/><stop offset="1" stop-color="#4F8DFF"/></linearGradient>
        <linearGradient id="dsBlueSoft" x1="18" y1="92" x2="138" y2="140" gradientUnits="userSpaceOnUse"><stop stop-color="#93C5FD"/><stop offset="1" stop-color="#4F8DFF"/></linearGradient>
      </defs>
      <path d="M14 12H109C148 12 172 39 172 76C172 114 148 140 112 140H14V102H30L67 58H14V12Z" fill="url(#dsBlueMain)"/>
      <path d="M14 102H30L67 58H95C112 58 126 65 132 78C138 91 135 106 127 118C118 132 99 140 76 140H14V102Z" fill="url(#dsBlueSoft)" opacity=".72"/>
      <path d="M118 88C129 84 139 77 147 68C145 81 139 94 129 104L117 114C122 103 124 95 118 88Z" fill="#FBFDFF"/>
    </svg>
    <span v-if="!iconOnly" class="ds-logo__body">
      <span class="ds-logo__word"><b>Data</b><strong>Sphere</strong></span>
      <small v-if="subtitle">{{ subtitle }}</small>
    </span>
  </span>
</template>
<style scoped>
.ds-logo{display:inline-flex;align-items:center;gap:10px;min-width:0;color:#081B3A;line-height:1}.ds-logo__mark{display:block;flex:none;filter:drop-shadow(0 4px 10px rgba(59,130,246,.08))}.ds-logo__body{display:flex;min-width:0;flex-direction:column;justify-content:center}.ds-logo__word{display:flex;align-items:baseline;white-space:nowrap;letter-spacing:-.7px}.ds-logo__word b,.ds-logo__word strong{font-weight:800;color:#081B3A}.ds-logo__body small{margin-top:4px;color:#7E8DA8;font-weight:500;letter-spacing:1.5px;white-space:nowrap}.ds-logo--sm{gap:8px}.ds-logo--sm .ds-logo__mark{width:38px;height:32px}.ds-logo--sm .ds-logo__word{font-size:19px}.ds-logo--sm small{font-size:9px}.ds-logo--md .ds-logo__mark{width:50px;height:42px}.ds-logo--md .ds-logo__word{font-size:24px}.ds-logo--md small{font-size:10px}.ds-logo--lg{gap:14px}.ds-logo--lg .ds-logo__mark{width:74px;height:62px}.ds-logo--lg .ds-logo__word{font-size:34px}.ds-logo--lg small{font-size:12px}.ds-logo--icon-only{gap:0}.ds-logo--icon-only .ds-logo__mark{margin:0}
</style>
