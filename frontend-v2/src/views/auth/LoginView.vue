<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { authApi } from '../../api/auth'

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
  <main class="modern-login-page">
    <div class="top-brand" aria-label="DataSphere 数据开发平台">
      <svg class="brand-mark" viewBox="0 0 40 40" fill="none" aria-hidden="true">
        <path d="M20 3 34 11v18L20 37 6 29V11L20 3Z" fill="#126ff1"/>
        <path d="m20 8 9 5-9 5-9-5 9-5Z" fill="#65afff"/>
        <path d="m11 16 9 5v11l-9-5V16Z" fill="#0a54c4"/>
        <path d="m29 16-9 5v11l9-5V16Z" fill="#2589f7"/>
      </svg>
      <div class="brand-copy"><strong>DataSphere</strong><span>数据开发平台</span></div>
    </div>

    <section class="intro-pane" aria-label="产品介绍">
      <div class="hero-copy">
        <h1>Data<span>Sphere</span></h1>
        <h2>统一数据开发与治理平台</h2>
        <p class="tagline">构建、编排并治理数据，<br />让团队高效协作与交付。</p>
        <div class="short-rule"></div>
        <p class="subline">从数据到价值，打造现代化数据工作方式。</p>
      </div>
      <div class="decor-shape" aria-hidden="true"><i></i><i></i><i></i></div>
    </section>

    <section class="login-pane" aria-label="登录区域">
      <div class="login-card">
        <div v-if="checkingSession" class="session-check">
          <span class="spinner"></span>
          <strong>正在验证登录状态</strong>
          <small>请稍候...</small>
        </div>
        <template v-else>
          <div class="card-title">
            <h2>登录</h2>
            <p>欢迎回来，使用 DataSphere</p>
          </div>
          <form class="login-form" @submit.prevent="submit">
            <div class="field">
              <label for="login-username">账号</label>
              <div class="input-wrap">
                <input id="login-username" v-model="form.username" autocomplete="username" placeholder="请输入账号" :disabled="loading" />
              </div>
            </div>
            <div class="field">
              <label for="login-password">密码</label>
              <div class="input-wrap">
                <input id="login-password" v-model="form.password" :type="passwordVisible?'text':'password'" autocomplete="current-password" placeholder="请输入密码" :disabled="loading" />
                <button class="password-toggle" type="button" :aria-label="passwordVisible?'隐藏密码':'显示密码'" @click="passwordVisible=!passwordVisible">
                  <svg v-if="!passwordVisible" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M2.5 12s3.2-5 9.5-5 9.5 5 9.5 5-3.2 5-9.5 5-9.5-5-9.5-5Z"/><circle cx="12" cy="12" r="2.5"/></svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m3 3 18 18M10.6 7.2A10.8 10.8 0 0 1 12 7c6.3 0 9.5 5 9.5 5a14 14 0 0 1-2.4 2.8M6.2 6.2C3.8 7.8 2.5 12 2.5 12s3.2 5 9.5 5c1 0 1.9-.1 2.7-.4"/></svg>
                </button>
              </div>
            </div>
            <div class="form-meta">
              <label class="remember"><input v-model="rememberUsername" type="checkbox" /><span>记住账号</span></label>
              <button class="forgot" type="button" @click="forgotPassword">忘记密码？</button>
            </div>
            <button class="login-button" type="submit" :disabled="loading">
              <span v-if="loading" class="spinner small"></span>
              <span>{{loading?'登录中…':'登录'}}</span>
            </button>
          </form>
        </template>
      </div>
    </section>

    <footer>© {{year}} DataSphere 数据开发平台</footer>
  </main>
</template>

