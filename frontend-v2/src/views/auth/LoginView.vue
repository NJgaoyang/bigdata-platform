<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../../api/auth'

const router=useRouter(),route=useRoute(),loading=ref(false)
const form=reactive({username:'',password:''})
async function submit(){
  if(!form.username.trim()||!form.password)return ElMessage.warning('请输入用户名和密码')
  loading.value=true
  try{
    const session=await authApi.login(form.username.trim(),form.password)
    localStorage.setItem('platform_auth_token',session.token)
    const redirect=typeof route.query.redirect==='string'&&route.query.redirect.startsWith('/')?route.query.redirect:'/'
    await router.replace(redirect)
  }catch(e){ElMessage.error(e instanceof Error?e.message:'登录失败')}
  finally{loading.value=false}
}
</script>
<template>
<div class="login-page">
  <div class="login-card">
    <div class="logo"><span><i/></span><div><strong>DataSphere</strong><small>企业数据开发平台</small></div></div>
    <div class="login-title">登录平台</div>
    <div class="login-sub">使用平台账号访问数据开发与生产任务。</div>
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="用户名"><el-input v-model="form.username" size="large" autocomplete="username" @keyup.enter="submit"/></el-form-item>
      <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password size="large" autocomplete="current-password" @keyup.enter="submit"/></el-form-item>
      <el-button type="primary" size="large" :loading="loading" style="width:100%" @click="submit">登录</el-button>
    </el-form>
  </div>
</div>
</template>
<style scoped>
.login-page{min-height:100vh;display:grid;place-items:center;background:#f6f7f9;padding:24px}.login-card{width:410px;padding:34px 38px 38px;border:1px solid #e4e7ec;border-radius:10px;background:#fff;box-shadow:0 12px 32px rgba(16,24,40,.05)}.logo{display:flex;align-items:center;gap:11px;margin-bottom:34px}.logo>span{width:42px;height:42px;display:grid;place-items:center;border-radius:10px;background:#4f6bff}.logo i{width:22px;height:22px;border:2px solid #fff;border-radius:3px;transform:rotate(45deg)}.logo strong,.logo small{display:block}.logo strong{font-size:20px}.logo small{margin-top:2px;color:#98a2b3;font-size:10px}.login-title{font-size:22px;font-weight:700;color:#101828}.login-sub{margin:7px 0 25px;color:#667085;font-size:12px}</style>
