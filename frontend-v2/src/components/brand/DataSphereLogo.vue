<script setup lang="ts">
withDefaults(defineProps<{ subtitle?: string; size?: 'sm'|'md'|'lg'; iconOnly?: boolean }>(),{subtitle:'',size:'md',iconOnly:false})
</script>
<template>
  <span :class="['ds-logo',`ds-logo--${size}`,{'ds-logo--icon-only':iconOnly}]">
    <svg class="ds-logo__mark" viewBox="0 0 96 72" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="dsBlueMain" x1="12" y1="12" x2="82" y2="60" gradientUnits="userSpaceOnUse"><stop stop-color="#2563EB"/><stop offset="1" stop-color="#4F8DFF"/></linearGradient>
        <linearGradient id="dsBlueLayer" x1="18" y1="34" x2="72" y2="60" gradientUnits="userSpaceOnUse"><stop stop-color="#78AEFF"/><stop offset="1" stop-color="#3B82F6"/></linearGradient>
      </defs>
      <path d="M8 10h38c26 0 42 10.6 42 26S72 62 46 62H8V47h36c12.6 0 21-4.1 21-11s-8.4-11-21-11H8V10Z" fill="url(#dsBlueMain)"/>
      <path d="M8 36h35.5c10.8 0 18.3 2.5 21 6.9C59.5 54 44.9 60.5 31.4 62H8V36Z" fill="url(#dsBlueLayer)" opacity=".9"/>
      <path d="M8 25h24L8 47V25Z" fill="#F8FBFF"/>
      <path d="M8 47h28c8.5 0 16.3-1.2 22.6-3.5C53.7 53.9 39.8 60 27 60H8V47Z" fill="#5E98FA" opacity=".62"/>
    </svg>
    <span v-if="!iconOnly" class="ds-logo__body">
      <span class="ds-logo__word"><b>Data</b><strong>Sphere</strong></span>
      <small v-if="subtitle">{{ subtitle }}</small>
    </span>
  </span>
</template>
<style scoped>
.ds-logo{display:inline-flex;align-items:center;gap:10px;min-width:0;color:#081B3A;line-height:1}.ds-logo__mark{display:block;flex:none;filter:drop-shadow(0 6px 14px rgba(59,130,246,.12))}.ds-logo__body{display:flex;min-width:0;flex-direction:column;justify-content:center}.ds-logo__word{display:flex;align-items:baseline;white-space:nowrap;letter-spacing:-.7px}.ds-logo__word b,.ds-logo__word strong{font-weight:800;color:#081B3A}.ds-logo__body small{margin-top:4px;color:#7E8DA8;font-weight:500;letter-spacing:1.5px;white-space:nowrap}.ds-logo--sm{gap:8px}.ds-logo--sm .ds-logo__mark{width:38px;height:29px}.ds-logo--sm .ds-logo__word{font-size:19px}.ds-logo--sm small{font-size:9px}.ds-logo--md .ds-logo__mark{width:50px;height:38px}.ds-logo--md .ds-logo__word{font-size:24px}.ds-logo--md small{font-size:10px}.ds-logo--lg{gap:14px}.ds-logo--lg .ds-logo__mark{width:74px;height:56px}.ds-logo--lg .ds-logo__word{font-size:34px}.ds-logo--lg small{font-size:12px}.ds-logo--icon-only{gap:0}.ds-logo--icon-only .ds-logo__mark{margin:0}
</style>
