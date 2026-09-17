<script setup lang="ts">
withDefaults(defineProps<{
  subtitle?: string
  size?: 'sm' | 'md' | 'lg'
  iconOnly?: boolean
}>(), { subtitle: '', size: 'md', iconOnly: false })
</script>

<template>
  <span :class="['ds-logo', `ds-logo--${size}`, { 'ds-logo--icon-only': iconOnly }]">
    <svg class="ds-logo__mark" viewBox="0 0 72 64" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="dsBrandBlue" x1="8" y1="8" x2="62" y2="56" gradientUnits="userSpaceOnUse">
          <stop stop-color="#1F6FFF"/>
          <stop offset="1" stop-color="#4D8CFF"/>
        </linearGradient>
        <linearGradient id="dsBrandBlueSoft" x1="12" y1="18" x2="48" y2="48" gradientUnits="userSpaceOnUse">
          <stop stop-color="#7CB3FF"/>
          <stop offset="1" stop-color="#2F74F3"/>
        </linearGradient>
      </defs>
      <path d="M9 9h25.5C51.5 9 63 18.1 63 32S51.5 55 34.5 55H9V40h24.7c8 0 13.3-3 13.3-8s-5.3-8-13.3-8H9V9Z" fill="url(#dsBrandBlue)"/>
      <path d="M9 24h24.7c6.9 0 11.8 2.3 12.9 6.2C41.2 37.1 31.7 40 22.7 40H9V24Z" fill="url(#dsBrandBlueSoft)" opacity=".96"/>
      <path d="M9 24h16L9 40V24Z" fill="#F8FBFF"/>
    </svg>
    <span v-if="!iconOnly" class="ds-logo__body">
      <span class="ds-logo__word"><b>Data</b><strong>Sphere</strong></span>
      <small v-if="subtitle">{{ subtitle }}</small>
    </span>
  </span>
</template>

<style scoped>
.ds-logo{display:inline-flex;align-items:center;gap:10px;min-width:0;color:#071b3b;line-height:1}
.ds-logo__mark{display:block;flex:none;filter:drop-shadow(0 7px 16px rgba(47,116,243,.12))}
.ds-logo__body{display:flex;min-width:0;flex-direction:column;justify-content:center}
.ds-logo__word{display:flex;align-items:baseline;white-space:nowrap;letter-spacing:-.5px}
.ds-logo__word b,.ds-logo__word strong{font-weight:780;color:#071b3b}
.ds-logo__body small{margin-top:4px;color:#8A97AD;font-weight:500;letter-spacing:1.6px;white-space:nowrap}
.ds-logo--sm{gap:8px}.ds-logo--sm .ds-logo__mark{width:34px;height:30px}.ds-logo--sm .ds-logo__word{font-size:19px}.ds-logo--sm small{font-size:9px}
.ds-logo--md .ds-logo__mark{width:46px;height:40px}.ds-logo--md .ds-logo__word{font-size:24px}.ds-logo--md small{font-size:10px}
.ds-logo--lg{gap:14px}.ds-logo--lg .ds-logo__mark{width:60px;height:54px}.ds-logo--lg .ds-logo__word{font-size:32px}.ds-logo--lg small{font-size:12px}
.ds-logo--icon-only{gap:0}.ds-logo--icon-only .ds-logo__mark{margin:0}
</style>
