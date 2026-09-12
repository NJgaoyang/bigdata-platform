<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>
<template>
  <header class="ds-page-header">
    <div>
      <h1 class="ds-page-header__title">{{ title }}</h1>
      <p v-if="subtitle" class="ds-page-header__subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="ds-page-header__actions"><slot name="actions" /></div>
  </header>
</template>
