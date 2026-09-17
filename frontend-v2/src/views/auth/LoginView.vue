<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { authApi } from '../../api/auth'
import brandImg from '../../assets/login-brand.webp'
import platformImg from '../../assets/login-platform.webp'

const router=useRouter()
const route=useRoute()
const loading=ref(false)
const checkingSession=ref(true)
const passwordVisible=ref(false)
const rememberedUsername=localStorage.getItem('datasphere_remember_username')||''
const rememberUsername=ref(Boolean(rememberedUsername))
const form=reactive({username:rememberedUsername,password:''})
const year=new Date().getFullYear()
const redirectTarget=computed(()=>typeof route.query.redirect==='string'&&route.query.redirect.startsWith('/')?route.query.redirect:'/')

onMounted(async()=>{
  const token=localStorage.getItem('platform_auth_token')
  if(!token){checkingSession.value=false;return}
  try{
    const me=await authApi.me()
    if(me.authenticated){await router.replace(redirectTarget.value);return}
  }catch{
    localStorage.removeItem('platform_auth_token')
  }
  checkingSession.value=false
})

async function submit(){
  if(loading.value||checkingSession.value)return
  const username=form.username.trim()
  if(!username)return ElMessage.warning('请输入账号')
  if(!form.password)return ElMessage.warning('请输入密码')
  loading.value=true
  try{
    const session=await authApi.login(username,form.password)
    localStorage.setItem('platform_auth_token',session.token)
    if(rememberUsername.value)localStorage.setItem('datasphere_remember_username',username)
    else localStorage.removeItem('datasphere_remember_username')
    await router.replace(redirectTarget.value)
  }catch(e){
    ElMessage.error(e instanceof Error?e.message:'登录失败，请检查账号或密码')
  }finally{
    loading.value=false
  }
}

function forgotPassword(){
  ElMessageBox.alert('当前平台未开放自助密码找回，请联系平台管理员重置账号密码。','忘记密码',{confirmButtonText:'我知道了',type:'info'})
}
</script>

<template>
  <div class="login-page">
    <div class="orbit orbit-a"></div>
    <div class="orbit orbit-b"></div>
    <div class="top-decoration" aria-hidden="true">
      <i></i><span>DATA</span><span>CONNECTS</span><span>A BRIGHTER</span><span>TOMORROW</span>
    </div>

    <main class="shell">
      <section class="hero" aria-label="DataSphere 平台介绍">
        <img class="brand-image" :src="brandImg" alt="DataSphere" />
        <h1>统一数据开发与治理平台</h1>
        <p class="hero-copy">轻质感、克制而清晰的数据工作台体验<br>统一数据接入、开发、调度与治理</p>
        <div class="visual-wrap" aria-hidden="true">
          <img class="platform-image" :src="platformImg" alt="" />
        </div>
        <div class="hero-slogan"><span>数 据 让 业 务 更 有 可 能</span><i></i></div>
      </section>

      <section class="login-column">
        <div class="login-card" aria-label="登录表单">
          <div v-if="checkingSession" class="session-check">
            <span class="spinner"></span>
            <strong>正在验证登录状态</strong>
            <small>请稍候...</small>
          </div>
          <template v-else>
            <h2>登录</h2>
            <p class="welcome">欢迎回来，使用 DataSphere</p>
            <form @submit.prevent="submit" novalidate>
              <div class="field">
                <label for="account">账号</label>
                <div class="input-wrap">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><circle cx="12" cy="8" r="3.2"/><path d="M5.5 20c.6-4 3-6 6.5-6s5.9 2 6.5 6"/></svg>
                  <input id="account" v-model="form.username" autocomplete="username" placeholder="请输入账号" :disabled="loading" />
                </div>
              </div>
              <div class="field">
                <label for="password">密码</label>
                <div class="input-wrap password-wrap">
                  <svg class="lock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><rect x="5.5" y="10" width="13" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>
                  <input id="password" v-model="form.password" :type="passwordVisible?'text':'password'" autocomplete="current-password" placeholder="请输入密码" :disabled="loading" />
                  <button class="eye" type="button" :aria-label="passwordVisible?'隐藏密码':'显示密码'" @click="passwordVisible=!passwordVisible">
                    <svg v-if="!passwordVisible" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M2.5 12s3.2-5 9.5-5 9.5 5 9.5 5-3.2 5-9.5 5-9.5-5-9.5-5Z"/><circle cx="12" cy="12" r="2.4"/></svg>
                    <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="m3 3 18 18M10.5 7.2A10.8 10.8 0 0 1 12 7c6.3 0 9.5 5 9.5 5a14.5 14.5 0 0 1-2.3 2.8M6.1 6.2C3.8 7.8 2.5 12 2.5 12s3.2 5 9.5 5c1 0 1.9-.1 2.7-.4"/></svg>
                  </button>
                </div>
              </div>
              <div class="form-row">
                <label class="remember"><input v-model="rememberUsername" type="checkbox" /><span>记住我</span></label>
                <button class="forgot" type="button" @click="forgotPassword">忘记密码？</button>
              </div>
              <button class="login-button" type="submit" :disabled="loading">
                <span v-if="loading" class="spinner small"></span><span>{{loading?'登录中...':'登 录'}}</span>
              </button>
            </form>
          </template>
        </div>
      </section>
    </main>

    <div class="copyright"><i></i><span>© {{year}} DataSphere · 用数据创造更大的价值</span><i></i></div>
  </div>
