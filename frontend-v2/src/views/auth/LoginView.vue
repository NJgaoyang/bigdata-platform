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
  <div class="login-page">
    <main class="shell">
      <section class="left" aria-label="DataSphere 平台介绍">
        <div class="brand-lockup">
          <svg class="brand-mark" viewBox="0 0 42 42" fill="none" aria-hidden="true">
            <path d="M21 3 36 12v18L21 39 6 30V12L21 3Z" fill="#3778F6"/>
            <path d="m21 8 10 6-10 6-10-6 10-6Z" fill="#75B9FF"/>
            <path d="m11 17 10 6v11l-10-6V17Z" fill="#285DCE"/>
            <path d="m31 17-10 6v11l10-6V17Z" fill="#3E96FF"/>
          </svg>
          <div class="brand-copy"><strong>DataSphere</strong><span>数据开发平台</span></div>
        </div>

        <h1 class="headline">统一数据开发与治理平台</h1>
        <p class="sub">轻质感、克制而清晰的数据工作台体验<br>统一数据接入、开发、调度与治理</p>

        <div class="illustration-wrap" aria-hidden="true">
          <div class="platform-shadow"></div>
          <div class="platform-base base-back"></div>
          <div class="platform-base base-mid"></div>
          <div class="platform-base base-front"></div>

          <div class="scene-node node-source">
            <span class="node-icon">DB</span><b>数据接入</b><small>MySQL · StarRocks</small>
          </div>
          <div class="scene-node node-dev">
            <span class="node-icon">SQL</span><b>数据开发</b><small>SQL · 版本 · 发布</small>
          </div>
          <div class="scene-node node-schedule">
            <span class="node-icon">JOB</span><b>任务调度</b><small>周期 · 依赖 · 运维</small>
          </div>
          <div class="scene-node node-govern">
            <span class="node-icon">META</span><b>数据治理</b><small>元数据 · 血缘 · 审计</small>
          </div>

          <div class="core-stack">
            <div class="core-top"></div>
            <div class="core-body"><strong>DataSphere</strong><span>统一数据生产工作台</span></div>
          </div>
          <i class="line l1"></i><i class="line l2"></i><i class="line l3"></i><i class="line l4"></i>
        </div>

        <div class="left-foot">DATA DEVELOPMENT PLATFORM</div>
      </section>

      <section class="card" aria-label="登录表单">
        <div v-if="checkingSession" class="session-check">
          <span class="spinner"></span>
          <strong>正在验证登录状态</strong>
          <small>请稍候...</small>
        </div>

        <template v-else>
          <h2>登录</h2>
          <div class="welcome">欢迎回来，使用 DataSphere</div>
          <form @submit.prevent="submit" novalidate>
            <div class="field">
              <label class="label" for="account">账号</label>
              <input id="account" v-model="form.username" class="input" autocomplete="username" placeholder="请输入账号" :disabled="loading" />
            </div>
            <div class="field">
              <label class="label" for="password">密码</label>
              <div class="password-wrap">
                <input id="password" v-model="form.password" class="input" :type="passwordVisible?'text':'password'" autocomplete="current-password" placeholder="请输入密码" :disabled="loading" />
                <button class="toggle" type="button" @click="passwordVisible=!passwordVisible">{{passwordVisible?'隐藏':'显示'}}</button>
              </div>
            </div>
            <div class="row">
              <label class="remember"><input v-model="rememberUsername" type="checkbox" />记住账号</label>
              <button class="link" type="button" @click="forgotPassword">忘记密码？</button>
            </div>
            <button class="login" type="submit" :disabled="loading">
              <span v-if="loading" class="spinner small"></span>
              <span>{{loading?'登录中...':'登录'}}</span>
            </button>
          </form>
        </template>
      </section>
    </main>
    <div class="copyright">© {{year}} DataSphere · 用数据创造更大的价值</div>
  </div>
</template>

