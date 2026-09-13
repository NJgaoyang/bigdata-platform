<script setup lang="ts">
import { useRouter } from 'vue-router'
const router=useRouter()
</script>
<template><div class="forbidden"><div class="box"><div class="code">403</div><h1>没有可访问的模块</h1><p>当前账号没有任何模块查看权限，请联系平台管理员配置权限。</p><el-button type="primary" @click="router.push('/login')">返回登录</el-button></div></div></template>
<style scoped>.forbidden{min-height:100vh;display:grid;place-items:center;background:#f7f8fa}.box{width:420px;padding:40px;border:1px solid #e4e7ec;border-radius:10px;background:#fff;text-align:center}.code{color:#6172f3;font-size:54px;font-weight:750}h1{margin:8px 0;font-size:20px}p{margin:0 0 22px;color:#667085;font-size:13px;line-height:1.7}</style>
