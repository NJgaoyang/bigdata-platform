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
    <div class="grid-layer"></div>
    <div class="glow glow-one"></div>
    <div class="glow glow-two"></div>

    <header class="login-header">
      <div class="brand">
        <svg class="brand-logo" viewBox="0 0 40 40" fill="none" aria-hidden="true">
          <path d="M20 3 34 11v18L20 37 6 29V11L20 3Z" fill="#2F7CFF"/>
          <path d="m20 8 9 5-9 5-9-5 9-5Z" fill="#63B8FF"/>
          <path d="m11 16 9 5v11l-9-5V16Z" fill="#1D59C8"/>
          <path d="m29 16-9 5v11l9-5V16Z" fill="#2E96FF"/>
        </svg>
        <strong>DataSphere</strong>
        <span>数据开发平台</span>
      </div>
      <div class="header-values"><span>稳定</span><span>安全</span><span>高效</span><span>统一数据生产</span></div>
    </header>

    <main class="login-main">
      <section class="hero-panel">
        <div class="eyebrow">ENTERPRISE DATA DEVELOPMENT PLATFORM</div>
        <h1>DataSphere <span>数据开发平台</span></h1>
        <p class="hero-subtitle">统一数据接入、研发、调度、运维与治理</p>

        <div class="feature-row">
          <article class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><ellipse cx="12" cy="5" rx="7" ry="3"/><path d="M5 5v6c0 1.7 3.1 3 7 3s7-1.3 7-3V5M5 11v6c0 1.7 3.1 3 7 3s7-1.3 7-3v-6"/></svg>
            </div>
            <div><h3>统一数据接入</h3><p>MySQL 业务数据统一接入 StarRocks 数据仓库。</p></div>
          </article>
          <article class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="m8 8-4 4 4 4M16 8l4 4-4 4M14 5l-4 14"/></svg>
            </div>
            <div><h3>一站式数据开发</h3><p>SQL 开发、版本管理、调度配置与发布流程统一协作。</p></div>
          </article>
          <article class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="6" cy="7" r="2.5"/><circle cx="18" cy="7" r="2.5"/><circle cx="12" cy="18" r="2.5"/><path d="M8.5 7h7M7.5 9l3.2 6.6M16.5 9l-3.2 6.6"/></svg>
            </div>
            <div><h3>生产调度运维</h3><p>周期调度、依赖编排、运行监控与告警统一管理。</p></div>
          </article>
        </div>

        <div class="data-scene" aria-hidden="true">
          <div class="scene-orbit"></div>
          <div class="scene-beam"></div>
          <div class="scene-core"><span></span></div>
          <div class="scene-card scene-source">
            <b>数据接入</b><small>MySQL</small><i></i><small>SeaTunnel / Flink CDC</small>
          </div>
          <div class="scene-card scene-dev">
            <b>数据研发</b><small>SQL Development</small><small>版本 · 调度 · 发布</small>
          </div>
          <div class="scene-card scene-warehouse">
            <b>数据仓库</b><small>StarRocks</small><div class="mini-bars"><i></i><i></i><i></i><i></i></div>
          </div>
          <div class="scene-card scene-governance">
            <b>数据治理</b><small>元数据　● 正常</small><small>血缘　　● 完整</small><small>审计　　● 已启用</small>
          </div>
        </div>
      </section>

      <section class="login-side">
        <div class="login-card">
          <div v-if="checkingSession" class="session-check">
            <span class="spinner"></span>
            <strong>正在验证登录状态</strong>
            <small>请稍候...</small>
          </div>

          <template v-else>
            <div class="card-head">
              <div><h2>账号登录</h2></div>
            </div>

            <form class="login-form" @submit.prevent="submit">
              <label class="login-field">
                <span class="field-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="8" r="3.5"/><path d="M5 20c.7-4.2 3.1-6.2 7-6.2s6.3 2 7 6.2"/></svg>
                </span>
                <input v-model="form.username" autocomplete="username" placeholder="请输入账号" :disabled="loading" />
              </label>

              <label class="login-field">
                <span class="field-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="5" y="10" width="14" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>
                </span>
                <input v-model="form.password" :type="passwordVisible?'text':'password'" autocomplete="current-password" placeholder="请输入密码" :disabled="loading" />
                <button class="password-toggle" type="button" :title="passwordVisible?'隐藏密码':'显示密码'" @click="passwordVisible=!passwordVisible">
                  <svg v-if="!passwordVisible" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M2.5 12s3.2-5 9.5-5 9.5 5 9.5 5-3.2 5-9.5 5-9.5-5-9.5-5Z"/><circle cx="12" cy="12" r="2.5"/></svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m3 3 18 18M10.6 7.2A10.8 10.8 0 0 1 12 7c6.3 0 9.5 5 9.5 5a14 14 0 0 1-2.4 2.8M6.2 6.2C3.8 7.8 2.5 12 2.5 12s3.2 5 9.5 5c1 0 1.9-.1 2.7-.4"/></svg>
                </button>
              </label>

              <div class="form-options">
                <label class="remember"><input v-model="rememberUsername" type="checkbox" /><span>记住账号</span></label>
                <button type="button" class="forgot" @click="forgotPassword">忘记密码？</button>
              </div>

              <button class="login-button" type="submit" :disabled="loading">
                <span v-if="loading" class="spinner small"></span>
                <span>{{loading?'登录中...':'登 录'}}</span>
              </button>
            </form>

            <div class="trust-line"><span></span><em>安全 · 专业 · 可信赖</em><span></span></div>
            <div class="card-foot">DataSphere · 企业数据生产与研发工作台</div>
          </template>
        </div>
      </section>
    </main>

    <div class="slogan"><strong>用数据驱动业务</strong><span>让数据创造未来</span><small>DATA DRIVES A BETTER TOMORROW</small></div>
    <footer>© {{year}} DataSphere 数据开发平台　|　本系统仅限企业内部人员使用　|　建议使用 Chrome / Edge 浏览器</footer>
  </div>