<style scoped>
.modern-login-page{min-height:100dvh;position:relative;display:grid;grid-template-columns:56% 44%;overflow:hidden;background:#fbfdff;color:#071b3b;font-family:Inter,"SF Pro Display","SF Pro Text",-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",Arial,sans-serif}
.top-brand{position:absolute;top:22px;left:72px;z-index:5;height:58px;display:flex;align-items:center;gap:12px}.brand-mark{width:40px;height:40px;flex:none;filter:drop-shadow(0 6px 16px rgba(18,111,241,.15))}.brand-copy{display:flex;align-items:baseline;gap:10px}.brand-copy strong{font-size:25px;font-weight:760;letter-spacing:-.5px;color:#071b3b}.brand-copy span{font-size:14px;color:#71819b}
.intro-pane{position:relative;min-height:100dvh;display:flex;align-items:center;padding:120px 7vw 130px 12.3vw}.hero-copy{width:min(650px,100%);transform:translateY(18px);position:relative;z-index:2}.hero-copy h1{margin:0 0 24px;font-size:clamp(58px,4.9vw,83px);line-height:.98;letter-spacing:-3.6px;font-weight:750;color:#071b3b}.hero-copy h1 span{color:#0d6df2}.hero-copy h2{margin:0 0 26px;font-size:clamp(31px,2.45vw,43px);line-height:1.22;letter-spacing:-.7px;font-weight:710;color:#071b3b}.tagline{margin:0;font-size:clamp(23px,1.7vw,30px);line-height:1.45;font-weight:430;color:#62728f}.short-rule{width:49px;height:4px;margin:30px 0 24px;border-radius:2px;background:#126ff1}.subline{margin:0;font-size:clamp(18px,1.25vw,23px);line-height:1.6;color:#7f8faa;font-weight:420}
.decor-shape{position:absolute;left:-55px;bottom:-72px;width:410px;height:300px;transform:rotate(-8deg);pointer-events:none;opacity:.92}.decor-shape:before,.decor-shape:after,.decor-shape i{content:"";position:absolute;border-radius:50% 50% 44% 56%;border:1px solid rgba(13,109,242,.11)}.decor-shape:before{inset:0;background:linear-gradient(145deg,rgba(13,109,242,.08),rgba(13,109,242,.015));box-shadow:inset -35px 10px 80px rgba(13,109,242,.05)}.decor-shape:after{left:50px;top:45px;width:280px;height:205px}.decor-shape i:nth-child(1){left:100px;top:83px;width:180px;height:132px}.decor-shape i:nth-child(2){left:137px;top:110px;width:105px;height:75px;background:rgba(13,109,242,.05)}.decor-shape i:nth-child(3){left:18px;top:128px;width:345px;height:1px;border:0;border-top:1px solid rgba(13,109,242,.1);border-radius:0}
.login-pane{min-height:100dvh;display:flex;align-items:center;justify-content:center;padding:110px 7.5vw 110px 4vw}.login-card{width:min(548px,100%);padding:48px 44px 54px;background:#fff;border:1px solid #e6ecf5;border-radius:14px;box-shadow:0 20px 60px rgba(34,67,122,.08)}.card-title h2{margin:0 0 8px;font-size:37px;line-height:1.2;font-weight:750;letter-spacing:-.8px;color:#071b3b}.card-title p{margin:0 0 32px;color:#6c7b97;font-size:19px}.field{margin-bottom:24px}.field label{display:block;margin-bottom:9px;color:#142645;font-size:16px;font-weight:560}.input-wrap{position:relative}.input-wrap input{width:100%;height:54px;border:1px solid #cdd9eb;border-radius:7px;background:#fff;color:#203150;font-size:17px;padding:0 50px 0 18px;outline:none;transition:border-color .18s ease,box-shadow .18s ease}.input-wrap input::placeholder{color:#9aa8bf}.input-wrap input:focus{border-color:#2a78ed;box-shadow:0 0 0 3px rgba(42,120,237,.09)}.input-wrap input:disabled{background:#f6f8fb;cursor:not-allowed}.password-toggle{position:absolute;right:12px;top:50%;width:30px;height:30px;padding:5px;border:0;background:transparent;color:#7184a5;transform:translateY(-50%);cursor:pointer}.password-toggle svg{width:100%;height:100%}.password-toggle:hover{color:#126ff1}
.form-meta{display:flex;align-items:center;justify-content:space-between;gap:18px;margin:-2px 0 26px}.remember{display:inline-flex;align-items:center;gap:9px;color:#1b2d4b;font-size:16px;cursor:pointer;user-select:none}.remember input{width:20px;height:20px;margin:0;accent-color:#1373ef;cursor:pointer}.forgot{border:0;background:transparent;color:#086ff1;padding:4px 0;font-size:16px;cursor:pointer}.forgot:hover{text-decoration:underline}.login-button{width:100%;height:58px;display:flex;align-items:center;justify-content:center;gap:8px;border:0;border-radius:7px;background:#1c72ed;color:#fff;font-size:18px;font-weight:650;letter-spacing:.2px;cursor:pointer;transition:transform .12s ease,background .18s ease}.login-button:hover:not(:disabled){background:#0e66df}.login-button:active:not(:disabled){transform:translateY(1px)}.login-button:disabled{opacity:.72;cursor:default}.session-check{min-height:340px;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#536783}.session-check strong{margin-top:15px;font-size:16px}.session-check small{margin-top:5px;color:#8a99af}.spinner{width:28px;height:28px;border:3px solid rgba(28,114,237,.17);border-top-color:#1c72ed;border-radius:50%;animation:spin .75s linear infinite}.spinner.small{width:18px;height:18px;border-width:2px;border-color:rgba(255,255,255,.35);border-top-color:#fff}@keyframes spin{to{transform:rotate(360deg)}}
.modern-login-page footer{position:absolute;left:50%;bottom:34px;z-index:4;transform:translateX(-50%);font-size:14px;color:#7788a3;white-space:nowrap}
@media(min-width:1051px){.login-card{transform:translate(-38px,-30px)}}
@media(max-width:1500px){.top-brand{left:54px}.intro-pane{padding-left:10vw;padding-right:6vw}.login-pane{padding-right:5.5vw}.login-card{width:min(500px,100%);padding:44px 40px 48px}.hero-copy h1{font-size:clamp(54px,4.7vw,72px)}.hero-copy h2{font-size:clamp(29px,2.35vw,38px)}}
@media(max-height:820px) and (min-width:1051px){.top-brand{top:16px}.intro-pane{padding-top:92px;padding-bottom:92px}.hero-copy{transform:translateY(8px)}.hero-copy h1{margin-bottom:18px;font-size:clamp(48px,4.3vw,66px)}.hero-copy h2{margin-bottom:18px;font-size:clamp(27px,2.2vw,35px)}.tagline{font-size:clamp(20px,1.55vw,25px)}.short-rule{margin:22px 0 18px}.subline{font-size:clamp(16px,1.12vw,20px)}.login-pane{padding-top:78px;padding-bottom:78px}.login-card{padding:38px 38px 42px;transform:translate(-28px,-10px)}.card-title h2{font-size:33px}.card-title p{margin-bottom:26px;font-size:17px}.field{margin-bottom:18px}.input-wrap input{height:50px;font-size:16px}.form-meta{margin-bottom:22px}.login-button{height:54px}.modern-login-page footer{bottom:18px;font-size:12px}}
@media(max-height:680px) and (min-width:1051px){.intro-pane{padding-top:78px;padding-bottom:70px}.hero-copy h1{font-size:50px}.hero-copy h2{font-size:28px}.tagline{font-size:20px}.short-rule{margin:17px 0 14px}.subline{font-size:16px}.login-pane{padding-top:64px;padding-bottom:64px}.login-card{padding:30px 34px 34px}.card-title h2{font-size:30px}.card-title p{margin-bottom:20px}.field{margin-bottom:14px}.field label{margin-bottom:6px;font-size:14px}.input-wrap input{height:46px;font-size:15px}.form-meta{margin-bottom:17px}.remember,.forgot{font-size:14px}.login-button{height:49px;font-size:16px}.modern-login-page footer{bottom:10px;font-size:11px}}
@media(max-width:1050px){.modern-login-page{grid-template-columns:1fr;overflow:auto}.top-brand{left:34px;top:18px}.intro-pane{min-height:auto;padding:125px 7vw 48px;justify-content:center}.hero-copy{width:min(640px,100%);transform:none}.hero-copy h1{font-size:clamp(50px,10vw,74px)}.login-pane{min-height:auto;padding:16px 7vw 120px}.login-card{width:min(640px,100%);transform:none}.decor-shape{opacity:.48;width:280px;height:210px}.modern-login-page footer{bottom:30px}}
@media(max-width:600px){.top-brand{left:22px;top:14px;height:50px}.brand-mark{width:34px;height:34px}.brand-copy strong{font-size:22px}.brand-copy span{display:none}.intro-pane{padding:105px 24px 34px}.hero-copy h1{font-size:clamp(46px,14vw,62px)}.hero-copy h2{font-size:30px}.tagline{font-size:20px}.subline{font-size:17px}.login-pane{padding:10px 18px 100px}.login-card{padding:34px 24px 38px;border-radius:12px}.card-title h2{font-size:31px}.card-title p{font-size:17px;margin-bottom:28px}.form-meta{align-items:flex-start}.modern-login-page footer{font-size:12px;bottom:26px}}
@media(max-width:420px){.intro-pane{padding-left:18px;padding-right:18px}.hero-copy h1{font-size:46px;letter-spacing:-2.4px}.hero-copy h2{font-size:27px}.tagline{font-size:18px}.subline{font-size:15px}.login-pane{padding-left:14px;padding-right:14px}.login-card{padding:30px 20px 34px}.card-title h2{font-size:29px}.field label,.remember,.forgot{font-size:14px}.input-wrap input{height:50px;font-size:15px}.login-button{height:54px;font-size:17px}}
:global(html:has(.modern-login-page)),:global(body:has(.modern-login-page)),:global(#app:has(.modern-login-page)){min-width:0;width:100%;min-height:100%;overflow-x:hidden}
</style>
