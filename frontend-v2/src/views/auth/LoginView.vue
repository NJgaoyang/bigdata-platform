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
  }catch{ localStorage.removeItem('platform_auth_token') }
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
  }catch(e){ ElMessage.error(e instanceof Error?e.message:'登录失败，请检查账号或密码') }
  finally{ loading.value=false }
}

function forgotPassword(){
  ElMessageBox.alert('当前平台未开放自助密码找回，请联系平台管理员重置账号密码。','忘记密码',{confirmButtonText:'我知道了',type:'info'})
}
</script>

<template>
  <div class="login-page">
    <div class="bg-orbit orbit-one"></div><div class="bg-orbit orbit-two"></div>
    <div class="corner-copy"><i></i><span>DATA</span><span>CONNECTS</span><span>A BRIGHTER</span><span>TOMORROW</span></div>
    <main class="shell">
      <section class="left" aria-label="DataSphere 平台介绍">
        <div class="brand" aria-label="DataSphere">
          <svg class="brand-logo" viewBox="0 0 106 86" aria-hidden="true">
            <defs><linearGradient id="lg1" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#1f68ff"/><stop offset="1" stop-color="#78a8ff"/></linearGradient><linearGradient id="lg2" x1="0" y1="1" x2="1" y2="0"><stop stop-color="#6fa4ff"/><stop offset="1" stop-color="#2d72f3"/></linearGradient></defs>
            <path fill="url(#lg1)" d="M10 8h44c25 0 43 17 43 35S79 78 54 78H10V55h39c12 0 20-5 20-12s-8-12-20-12H10V8Z"/>
            <path fill="url(#lg2)" d="M10 31h39c12 0 20 5 20 12L45 66H10V55l23-24H10Z" opacity=".9"/>
            <path fill="#f8fbff" d="M10 31h28L19 55H10V31Z"/>
          </svg>
          <div class="brand-word"><strong>Data</strong><b>Sphere</b></div>
        </div>
        <h1>统一数据开发与治理平台</h1>
        <p class="sub">轻质感、克制而清晰的数据工作台体验<br>统一数据接入、开发、调度与治理</p>
        <div class="illustration-wrap" aria-hidden="true">
          <svg class="illustration" viewBox="0 0 950 430" role="presentation">
            <defs>
              <linearGradient id="base" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#ffffff" stop-opacity=".98"/><stop offset="1" stop-color="#dceaff" stop-opacity=".96"/></linearGradient>
              <linearGradient id="blue" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#91c1ff"/><stop offset="1" stop-color="#3978f3"/></linearGradient>
              <linearGradient id="cube" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#eef6ff"/><stop offset="1" stop-color="#72a8ff"/></linearGradient>
              <radialGradient id="glow"><stop stop-color="#8db9ff" stop-opacity=".24"/><stop offset="1" stop-color="#8db9ff" stop-opacity="0"/></radialGradient>
              <filter id="shadow"><feDropShadow dx="0" dy="18" stdDeviation="18" flood-color="#5f88c8" flood-opacity=".12"/></filter>
            </defs>
            <ellipse cx="430" cy="354" rx="430" ry="76" fill="url(#glow)"/>
            <g opacity=".45" stroke="#cfe0f7" fill="none"><path d="M26 357 412 184 914 318 532 423Z"/><path d="M94 383 475 213 845 311 478 414Z"/><path d="M155 400 521 239 783 309 430 410Z"/></g>
            <g filter="url(#shadow)">
              <path d="M242 300 438 215 677 281 487 377 242 334Z" fill="url(#base)" stroke="#d4e4fa"/>
              <path d="M289 263 448 196 625 244 467 324 289 296Z" fill="url(#base)" stroke="#d5e5fa"/>
              <path d="M342 231 455 183 573 217 461 273 342 254Z" fill="url(#base)" stroke="#d4e4fa"/>
            </g>
            <g stroke="#84adf3" stroke-width="2" fill="none" opacity=".75"><ellipse cx="458" cy="226" rx="148" ry="49"/><ellipse cx="458" cy="226" rx="95" ry="31" opacity=".55"/></g>
            <g transform="translate(388 146)" filter="url(#shadow)">
              <g fill="url(#cube)" stroke="#fff" stroke-opacity=".8"><path d="M62 0 111 22 62 46 14 22Z"/><path d="M14 22 62 46 62 101 14 77Z"/><path d="M111 22 62 46 62 101 111 76Z"/><path d="M8 54 48 72 8 92-31 73Z" opacity=".7"/><path d="M118 51 153 67 118 84 83 67Z" opacity=".55"/></g>
              <path d="M14 77 62 101 62 145 14 121Z" fill="#79aaf7" opacity=".46"/><path d="M111 76 62 101 62 145 111 120Z" fill="#3d7bed" opacity=".48"/>
            </g>
            <text x="429" y="348" font-size="12" fill="#8ba2c2">DataSphere</text>
            <g transform="translate(120 215)" filter="url(#shadow)"><rect width="92" height="104" rx="16" fill="#fff" fill-opacity=".82" stroke="#d4e3f8"/><rect x="20" y="15" width="52" height="49" rx="10" fill="#edf5ff"/><g fill="#5791f6"><rect x="30" y="42" width="7" height="15" rx="3"/><rect x="43" y="31" width="7" height="26" rx="3"/><rect x="56" y="22" width="7" height="35" rx="3"/></g><text x="24" y="82" font-size="13" font-weight="700" fill="#416b9f">数据接入</text></g>
            <g transform="translate(614 133)" filter="url(#shadow)"><rect width="92" height="104" rx="16" fill="#fff" fill-opacity=".82" stroke="#d4e3f8"/><rect x="20" y="15" width="52" height="49" rx="10" fill="#edf5ff"/><text x="31" y="49" font-size="22" font-weight="800" fill="#4d86f5">&lt;/&gt;</text><text x="24" y="82" font-size="13" font-weight="700" fill="#416b9f">数据开发</text></g>
            <g transform="translate(746 235)" filter="url(#shadow)"><rect width="92" height="104" rx="16" fill="#fff" fill-opacity=".82" stroke="#d4e3f8"/><circle cx="34" cy="39" r="7" fill="#4f89f5"/><circle cx="57" cy="28" r="7" fill="#4f89f5"/><circle cx="59" cy="52" r="7" fill="#4f89f5"/><path d="M40 36 51 31M40 42 52 49" stroke="#7ca9ef" stroke-width="3"/><text x="24" y="82" font-size="13" font-weight="700" fill="#416b9f">任务调度</text></g>
            <g transform="translate(598 304)" filter="url(#shadow)"><rect width="92" height="104" rx="16" fill="#fff" fill-opacity=".82" stroke="#d4e3f8"/><path d="M46 17 65 24v16c0 14-9 23-19 28-10-5-19-14-19-28V24l19-7Z" fill="url(#blue)"/><path d="m38 41 6 6 11-14" fill="none" stroke="#fff" stroke-width="4" stroke-linecap="round"/><text x="24" y="82" font-size="13" font-weight="700" fill="#416b9f">数据治理</text></g>
            <g transform="translate(160 333)"><ellipse cx="52" cy="0" rx="50" ry="18" fill="#eef6ff" stroke="#d3e3f8"/><path d="M2 0v48c0 10 22 18 50 18s50-8 50-18V0" fill="#e7f1ff" stroke="#d3e3f8"/><ellipse cx="52" cy="24" rx="50" ry="18" fill="none" stroke="#d3e3f8"/><ellipse cx="52" cy="48" rx="50" ry="18" fill="none" stroke="#d3e3f8"/></g>
            <g fill="#e8f2ff" stroke="#d1e1f6"><circle cx="70" cy="320" r="19"/><path d="M70 337v39"/><circle cx="875" cy="323" r="18"/><path d="M875 340v37"/><circle cx="805" cy="199" r="14"/><path d="M805 213v30"/></g>
            <g stroke="#82aef4" stroke-width="2" opacity=".65"><path d="M205 255 350 228"/><path d="M563 219 615 187"/><path d="M591 268 748 276"/><path d="M548 316 599 343"/></g>
          </svg>
        </div>
        <div class="left-foot"><span>数 据 让 业 务 更 有 可 能</span><i></i></div>
      </section>
      <section class="card" aria-label="登录表单">
        <div v-if="checkingSession" class="session-check"><span class="spinner"></span><strong>正在验证登录状态</strong><small>请稍候...</small></div>
        <template v-else>
          <h2>登录</h2><div class="welcome">欢迎回来，使用 DataSphere</div>
          <form @submit.prevent="submit" novalidate>
            <div class="field"><label for="account">账号</label><div class="input-box"><span class="user-icon"></span><input id="account" v-model="form.username" autocomplete="username" placeholder="请输入账号" :disabled="loading" /></div></div>
            <div class="field"><label for="password">密码</label><div class="input-box"><span class="lock-icon"></span><input id="password" v-model="form.password" :type="passwordVisible?'text':'password'" autocomplete="current-password" placeholder="请输入密码" :disabled="loading" /><button class="eye" type="button" @click="passwordVisible=!passwordVisible"><span :class="{off:!passwordVisible}"></span></button></div></div>
            <div class="row"><label class="remember"><input v-model="rememberUsername" type="checkbox" />记住我</label><button class="link" type="button" @click="forgotPassword">忘记密码？</button></div>
            <button class="login" type="submit" :disabled="loading"><span v-if="loading" class="spinner small"></span><span>{{loading?'登录中...':'登 录'}}</span></button>
          </form>
        </template>
      </section>
    </main>
    <div class="copyright"><i></i><span>© {{year}} DataSphere · 用数据创造更大的价值</span><i></i></div>
  </div>