<style scoped>
.login-page{--blue:#2f6ff7;--ink:#0c234a;--muted:#7487a8;--shadow:0 24px 70px rgba(63,104,173,.12);position:relative;min-height:100vh;overflow:hidden;color:var(--ink);background:linear-gradient(180deg,#fbfdff 0%,#f5f9ff 100%)}
.login-page:before{content:"";position:absolute;inset:0;background:radial-gradient(circle at 88% 8%,rgba(85,139,244,.13),transparent 28%),radial-gradient(circle at 15% 80%,rgba(76,133,255,.1),transparent 26%);pointer-events:none}
.shell{position:relative;z-index:1;width:min(1450px,calc(100vw - 72px));height:100vh;margin:0 auto;display:grid;grid-template-columns:minmax(0,1.45fr) minmax(440px,.85fr);gap:70px;align-items:center}
.left{position:relative;align-self:stretch;padding:94px 0 56px;display:flex;flex-direction:column;min-width:0}
.brand-lockup{display:flex;align-items:center;gap:15px;margin-bottom:28px}.brand-mark{width:46px;height:46px;filter:drop-shadow(0 8px 18px rgba(47,111,247,.18))}.brand-copy{display:flex;align-items:baseline;gap:14px}.brand-copy strong{font-size:34px;line-height:1;font-weight:820;letter-spacing:-1px;color:#12315f}.brand-copy span{font-size:18px;color:#7d90ae}
.headline{font-size:44px;line-height:1.2;font-weight:760;letter-spacing:-1.5px;margin:0 0 18px}.sub{font-size:22px;line-height:1.65;color:#778aa9;margin:0;max-width:680px}
.illustration-wrap{position:relative;flex:1;min-height:390px;margin-top:10px}.platform-shadow{position:absolute;left:4%;bottom:1%;width:78%;height:90px;border-radius:50%;background:radial-gradient(ellipse,rgba(83,128,210,.16),transparent 68%);filter:blur(10px)}.platform-base{position:absolute;left:11%;width:65%;height:88px;border:1px solid rgba(98,151,235,.28);border-radius:22px;transform:skewX(-18deg);background:linear-gradient(145deg,rgba(255,255,255,.96),rgba(222,236,255,.72));box-shadow:0 18px 38px rgba(66,112,191,.08)}.base-back{bottom:134px;opacity:.5}.base-mid{bottom:86px;left:7%;opacity:.72}.base-front{bottom:35px;left:3%}
.scene-node{position:absolute;width:175px;padding:13px 14px;border-radius:14px;background:rgba(255,255,255,.86);border:1px solid rgba(178,204,241,.82);box-shadow:0 14px 34px rgba(65,108,178,.09);backdrop-filter:blur(8px)}.scene-node b,.scene-node small{display:block}.scene-node b{margin:8px 0 4px;font-size:14px;color:#183967}.scene-node small{font-size:10px;color:#8296b6}.node-icon{display:grid;place-items:center;width:34px;height:34px;border-radius:10px;background:#edf5ff;color:#3778f6;font-size:9px;font-weight:800;letter-spacing:.4px}.node-source{left:0;bottom:102px}.node-dev{left:27%;bottom:205px}.node-schedule{right:13%;bottom:178px}.node-govern{right:0;bottom:66px}
.core-stack{position:absolute;left:42%;bottom:60px;width:210px;height:150px}.core-top{position:absolute;left:50%;top:0;width:86px;height:50px;transform:translateX(-50%) skewX(-22deg);border-radius:12px;background:linear-gradient(135deg,#90c8ff,#4f8cf8);box-shadow:0 16px 28px rgba(71,133,233,.2)}.core-body{position:absolute;left:0;right:0;bottom:0;height:105px;display:flex;flex-direction:column;align-items:center;justify-content:center;border-radius:18px;background:linear-gradient(180deg,rgba(248,252,255,.96),rgba(222,237,255,.92));border:1px solid rgba(132,174,235,.45);box-shadow:0 18px 40px rgba(65,110,186,.12)}.core-body strong{font-size:18px;color:#24508e}.core-body span{margin-top:7px;font-size:11px;color:#8294af}
.line{position:absolute;height:1px;background:linear-gradient(90deg,rgba(68,139,240,.08),rgba(68,139,240,.7),rgba(68,139,240,.08));transform-origin:left center}.l1{left:18%;bottom:160px;width:190px;transform:rotate(-8deg)}.l2{left:40%;bottom:235px;width:155px;transform:rotate(20deg)}.l3{left:56%;bottom:195px;width:180px;transform:rotate(-7deg)}.l4{left:58%;bottom:112px;width:210px;transform:rotate(8deg)}
.left-foot{font-size:14px;letter-spacing:8px;color:#9fb0cb;margin-left:4px;margin-top:auto}
.card{width:100%;max-width:580px;justify-self:end;background:rgba(255,255,255,.96);border:1px solid rgba(221,230,243,.92);border-radius:24px;box-shadow:var(--shadow);padding:54px 60px 60px;backdrop-filter:blur(10px)}.card h2{font-size:40px;line-height:1.15;margin:0 0 12px;font-weight:800}.welcome{font-size:21px;color:#7487a8;margin-bottom:36px}.field{margin-bottom:22px}.label{display:block;font-size:18px;margin-bottom:10px;color:#223d68}.input{width:100%;height:64px;border:1px solid #cfd9eb;border-radius:8px;background:#fff;padding:0 18px;font-size:18px;color:#20375f;outline:none;transition:.2s}.input:focus{border-color:#6d9cff;box-shadow:0 0 0 4px rgba(47,111,247,.1)}.input:disabled{background:#f6f8fb}.password-wrap{position:relative}.password-wrap .input{padding-right:64px}.toggle{position:absolute;right:13px;top:50%;transform:translateY(-50%);border:0;background:transparent;color:#6f83a7;font-size:14px;cursor:pointer;padding:8px 6px}.row{display:flex;align-items:center;justify-content:space-between;margin:6px 0 30px;gap:20px}.remember{display:flex;align-items:center;gap:10px;font-size:17px;color:#31496f;cursor:pointer}.remember input{width:20px;height:20px;accent-color:var(--blue)}.link{border:0;background:none;color:#1e6df4;font-size:17px;cursor:pointer;padding:0}.login{width:100%;height:64px;display:flex;align-items:center;justify-content:center;gap:10px;border:0;border-radius:8px;background:#2f70f3;color:#fff;font-weight:700;font-size:20px;letter-spacing:8px;cursor:pointer;box-shadow:0 10px 24px rgba(47,112,243,.18);transition:.2s}.login:hover:not(:disabled){transform:translateY(-1px);background:#2767e6}.login:disabled{opacity:.72;cursor:not-allowed}.session-check{min-height:360px;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#607695}.session-check strong{margin-top:18px;font-size:17px}.session-check small{margin-top:6px;color:#96a6be}.spinner{width:30px;height:30px;border:3px solid rgba(47,111,247,.18);border-top-color:#2f6ff7;border-radius:50%;animation:spin .75s linear infinite}.spinner.small{width:18px;height:18px;border-width:2px;border-color:rgba(255,255,255,.35);border-top-color:#fff}@keyframes spin{to{transform:rotate(360deg)}}
.copyright{position:absolute;left:50%;bottom:26px;transform:translateX(-50%);font-size:13px;color:#97a8c2;white-space:nowrap}
@media(max-width:1500px){.shell{width:min(1320px,calc(100vw - 56px));gap:48px;grid-template-columns:minmax(0,1.35fr) minmax(420px,.82fr)}.left{padding-top:76px}.headline{font-size:40px}.sub{font-size:19px}.illustration-wrap{min-height:340px}.card{max-width:530px;padding:46px 50px 50px}.card h2{font-size:36px}.welcome{font-size:18px;margin-bottom:30px}.input,.login{height:58px}}
@media(max-height:820px) and (min-width:1101px){.shell{gap:44px}.left{padding-top:44px;padding-bottom:38px}.brand-lockup{margin-bottom:20px}.brand-mark{width:40px;height:40px}.brand-copy strong{font-size:29px}.brand-copy span{font-size:15px}.headline{font-size:34px;margin-bottom:12px}.sub{font-size:17px;line-height:1.5}.illustration-wrap{min-height:280px;margin-top:2px}.scene-node{transform:scale(.85);transform-origin:center}.node-source{bottom:72px}.node-dev{bottom:160px}.node-schedule{bottom:142px}.node-govern{bottom:45px}.core-stack{transform:scale(.82);transform-origin:center bottom;bottom:40px}.platform-base{transform:skewX(-18deg) scale(.9);transform-origin:left bottom}.left-foot{font-size:11px;letter-spacing:6px}.card{max-width:500px;padding:36px 44px 40px}.card h2{font-size:32px}.welcome{font-size:17px;margin-bottom:24px}.field{margin-bottom:16px}.label{font-size:15px;margin-bottom:7px}.input,.login{height:52px;font-size:16px}.row{margin:2px 0 20px}.remember,.link{font-size:14px}}
@media(max-height:680px) and (min-width:1101px){.left{padding-top:28px}.brand-lockup{margin-bottom:14px}.headline{font-size:30px}.sub{font-size:15px}.illustration-wrap{min-height:210px}.scene-node{transform:scale(.72)}.node-source{bottom:40px}.node-dev{bottom:115px}.node-schedule{bottom:102px}.node-govern{bottom:28px}.core-stack{transform:scale(.68);bottom:22px}.platform-base{transform:skewX(-18deg) scale(.78)}.left-foot{display:none}.card{padding:28px 40px 30px}.card h2{font-size:28px}.welcome{margin-bottom:18px}.field{margin-bottom:12px}.input,.login{height:48px}.row{margin:0 0 16px}.copyright{bottom:10px;font-size:11px}}
@media(max-width:1100px){.login-page{overflow:auto}.shell{height:auto;min-height:100vh;grid-template-columns:1fr;width:min(760px,calc(100vw - 40px));gap:24px;padding:52px 0 90px}.left{padding:0;align-self:auto}.brand-lockup{margin-bottom:22px}.headline{font-size:36px}.sub{font-size:18px}.illustration-wrap{min-height:290px}.scene-node{transform:scale(.86)}.node-source{left:1%;bottom:70px}.node-dev{left:25%;bottom:170px}.node-schedule{right:12%;bottom:154px}.node-govern{right:0;bottom:54px}.core-stack{transform:scale(.82);bottom:38px}.left-foot{display:none}.card{justify-self:center;max-width:620px}.copyright{position:absolute;bottom:18px}}
@media(max-width:640px){.shell{width:calc(100vw - 28px);padding-top:32px}.brand-lockup{gap:10px;margin-bottom:18px}.brand-mark{width:36px;height:36px}.brand-copy{gap:8px}.brand-copy strong{font-size:25px}.brand-copy span{font-size:13px}.headline{font-size:30px}.sub{font-size:16px}.illustration-wrap{min-height:210px}.scene-node{display:none}.core-stack{left:50%;transform:translateX(-50%) scale(.8);bottom:26px}.platform-base{left:12%;width:76%;transform:skewX(-18deg) scale(.8)}.card{padding:34px 24px;border-radius:18px}.card h2{font-size:34px}.welcome{font-size:17px;margin-bottom:28px}.input,.login{height:56px}.row{align-items:flex-start}.remember,.link{font-size:15px}.copyright{font-size:11px}}
@media(max-width:420px){.shell{width:calc(100vw - 20px);padding-top:24px}.left{padding:0 4px}.brand-copy span{display:none}.headline{font-size:27px}.sub{font-size:14px}.illustration-wrap{min-height:180px}.card{padding:28px 18px 30px}.card h2{font-size:30px}.welcome{font-size:15px;margin-bottom:22px}.field{margin-bottom:16px}.label{font-size:15px}.input{font-size:15px}.row{margin-bottom:22px}.copyright{bottom:10px;font-size:10px}}
:global(html:has(.login-page)),:global(body:has(.login-page)),:global(#app:has(.login-page)){min-width:0;width:100%;min-height:100%;overflow-x:hidden}
</style>
