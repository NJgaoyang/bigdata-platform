<script setup lang="ts">
withDefaults(defineProps<{ subtitle?: string; size?: 'sm'|'md'|'lg'; iconOnly?: boolean }>(),{subtitle:'',size:'md',iconOnly:false})
</script>
<template>
  <span :class="['ds-logo',`ds-logo--${size}`,{'ds-logo--icon-only':iconOnly}]">
    <svg class="ds-logo__mark" viewBox="0 0 104 72" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="dsBlueMain" x1="10" y1="10" x2="91" y2="61" gradientUnits="userSpaceOnUse"><stop stop-color="#2563EB"/><stop offset=".55" stop-color="#3B82F6"/><stop offset="1" stop-color="#4F8DFF"/></linearGradient>
        <linearGradient id="dsBlueSoft" x1="11" y1="37" x2="70" y2="60" gradientUnits="userSpaceOnUse"><stop stop-color="#93C5FD"/><stop offset="1" stop-color="#4F8DFF"/></linearGradient>
      </defs>
      <path d="M8 10h42c25 0 42 10.7 42 26S75 62 50 62H8V48h38c13.7 0 22-4.4 22-12s-8.3-12-22-12H8V10Z" fill="url(#dsBlueMain)"/>
      <path d="M8 37h39c9.8 0 16.8 2.2 21 6.1C62.3 54.3 48.4 61 32.2 62H8V37Z" fill="url(#dsBlueSoft)" opacity=".72"/>
      <path d="M8 24h25.5L49 36 33.5 48H8V39h20.5l4-3-4-3H8V24Z" fill="#FBFDFF"/>
    </svg>
    <span v-if="!iconOnly" class="ds-logo__body">
      <span class="ds-logo__word"><b>Data</b><strong>Sphere</strong></span>
      <small v-if="subtitle">{{ subtitle }}</small>
    </span>
  </span>
</template>
<style scoped>
.ds-logo{display:inline-flex;align-items:center;gap:10px;min-width:0;color:#081B3A;line-height:1}.ds-logo__mark{display:block;flex:none;filter:drop-shadow(0 4px 10px rgba(59,130,246,.08))}.ds-logo__body{display:flex;min-width:0;flex-direction:column;justify-content:center}.ds-logo__word{display:flex;align-items:baseline;white-space:nowrap;letter-spacing:-.7px}.ds-logo__word b,.ds-logo__word strong{font-weight:800;color:#081B3A}.ds-logo__body small{margin-top:4px;color:#7E8DA8;font-weight:500;letter-spacing:1.5px;white-space:nowrap}.ds-logo--sm{gap:8px}.ds-logo--sm .ds-logo__mark{width:38px;height:29px}.ds-logo--sm .ds-logo__word{font-size:19px}.ds-logo--sm small{font-size:9px}.ds-logo--md .ds-logo__mark{width:50px;height:38px}.ds-logo--md .ds-logo__word{font-size:24px}.ds-logo--md small{font-size:10px}.ds-logo--lg{gap:14px}.ds-logo--lg .ds-logo__mark{width:74px;height:56px}.ds-logo--lg .ds-logo__word{font-size:34px}.ds-logo--lg small{font-size:12px}.ds-logo--icon-only{gap:0}.ds-logo--icon-only .ds-logo__mark{margin:0}
</style>