</template>

<style scoped>
.login-page{--blue:#2f6ff7;--ink:#0c234a;--muted:#7487a8;--line:#dfe7f4;--shadow:0 24px 70px rgba(63,104,173,.12);position:relative;min-height:100vh;overflow:hidden;color:var(--ink);background:linear-gradient(180deg,#fbfdff 0%,#f5f9ff 100%)}
.login-page:before{content:"";position:absolute;inset:0;background:radial-gradient(circle at 88% 8%,rgba(85,139,244,.13),transparent 28%),radial-gradient(circle at 15% 80%,rgba(76,133,255,.1),transparent 26%);pointer-events:none}.bg-orbit{position:absolute;border:2px solid rgba(92,145,230,.075);border-radius:50%;pointer-events:none}.orbit-one{width:990px;height:990px;right:-180px;top:-620px}.orbit-two{width:760px;height:760px;right:-220px;top:-430px}.corner-copy{position:absolute;right:5.6vw;top:5vh;z-index:3;display:flex;flex-direction:column;gap:7px;color:#a1b2cc;font-size:10px;letter-spacing:2.4px;line-height:1}.corner-copy i{width:26px;height:1px;margin-bottom:5px;background:#9eb3d0}
.shell{position:relative;z-index:2;width:min(1450px,calc(100vw - 72px));height:100vh;margin:0 auto;display:grid;grid-template-columns:minmax(0,1.45fr) minmax(440px,.85fr);gap:70px;align-items:center}.left{position:relative;align-self:stretch;padding:112px 0 56px;display:flex;flex-direction:column;min-width:0}.brand{width:510px;max-width:68%;height:115px;display:flex;align-items:center;gap:20px;margin-bottom:18px}.brand-logo{width:106px;height:86px;flex:none;filter:drop-shadow(0 10px 22px rgba(47,111,247,.13))}.brand-word{display:flex;align-items:baseline;font-size:56px;line-height:1;font-weight:800;letter-spacing:-2.5px;white-space:nowrap}.brand-word strong{color:#0a2857}.brand-word b{color:#3478f6;font-weight:800}.left h1{font-size:44px;line-height:1.2;font-weight:760;letter-spacing:-1.5px;margin:0 0 18px}.sub{font-size:22px;line-height:1.65;color:#778aa9;margin:0;max-width:680px}.illustration-wrap{position:relative;flex:1;min-height:390px;margin-top:10px}.illustration{position:absolute;left:-120px;bottom:-10px;width:min(940px,105%);height:auto;display:block;filter:drop-shadow(0 24px 36px rgba(63,113,203,.07))}.left-foot{display:flex;align-items:center;gap:18px;font-size:14px;letter-spacing:8px;color:#9fb0cb;margin-left:4px;margin-top:auto;white-space:nowrap}.left-foot i{width:96px;height:1px;background:#cbd7e8}
.card{width:100%;max-width:580px;justify-self:end;background:rgba(255,255,255,.96);border:1px solid rgba(221,230,243,.92);border-radius:24px;box-shadow:var(--shadow);padding:54px 60px 60px;backdrop-filter:blur(10px)}.card h2{font-size:40px;line-height:1.15;margin:0 0 12px;font-weight:800}.welcome{font-size:21px;color:#7487a8;margin-bottom:36px}.field{margin-bottom:22px}.field>label{display:block;font-size:18px;margin-bottom:10px;color:#223d68;font-weight:600}.input-box{position:relative;height:64px}.input-box input{width:100%;height:100%;border:1px solid #cfd9eb;border-radius:8px;background:#fff;padding:0 58px;font-size:18px;color:#20375f;outline:none;transition:.2s}.input-box input::placeholder{color:#a5b4ca}.input-box input:focus{border-color:#6d9cff;box-shadow:0 0 0 4px rgba(47,111,247,.1)}.input-box input:disabled{background:#f7f9fc}.user-icon,.lock-icon{position:absolute;left:20px;top:50%;width:21px;height:21px;transform:translateY(-50%);z-index:2}.user-icon:before{content:"";position:absolute;left:6px;top:0;width:8px;height:8px;border:2px solid #7892b9;border-radius:50%}.user-icon:after{content:"";position:absolute;left:2px;bottom:0;width:16px;height:9px;border:2px solid #7892b9;border-bottom:0;border-radius:10px 10px 0 0}.lock-icon:before{content:"";position:absolute;left:2px;top:8px;width:17px;height:13px;border:2px solid #7892b9;border-radius:3px}.lock-icon:after{content:"";position:absolute;left:6px;top:1px;width:9px;height:10px;border:2px solid #7892b9;border-bottom:0;border-radius:8px 8px 0 0}.eye{position:absolute;right:13px;top:50%;width:38px;height:38px;transform:translateY(-50%);border:0;background:transparent;cursor:pointer}.eye span{position:absolute;left:9px;top:12px;width:20px;height:13px;border:2px solid #7892b9;border-radius:50%}.eye span:after{content:"";position:absolute;left:6px;top:3px;width:4px;height:4px;border:2px solid #7892b9;border-radius:50%}.eye span.off:before{content:"";position:absolute;left:-4px;top:4px;width:27px;height:2px;background:#7892b9;transform:rotate(40deg)}.row{display:flex;align-items:center;justify-content:space-between;margin:6px 0 30px;gap:20px}.remember{display:flex;align-items:center;gap:10px;font-size:17px;color:#31496f;cursor:pointer}.remember input{width:20px;height:20px;accent-color:var(--blue)}.link{border:0;background:none;color:#1e6df4;font-size:17px;cursor:pointer;padding:0}.login{width:100%;height:64px;display:flex;align-items:center;justify-content:center;gap:10px;border:0;border-radius:8px;background:#2f70f3;color:#fff;font-weight:700;font-size:20px;letter-spacing:10px;cursor:pointer;box-shadow:0 10px 24px rgba(47,112,243,.18);transition:.2s}.login:hover:not(:disabled){transform:translateY(-1px);background:#2767e6}.login:disabled{opacity:.72;cursor:not-allowed}.session-check{min-height:360px;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#607695}.session-check strong{margin-top:18px;font-size:17px}.session-check small{margin-top:6px;color:#96a6be}.spinner{width:30px;height:30px;border:3px solid rgba(47,111,247,.18);border-top-color:#2f6ff7;border-radius:50%;animation:spin .75s linear infinite}.spinner.small{width:18px;height:18px;border-width:2px;border-color:rgba(255,255,255,.35);border-top-color:#fff}@keyframes spin{to{transform:rotate(360deg)}}.copyright{position:absolute;left:50%;bottom:26px;z-index:3;display:flex;align-items:center;gap:20px;transform:translateX(-50%);font-size:13px;color:#97a8c2;white-space:nowrap}.copyright i{width:36px;height:1px;background:#c5d3e6}
@media(max-width:1500px){.shell{width:min(1320px,calc(100vw - 56px));gap:48px;grid-template-columns:minmax(0,1.35fr) minmax(420px,.82fr)}.left{padding-top:76px}.brand{width:430px;height:96px;margin-bottom:12px}.brand-logo{width:88px;height:72px}.brand-word{font-size:47px}.left h1{font-size:40px}.sub{font-size:19px}.illustration-wrap{min-height:330px}.illustration{left:-95px;width:min(820px,106%)}.card{max-width:530px;padding:46px 50px 50px}.card h2{font-size:36px}.welcome{font-size:18px;margin-bottom:30px}.input-box,.login{height:58px}.field>label{font-size:16px}}
@media(max-height:820px) and (min-width:1101px){.shell{gap:44px}.left{padding-top:42px;padding-bottom:36px}.brand{width:360px;height:82px;margin-bottom:8px}.brand-logo{width:74px;height:62px}.brand-word{font-size:40px}.left h1{font-size:34px;margin-bottom:10px}.sub{font-size:17px;line-height:1.48}.illustration-wrap{min-height:260px;margin-top:0}.illustration{left:-72px;bottom:-4px;width:min(700px,106%)}.left-foot{font-size:11px;letter-spacing:6px}.card{max-width:500px;padding:36px 44px 40px}.card h2{font-size:32px}.welcome{font-size:17px;margin-bottom:24px}.field{margin-bottom:16px}.field>label{font-size:15px;margin-bottom:7px}.input-box,.login{height:52px}.input-box input{font-size:16px}.row{margin:2px 0 20px}.remember,.link{font-size:14px}.corner-copy{top:3vh}.copyright{bottom:12px;font-size:11px}}
@media(max-height:680px) and (min-width:1101px){.left{padding-top:24px}.brand{width:310px;height:68px}.brand-logo{width:64px;height:52px}.brand-word{font-size:34px}.left h1{font-size:30px}.sub{font-size:15px}.illustration-wrap{min-height:190px}.illustration{width:min(590px,104%);left:-58px}.left-foot{display:none}.card{max-width:450px;padding:27px 36px 30px}.card h2{font-size:28px}.welcome{font-size:15px;margin-bottom:17px}.field{margin-bottom:11px}.input-box,.login{height:47px}.row{margin:0 0 14px}.copyright{bottom:8px}.corner-copy{display:none}}
@media(max-width:1100px){.login-page{overflow:auto}.corner-copy{display:none}.shell{height:auto;min-height:100vh;grid-template-columns:1fr;width:min(760px,calc(100vw - 40px));gap:24px;padding:52px 0 90px}.left{padding:0;align-self:auto}.brand{max-width:72%;width:410px;height:92px}.brand-logo{width:84px;height:70px}.brand-word{font-size:44px}.left h1{font-size:36px}.sub{font-size:18px}.illustration-wrap{min-height:290px}.illustration{position:relative;left:-30px;bottom:auto;width:110%;margin-top:10px}.left-foot{display:none}.card{justify-self:center;max-width:620px}.copyright{position:absolute;bottom:18px}}
@media(max-width:640px){.shell{width:calc(100vw - 28px);padding-top:32px}.brand{max-width:84%;width:330px;height:75px;gap:12px}.brand-logo{width:68px;height:58px}.brand-word{font-size:36px}.left h1{font-size:30px}.sub{font-size:16px}.illustration-wrap{min-height:0}.illustration{left:-18px;width:112%}.card{padding:34px 24px;border-radius:18px}.card h2{font-size:34px}.welcome{font-size:17px;margin-bottom:28px}.input-box,.login{height:56px}.row{align-items:flex-start}.remember,.link{font-size:15px}.copyright{font-size:11px;gap:10px}.copyright i{width:20px}}
@media(max-width:420px){.brand{width:290px}.brand-logo{width:58px;height:50px}.brand-word{font-size:31px}.left h1{font-size:27px}.sub{font-size:14px}.card{padding:28px 18px}.copyright{display:none}}
:global(html:has(.login-page)),:global(body:has(.login-page)),:global(#app:has(.login-page)){min-width:0;width:100%;min-height:100%;overflow-x:hidden}
</style>