</template>

<style scoped>
.login-page{--blue:#2f70f3;--ink:#0d2a55;position:relative;min-height:100vh;overflow:hidden;color:var(--ink);background:linear-gradient(180deg,#fbfdff 0%,#f3f8ff 100%)}
.login-page:before{content:"";position:absolute;inset:0;pointer-events:none;background:radial-gradient(circle at 75% 22%,rgba(98,158,255,.16),transparent 31%),radial-gradient(circle at 15% 72%,rgba(107,165,255,.08),transparent 31%)}
.orbit{position:absolute;border:2px solid rgba(110,157,226,.11);border-radius:50%;pointer-events:none}.orbit-a{width:980px;height:980px;right:-120px;top:-610px}.orbit-b{width:1360px;height:1360px;right:-610px;top:-780px;border-color:rgba(110,157,226,.08)}
.top-decoration{position:absolute;right:5.8vw;top:5.2vh;z-index:2;display:flex;flex-direction:column;gap:7px;color:#9fb2cf;font-size:11px;letter-spacing:2.1px;line-height:1}.top-decoration i{width:25px;height:1px;margin-bottom:8px;background:#9db2cf}.top-decoration span{white-space:nowrap}
.shell{position:relative;z-index:1;width:min(1530px,calc(100vw - 96px));height:100vh;margin:0 auto;display:grid;grid-template-columns:minmax(0,1.32fr) minmax(500px,.88fr);gap:64px;align-items:center}
.hero{position:relative;height:100%;min-width:0;padding:15.2vh 0 70px;display:flex;flex-direction:column}.brand-image{width:min(495px,57%);height:auto;display:block;margin-bottom:25px}.hero h1{margin:0 0 20px;font-size:45px;line-height:1.16;font-weight:800;letter-spacing:-1.7px;color:#0b2a58}.hero-copy{margin:0;color:#7e91af;font-size:21px;line-height:1.68;font-weight:430}.visual-wrap{position:relative;flex:1;min-height:390px;margin-top:5px}.platform-image{position:absolute;left:-112px;bottom:-8px;width:min(950px,113%);height:auto;display:block;filter:drop-shadow(0 24px 36px rgba(63,113,203,.05))}.hero-slogan{position:absolute;left:-4px;bottom:77px;display:flex;align-items:center;gap:20px;color:#98abc8;font-size:13px;letter-spacing:8px;white-space:nowrap}.hero-slogan i{display:block;width:110px;height:1px;background:#cbd9ec}
.login-column{height:100%;display:flex;align-items:center;justify-content:flex-end;padding-top:1.4vh}.login-card{width:min(100%,570px);min-height:605px;padding:58px 59px 56px;border:1px solid rgba(221,230,243,.9);border-radius:22px;background:rgba(255,255,255,.96);box-shadow:0 24px 70px rgba(63,104,173,.12);backdrop-filter:blur(12px)}.login-card h2{margin:0 0 12px;font-size:40px;line-height:1.15;font-weight:800;letter-spacing:-.8px;color:#081f48}.welcome{margin:0 0 37px;color:#7487a8;font-size:20px}.field{margin-bottom:25px}.field label{display:block;margin-bottom:10px;color:#193962;font-size:17px;font-weight:650}.input-wrap{position:relative}.input-wrap>svg{position:absolute;left:19px;top:50%;width:22px;height:22px;transform:translateY(-50%);color:#7f96b8;z-index:2}.input-wrap input{width:100%;height:63px;padding:0 52px 0 56px;border:1px solid #cfd9eb;border-radius:7px;outline:none;background:#fff;color:#20375f;font-size:17px;transition:.2s}.input-wrap input::placeholder{color:#a2b0c5}.input-wrap input:focus{border-color:#6d9cff;box-shadow:0 0 0 4px rgba(47,111,247,.09)}.input-wrap input:disabled{background:#f7f9fc}.eye{position:absolute;right:14px;top:50%;width:30px;height:30px;padding:4px;border:0;background:transparent;color:#748bab;transform:translateY(-50%);cursor:pointer}.eye svg{width:100%;height:100%}.form-row{display:flex;align-items:center;justify-content:space-between;gap:20px;margin:3px 0 31px}.remember{display:flex;align-items:center;gap:10px;color:#425879;font-size:16px;cursor:pointer;user-select:none}.remember input{appearance:none;width:21px;height:21px;margin:0;border:1px solid #9cafc8;border-radius:4px;background:#fff;position:relative;cursor:pointer}.remember input:checked{border-color:#3778f6;background:#3778f6}.remember input:checked:after{content:"✓";position:absolute;left:4px;top:-2px;color:#fff;font-size:15px;font-weight:800}.forgot{border:0;background:transparent;color:#1e6df4;font-size:16px;cursor:pointer;padding:0}.login-button{width:100%;height:64px;display:flex;align-items:center;justify-content:center;gap:10px;border:0;border-radius:7px;background:linear-gradient(90deg,#3479f5,#276cf0);color:#fff;font-size:19px;font-weight:750;letter-spacing:7px;cursor:pointer;box-shadow:0 10px 24px rgba(47,112,243,.18);transition:.18s}.login-button:hover:not(:disabled){transform:translateY(-1px);background:linear-gradient(90deg,#2d72ef,#2166e6)}.login-button:disabled{opacity:.7;cursor:not-allowed}.session-check{min-height:470px;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#607695}.session-check strong{margin-top:17px;font-size:17px}.session-check small{margin-top:6px;color:#96a6be}.spinner{width:30px;height:30px;border:3px solid rgba(47,111,247,.18);border-top-color:#2f6ff7;border-radius:50%;animation:spin .75s linear infinite}.spinner.small{width:18px;height:18px;border-width:2px;border-color:rgba(255,255,255,.35);border-top-color:#fff}@keyframes spin{to{transform:rotate(360deg)}}
.copyright{position:absolute;left:50%;bottom:26px;z-index:3;transform:translateX(-50%);display:flex;align-items:center;gap:22px;color:#92a6c3;font-size:12px;white-space:nowrap}.copyright i{display:block;width:29px;height:1px;background:#b8c9df}
@media(max-width:1500px){.shell{width:min(1360px,calc(100vw - 64px));grid-template-columns:minmax(0,1.26fr) minmax(460px,.84fr);gap:46px}.hero{padding-top:12.7vh}.brand-image{width:min(450px,59%);margin-bottom:20px}.hero h1{font-size:39px;margin-bottom:14px}.hero-copy{font-size:18px}.visual-wrap{min-height:320px}.platform-image{left:-90px;width:min(860px,113%)}.login-card{max-width:520px;min-height:555px;padding:48px 48px 46px}.login-card h2{font-size:35px}.welcome{font-size:18px;margin-bottom:30px}.field{margin-bottom:19px}.input-wrap input,.login-button{height:56px}.hero-slogan{bottom:62px;font-size:11px;letter-spacing:6px}}
@media(max-width:1220px){.shell{grid-template-columns:minmax(0,1.08fr) minmax(430px,.92fr);gap:30px}.brand-image{width:min(400px,65%)}.hero h1{font-size:34px}.hero-copy{font-size:16px}.platform-image{left:-70px;width:min(760px,118%)}.login-card{max-width:470px;min-height:515px;padding:40px}.login-card h2{font-size:32px}.welcome{font-size:16px}.field label{font-size:15px}.input-wrap input{font-size:15px}}
@media(max-height:820px) and (min-width:1101px){.hero{padding-top:8vh;padding-bottom:40px}.brand-image{width:min(380px,54%);margin-bottom:15px}.hero h1{font-size:34px;margin-bottom:12px}.hero-copy{font-size:16px;line-height:1.55}.visual-wrap{min-height:255px}.platform-image{left:-70px;bottom:0;width:min(735px,102%)}.hero-slogan{bottom:34px}.login-column{padding-top:0}.login-card{max-width:470px;min-height:480px;padding:34px 40px}.login-card h2{font-size:31px}.welcome{margin-bottom:22px;font-size:16px}.field{margin-bottom:14px}.field label{margin-bottom:7px;font-size:14px}.input-wrap input,.login-button{height:50px}.form-row{margin:0 0 19px}.remember,.forgot{font-size:14px}.top-decoration{top:4vh;font-size:9px}.copyright{bottom:12px;font-size:10px}}
@media(max-height:680px) and (min-width:1101px){.hero{padding-top:5vh}.brand-image{width:min(330px,50%)}.hero h1{font-size:29px}.hero-copy{font-size:14px}.visual-wrap{min-height:190px}.platform-image{width:min(620px,93%)}.hero-slogan{display:none}.login-card{min-height:430px;padding:27px 34px}.login-card h2{font-size:28px}.welcome{margin-bottom:17px}.field{margin-bottom:11px}.input-wrap input,.login-button{height:46px}.form-row{margin-bottom:15px}.copyright{display:none}}
@media(max-width:1100px){.login-page{overflow:auto}.orbit,.top-decoration{display:none}.shell{height:auto;min-height:100vh;grid-template-columns:1fr;width:min(780px,calc(100vw - 40px));gap:24px;padding:45px 0 92px}.hero{height:auto;padding:0}.brand-image{width:min(410px,65%);margin-bottom:20px}.hero h1{font-size:36px}.hero-copy{font-size:18px}.visual-wrap{min-height:330px}.platform-image{position:relative;left:-35px;bottom:auto;width:108%;margin-top:8px}.hero-slogan{display:none}.login-column{height:auto;justify-content:center;padding:0}.login-card{width:min(100%,620px);max-width:none;min-height:0}.copyright{bottom:18px}}
@media(max-width:640px){.shell{width:calc(100vw - 28px);padding-top:28px}.brand-image{width:min(330px,80%)}.hero h1{font-size:29px}.hero-copy{font-size:15px}.visual-wrap{min-height:0}.platform-image{left:-16px;width:106%;margin-top:10px}.login-card{padding:32px 24px 34px;border-radius:18px}.login-card h2{font-size:31px}.welcome{font-size:16px;margin-bottom:25px}.field{margin-bottom:17px}.field label{font-size:15px}.input-wrap input,.login-button{height:54px}.input-wrap input{font-size:15px}.form-row{margin-bottom:23px}.remember,.forgot{font-size:14px}.copyright{font-size:10px;gap:10px}.copyright i{width:18px}}
@media(max-width:420px){.shell{width:calc(100vw - 20px);padding-top:22px}.brand-image{width:min(285px,86%)}.hero h1{font-size:26px}.hero-copy{font-size:13px}.login-card{padding:27px 18px 30px}.login-card h2{font-size:28px}.welcome{font-size:14px}.input-wrap input{padding-left:48px}.form-row{align-items:flex-start}.copyright{display:none}}
:global(html:has(.login-page)),:global(body:has(.login-page)),:global(#app:has(.login-page)){min-width:0;width:100%;min-height:100%;overflow-x:hidden}
</style>
