<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productNavigation, releaseNavigation, systemNavigation } from '../navigation'
import { authApi, type CurrentUser } from '../api/auth'
import { ArrowDown } from '@element-plus/icons-vue'
import { moduleViewPermission, hasPermission } from '../auth/permissions'

const route = useRoute()
const username=ref('admin'), roleCode=ref('ADMIN'), me=ref<CurrentUser|null>(null)
const router = useRouter()
const moduleKey = computed(() => String(route.meta.module || 'workbench'))
const visibleProducts=computed(()=>productNavigation.filter(item=>hasPermission(me.value,moduleViewPermission[item.key])))
const activeProduct = computed(() => productNavigation.find(item => item.key === moduleKey.value))
const canRelease=computed(()=>hasPermission(me.value,moduleViewPermission.release))
const canSystem=computed(()=>hasPermission(me.value,moduleViewPermission.system))
const isFullWidth = computed(() => moduleKey.value === 'workbench' || moduleKey.value === 'metadata')
const sideTitle = computed(() => {
  if (moduleKey.value === 'release') return '发布中心'
  if (moduleKey.value === 'system') return '系统设置'
  return activeProduct.value?.label || ''
})
const sideItems = computed(() => {
  if (moduleKey.value === 'release') return releaseNavigation
  if (moduleKey.value === 'system') return systemNavigation
  return activeProduct.value?.side || []
})

function go(path: string) { void router.push(path) }
function roleName(value:string){return ({ADMIN:'平台管理员',DEVELOPER:'开发者',RELEASE_MANAGER:'发布审核人',VIEWER:'只读用户',USER:'普通用户'} as Record<string,string>)[value]||value}
async function loadMe(){try{const current=await authApi.me();me.value=current;username.value=current.username||'admin';roleCode.value=current.roleCode||'USER'}catch{}}
async function logout(){try{await authApi.logout()}catch{}localStorage.removeItem('platform_auth_token');await router.replace('/login')}
onMounted(loadMe)
</script>

<template>
  <div class="platform-shell">
    <header class="topbar">
      <button class="brand" type="button" @click="go('/')" aria-label="返回工作台">
        <span class="brand__mark"><i /></span>
        <span class="brand__copy"><strong>DataSphere</strong><small>企业数据开发平台</small></span>
      </button>
      <nav class="product-nav" aria-label="主导航">
        <button v-for="item in visibleProducts" :key="item.key" type="button"
          :class="['product-nav__item', { active: moduleKey === item.key }]" @click="go(item.path)">
          {{ item.label }}
        </button>
      </nav>
      <div class="topbar__right">
        <el-button v-if="canRelease" plain @click="go('/release/history')">发布中心</el-button>
        <el-dropdown trigger="click">
          <button type="button" class="user-entry" :title="`${username} · ${roleName(roleCode)}`">
            <span class="user-entry__avatar">{{ (username||'U').slice(0,1).toUpperCase() }}</span>
            <el-icon class="user-entry__arrow"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="canSystem" @click="go('/system/data-sources')">系统设置</el-dropdown-item>
              <el-dropdown-item :divided="canSystem" @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div :class="['body-shell', { 'body-shell--full': isFullWidth }]">
      <aside v-if="!isFullWidth" class="sidebar">
        <div class="sidebar__title">{{ sideTitle }}</div>
        <nav class="side-nav" :aria-label="`${sideTitle}二级导航`">
          <button v-for="item in sideItems" :key="item.path" type="button"
            :class="['side-nav__item', { active: route.path === item.path }]" @click="go(item.path)">
            <span class="side-nav__icon" />{{ item.label }}
          </button>
        </nav>
      </aside>
      <main class="main-content"><router-view /></main>
    </div>
  </div>
</template>

<style scoped>
.platform-shell { min-height: 100%; background: var(--ds-bg); }
.topbar { position: sticky; top: 0; z-index: 20; height: var(--ds-topbar); display: flex; align-items: center; gap: 18px; padding: 0 24px 0 28px; border-bottom: 1px solid var(--ds-border); background: #fff; }
.brand { width: 224px; flex: 0 0 224px; display: flex; align-items: center; gap: 11px; padding: 0; border: 0; background: transparent; text-align: left; cursor: pointer; }
.brand__mark { width: 40px; height: 40px; display: grid; place-items: center; border-radius: 10px; background: var(--ds-brand); }
.brand__mark i { width: 22px; height: 22px; display: block; border: 2px solid #fff; border-radius: 3px; transform: rotate(45deg); }
.brand__copy { min-width: 0; display: flex; flex-direction: column; }
.brand__copy strong { font-size: 20px; line-height: 23px; letter-spacing: -.3px; }
.brand__copy small { margin-top: 2px; color: var(--ds-text-tertiary); font-size: 10px; }
.product-nav { align-self: stretch; display: flex; align-items: stretch; white-space: nowrap; }
.product-nav__item { position: relative; min-width: 72px; padding: 0 13px; border: 0; background: transparent; color: #344054; cursor: pointer; font-size: 14px; }
.product-nav__item:hover { color: var(--ds-brand); }
.product-nav__item.active { color: var(--ds-text); font-weight: 650; }
.product-nav__item.active::after { content: ''; position: absolute; left: 14px; right: 14px; bottom: 0; height: 3px; border-radius: 3px 3px 0 0; background: var(--ds-brand); }
.topbar__right { margin-left: auto; display: flex; align-items: center; justify-content: flex-end; gap: 12px; }
.user-entry { display: flex; align-items: center; gap: 8px; padding: 0; border: 0; background: transparent; cursor: pointer; }
.user-entry__avatar { width: 40px; height: 40px; display: grid; place-items: center; border-radius: 50%; background: #f0f2f5; color: #52637a; font-size: 14px; font-weight: 700; }
.user-entry__arrow { color: #98a2b3; font-size: 14px; }
.body-shell { min-height: calc(100vh - var(--ds-topbar)); display: grid; grid-template-columns: var(--ds-sidebar) minmax(0, 1fr); }
.body-shell--full { display: block; }
.sidebar { position: sticky; top: var(--ds-topbar); align-self: start; height: calc(100vh - var(--ds-topbar)); overflow-y: auto; padding: 20px 10px; border-right: 1px solid var(--ds-border); background: #fff; }
.sidebar__title { padding: 0 12px 10px; color: var(--ds-text-tertiary); font-size: 11px; }
.side-nav { display: flex; flex-direction: column; gap: 4px; }
.side-nav__item { width: 100%; height: 40px; display: flex; align-items: center; gap: 10px; padding: 0 12px; border: 0; border-radius: 7px; background: transparent; color: #475467; cursor: pointer; text-align: left; }
.side-nav__item:hover { background: #f8f9fb; color: var(--ds-text); }
.side-nav__item.active { background: var(--ds-brand-soft); color: var(--ds-brand); font-weight: 650; }
.side-nav__icon { width: 15px; height: 15px; border: 1px solid #cbd5e1; border-radius: 4px; box-shadow: inset 0 0 0 4px #fff; background: #98a2b3; }
.side-nav__item.active .side-nav__icon { border-color: #aeb8ff; background: var(--ds-brand); }
.main-content { min-width: 0; }
</style>