</template>

<style scoped>
.login-page{--login-blue:#2f7cff;--login-cyan:#38c8ff;min-height:100vh;position:relative;overflow:hidden;color:#eef5ff;background:radial-gradient(circle at 72% 28%,rgba(42,106,224,.2),transparent 25%),radial-gradient(circle at 34% 78%,rgba(22,150,255,.14),transparent 32%),linear-gradient(135deg,#041229 0%,#081b3b 52%,#07152f 100%)}
.grid-layer{position:absolute;inset:0;pointer-events:none;opacity:.4;background-image:linear-gradient(rgba(98,154,255,.045) 1px,transparent 1px),linear-gradient(90deg,rgba(98,154,255,.045) 1px,transparent 1px);background-size:54px 54px;mask-image:linear-gradient(to bottom,rgba(0,0,0,.12),#000 45%,rgba(0,0,0,.28))}.glow{position:absolute;border-radius:50%;filter:blur(80px);pointer-events:none}.glow-one{width:420px;height:420px;right:15%;top:9%;background:rgba(37,105,235,.08)}.glow-two{width:360px;height:360px;left:20%;bottom:5%;background:rgba(22,167,255,.06)}
.login-header{height:72px;display:flex;align-items:center;justify-content:space-between;padding:0 52px;border-bottom:1px solid rgba(137,179,255,.18);position:relative;z-index:5;background:linear-gradient(90deg,rgba(4,19,43,.76),rgba(19,50,100,.26));backdrop-filter:blur(12px)}.brand{display:flex;align-items:center;gap:12px}.brand-logo{width:34px;height:34px;filter:drop-shadow(0 0 12px rgba(45,135,255,.45))}.brand strong{font-size:23px;font-weight:800;letter-spacing:.3px}.brand span{font-size:14px;color:#93acd1}.header-values{display:flex;gap:30px;color:#8ea8ce;font-size:14px}.header-values span{position:relative}.header-values span:not(:last-child):after{content:"";position:absolute;right:-16px;top:50%;width:3px;height:3px;border-radius:50%;background:#5c79a5}
.login-main{min-height:calc(100vh - 72px);display:grid;grid-template-columns:minmax(0,1.48fr) minmax(430px,.72fr);gap:46px;align-items:center;padding:48px 6.2vw 86px;position:relative;z-index:2}.hero-panel{min-width:0}.eyebrow{margin-bottom:12px;color:#77c8ff;font-size:13px;letter-spacing:2.6px}.hero-panel h1{margin:0;font-size:clamp(42px,4vw,66px);line-height:1.06;letter-spacing:-1.8px;font-weight:850;background:linear-gradient(90deg,#fff 7%,#5cbcff 92%);-webkit-background-clip:text;background-clip:text;color:transparent}.hero-panel h1 span{font-weight:820}.hero-subtitle{margin:17px 0 0;color:#d4e2f6;font-size:20px;letter-spacing:1.8px}
.feature-row{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:0;margin-top:38px;max-width:880px}.feature-item{display:flex;gap:13px;min-width:0;padding:2px 20px;border-right:1px solid rgba(128,172,235,.22)}.feature-item:first-child{padding-left:0}.feature-item:last-child{border-right:0}.feature-icon{width:40px;height:40px;display:grid;place-items:center;flex:none;border-radius:50%;color:#55b8ff;background:rgba(34,112,239,.12);border:1px solid rgba(75,160,255,.18);box-shadow:inset 0 0 14px rgba(52,136,255,.08)}.feature-icon svg{width:20px;height:20px}.feature-item h3{margin:1px 0 5px;font-size:14px}.feature-item p{margin:0;color:#8099bf;font-size:11px;line-height:1.65}
.data-scene{position:relative;height:380px;margin-top:18px;max-width:900px}.scene-orbit{position:absolute;left:-8%;right:5%;bottom:-18px;height:150px;border-radius:50%;border:1px solid rgba(48,156,255,.28);background:radial-gradient(ellipse at 50% 10%,rgba(29,141,255,.13),transparent 42%),repeating-radial-gradient(ellipse at center,rgba(30,151,255,.085) 0 1px,transparent 1px 13px);transform:perspective(540px) rotateX(67deg);box-shadow:0 -24px 80px rgba(17,102,255,.08)}.scene-beam{position:absolute;left:48%;top:22%;width:2px;height:118px;background:linear-gradient(180deg,transparent,#43c6ff,transparent);box-shadow:0 0 14px #3dbdff}.scene-core{position:absolute;left:48%;top:55%;transform:translate(-50%,-50%);width:185px;height:112px;border-radius:18px;background:linear-gradient(180deg,rgba(43,116,233,.28),rgba(12,45,98,.54));border:1px solid rgba(80,167,255,.27);box-shadow:0 22px 70px rgba(6,106,255,.18),inset 0 0 45px rgba(48,138,255,.08)}.scene-core:before{content:"";position:absolute;left:50%;top:-56px;width:68px;height:68px;transform:translateX(-50%) rotate(30deg) skewY(-30deg);border-radius:6px;background:linear-gradient(145deg,rgba(86,181,255,.78),rgba(47,77,226,.35));box-shadow:0 0 35px rgba(50,165,255,.4)}.scene-core span{position:absolute;left:50%;bottom:15px;width:84px;height:24px;transform:translateX(-50%) rotate(30deg) skewY(-30deg);border-radius:6px;background:linear-gradient(90deg,#2a6bff,#2bd1ff);opacity:.72;box-shadow:0 0 24px rgba(42,179,255,.5)}.scene-card{position:absolute;width:185px;padding:13px 14px;border-radius:10px;background:linear-gradient(180deg,rgba(14,53,111,.84),rgba(7,33,76,.8));border:1px solid rgba(61,145,255,.28);box-shadow:0 18px 40px rgba(0,10,30,.18);backdrop-filter:blur(9px)}.scene-card b,.scene-card small{display:block}.scene-card b{margin-bottom:8px;font-size:13px}.scene-card small{color:#8db4e2;font-size:10px;line-height:1.75}.scene-card>i{display:block;width:100%;height:1px;margin:6px 0;background:linear-gradient(90deg,#2b7ff2,transparent)}.scene-source{left:5%;top:190px}.scene-dev{left:31%;top:100px}.scene-warehouse{right:10%;top:88px}.scene-governance{right:1%;top:230px}.mini-bars{height:38px;display:flex;align-items:flex-end;gap:6px;margin-top:7px}.mini-bars i{flex:1;border-radius:2px 2px 0 0;background:linear-gradient(#42bcff,#2268db)}.mini-bars i:nth-child(1){height:35%}.mini-bars i:nth-child(2){height:62%}.mini-bars i:nth-child(3){height:48%}.mini-bars i:nth-child(4){height:82%}
.login-side{display:flex;justify-content:flex-end}.login-card{width:min(100%,510px);min-height:500px;padding:40px 44px 32px;border-radius:18px;color:#102449;background:linear-gradient(180deg,#fbfdff,#eef5ff);box-shadow:0 28px 80px rgba(1,12,37,.34),0 0 0 1px rgba(255,255,255,.55)}.session-check{min-height:420px;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#415b82}.session-check strong{margin-top:17px;font-size:16px}.session-check small{margin-top:6px;color:#8aa0bd}.card-head{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;margin-bottom:30px}.card-head h2{margin:0;font-size:29px;letter-spacing:-.5px}.card-head p{margin:8px 0 0;color:#7185a7;font-size:14px}
.login-form{display:block}.login-field{height:58px;display:block;position:relative;margin-bottom:18px}.login-field input{width:100%;height:100%;padding:0 48px 0 46px;border:1px solid #d4deed;border-radius:10px;outline:none;color:#102449;background:#fff;font-size:15px;box-shadow:0 2px 12px rgba(23,61,123,.03);transition:border-color .18s,box-shadow .18s}.login-field input::placeholder{color:#9aaac0}.login-field input:focus{border-color:#5796ff;box-shadow:0 0 0 3px rgba(47,124,255,.11)}.login-field input:disabled{cursor:not-allowed;background:#f6f8fb}.field-icon{position:absolute;left:16px;top:50%;width:20px;height:20px;transform:translateY(-50%);color:#6f84a8;z-index:2}.field-icon svg,.password-toggle svg{width:100%;height:100%}.password-toggle{position:absolute;right:14px;top:50%;width:25px;height:25px;padding:2px;border:0;background:transparent;color:#7085a8;transform:translateY(-50%)}.password-toggle:hover{color:#2f72ee}.form-options{display:flex;justify-content:space-between;align-items:center;margin:3px 0 24px;color:#445a7c;font-size:14px}.remember{display:flex;align-items:center;gap:8px;cursor:pointer;user-select:none}.remember input{width:17px;height:17px;accent-color:#2f7cff}.forgot{padding:0;border:0;background:transparent;color:#2f72ee;cursor:pointer}.forgot:hover{color:#195dd4}.login-button{width:100%;height:60px;display:flex;align-items:center;justify-content:center;gap:9px;border:0;border-radius:10px;color:#fff;background:linear-gradient(90deg,#2970ef,#257fff,#2e71ef);box-shadow:0 10px 26px rgba(42,118,244,.22);font-size:17px;font-weight:750;letter-spacing:2px;cursor:pointer;transition:transform .18s,box-shadow .18s,opacity .18s}.login-button:hover:not(:disabled){transform:translateY(-1px);box-shadow:0 14px 30px rgba(42,118,244,.3)}.login-button:disabled{cursor:not-allowed;opacity:.72}.spinner{width:28px;height:28px;border:3px solid rgba(47,124,255,.18);border-top-color:#2f7cff;border-radius:50%;animation:spin .75s linear infinite}.spinner.small{width:18px;height:18px;border-width:2px;border-color:rgba(255,255,255,.32);border-top-color:#fff}@keyframes spin{to{transform:rotate(360deg)}}
.trust-line{display:flex;align-items:center;gap:12px;margin:28px 0 19px;color:#91a4c0;font-size:12px}.trust-line span{height:1px;flex:1;background:#d4deec}.trust-line em{font-style:normal;white-space:nowrap}.card-foot{margin-top:19px;color:#9aabc4;font-size:11px;text-align:center}.slogan{position:absolute;left:5.4vw;bottom:28px;z-index:3;color:#62bfff;font-size:13px;letter-spacing:5px}.slogan strong,.slogan span,.slogan small{display:block}.slogan strong{margin-bottom:4px;color:#8ed4ff;font-size:15px;letter-spacing:8px}.slogan small{margin-top:9px;color:#527cab;font-size:9px;letter-spacing:2.2px}.login-page footer{position:absolute;left:0;right:0;bottom:15px;z-index:4;color:#5f789e;font-size:10px;letter-spacing:.3px;text-align:center}
@media(max-width:1600px){.login-main{padding-left:5vw;padding-right:5vw;gap:36px}.hero-panel h1{font-size:clamp(40px,3.7vw,58px)}.data-scene{height:350px}.login-card{max-width:480px;padding:36px 40px 30px}}
@media(max-width:1320px){.login-header{padding:0 34px}.login-main{grid-template-columns:minmax(0,1.16fr) minmax(410px,.84fr);padding:34px 3.5vw 66px;gap:28px}.hero-panel h1{font-size:clamp(36px,3.8vw,50px)}.hero-subtitle{font-size:17px}.feature-row{margin-top:26px}.feature-item{padding-left:14px;padding-right:14px}.feature-icon{width:36px;height:36px}.data-scene{height:300px}.scene-card{transform:scale(.9);transform-origin:center}.scene-source{left:2%;top:150px}.scene-dev{left:28%;top:76px}.scene-warehouse{right:8%;top:66px}.scene-governance{right:-2%;top:176px}.login-card{max-width:450px;padding:32px 34px 27px}.card-head{margin-bottom:24px}.login-field{height:54px}.login-button{height:56px}.trust-line{margin:23px 0 16px}}
@media(max-width:1120px){.login-page{overflow:auto}.login-main{min-height:calc(100dvh - 64px);grid-template-columns:1fr;align-items:start;padding:36px clamp(24px,6vw,72px) 72px}.login-header{height:64px}.header-values{display:none}.hero-panel{max-width:760px}.hero-panel h1{font-size:clamp(34px,5.5vw,48px)}.hero-subtitle{font-size:16px}.feature-row{margin-top:25px;margin-bottom:28px}.data-scene{display:none}.login-side{justify-content:flex-start}.login-card{width:min(100%,560px);max-width:none;min-height:0}.slogan,.login-page footer{display:none}}
@media(max-width:760px){.login-header{height:60px;padding:0 20px}.brand{gap:9px}.brand-logo{width:30px;height:30px}.brand strong{font-size:19px}.brand span{display:none}.login-main{min-height:calc(100dvh - 60px);padding:28px 20px 40px}.eyebrow{font-size:10px;letter-spacing:1.7px}.hero-panel h1{font-size:clamp(30px,9vw,42px);line-height:1.12}.hero-panel h1 span{display:block;margin-top:4px}.hero-subtitle{margin-top:12px;font-size:14px;letter-spacing:.8px}.feature-row{grid-template-columns:1fr;gap:12px;margin:22px 0 24px}.feature-item{padding:0 0 12px;border-right:0;border-bottom:1px solid rgba(128,172,235,.18)}.feature-item:last-child{padding-bottom:0;border-bottom:0}.feature-item p{font-size:11px}.login-card{width:100%;padding:28px 24px 24px;border-radius:14px}.card-head h2{font-size:25px}.card-head p{font-size:13px}.internal-badge{display:none}.login-field{height:52px;margin-bottom:14px}.form-options{margin-bottom:20px}.login-button{height:54px;font-size:16px}.trust-line{margin:22px 0 16px}.security-tip p{font-size:10px}}
@media(max-width:480px){.login-main{padding:22px 14px 28px}.hero-panel h1{font-size:30px}.hero-subtitle{font-size:13px}.feature-row{display:none}.login-card{padding:24px 18px 20px}.card-head{margin-bottom:22px}.card-head h2{font-size:23px}.login-field{height:50px}.login-field input{padding-left:42px;font-size:14px}.field-icon{left:14px;width:18px;height:18px}.password-toggle{right:11px}.form-options{font-size:13px}.login-button{height:52px}.security-tip{padding:10px 11px}.card-foot{margin-top:15px}}
@media(max-height:820px) and (min-width:1121px){.login-main{padding-top:26px;padding-bottom:54px}.hero-panel h1{font-size:clamp(34px,3.5vw,52px)}.hero-subtitle{margin-top:12px;font-size:16px}.feature-row{margin-top:22px}.data-scene{height:270px;margin-top:8px}.scene-source{top:132px}.scene-dev{top:62px}.scene-warehouse{top:54px}.scene-governance{top:158px}.login-card{min-height:0;padding-top:28px;padding-bottom:24px}.card-head{margin-bottom:22px}.login-field{height:50px;margin-bottom:14px}.form-options{margin-bottom:18px}.login-button{height:52px}.trust-line{margin:20px 0 14px}.security-tip{padding:9px 12px}.card-foot{margin-top:14px}.slogan{display:none}}
@media(max-height:700px) and (min-width:1121px){.feature-row{display:none}.data-scene{height:220px}.scene-card{transform:scale(.8)}.scene-source{top:100px}.scene-dev{top:40px}.scene-warehouse{top:34px}.scene-governance{top:120px}.login-main{padding-top:18px;padding-bottom:30px}.login-card{padding:24px 30px 20px}.trust-line{margin:16px 0 12px}.security-tip p{font-size:10px}.login-page footer{display:none}}
@media(orientation:landscape) and (max-width:960px) and (max-height:620px){.login-header{height:54px}.login-main{min-height:calc(100dvh - 54px);grid-template-columns:minmax(0,.9fr) minmax(360px,1.1fr);align-items:center;padding:18px 24px 24px;gap:24px}.hero-panel .feature-row,.data-scene{display:none}.hero-panel h1{font-size:32px}.hero-subtitle{font-size:13px}.login-card{padding:22px 24px 18px}.card-head{margin-bottom:18px}.login-field{height:46px;margin-bottom:12px}.login-button{height:48px}.trust-line{display:none}.security-tip{margin-top:14px}.slogan,.login-page footer{display:none}}
:global(html:has(.login-page)),:global(body:has(.login-page)),:global(#app:has(.login-page)){min-width:0;width:100%;min-height:100%;overflow-x:hidden}
</style>
