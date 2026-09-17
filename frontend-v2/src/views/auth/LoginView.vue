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
    <div class="decor-orbit orbit-a"></div>
    <div class="decor-orbit orbit-b"></div>
    <div class="top-note"><i></i><span>DATA</span><span>CONNECTS</span><span>A BRIGHTER</span><span>TOMORROW</span></div>

    <main class="shell">
      <section class="hero" aria-label="DataSphere 平台介绍">
        <div class="brand">
          <div class="brand-mark" aria-hidden="true">
            <span class="mark-top"></span><span class="mark-mid"></span><span class="mark-bottom"></span>
          </div>
          <div class="brand-word"><strong>Data</strong><b>Sphere</b></div>
        </div>

        <h1>统一数据开发与治理平台</h1>
        <p class="hero-sub">轻质感、克制而清晰的数据工作台体验<br>统一数据接入、开发、调度与治理</p>

        <div class="scene" aria-hidden="true">
          <div class="scene-ground"></div>
          <div class="scene-grid"></div>
          <div class="scene-base base-1"></div>
          <div class="scene-base base-2"></div>
          <div class="scene-base base-3"></div>

          <div class="core-platform">
            <div class="core-ring ring-a"></div><div class="core-ring ring-b"></div>
            <div class="core-cubes">
              <i class="cube c1"></i><i class="cube c2"></i><i class="cube c3"></i><i class="cube c4"></i><i class="cube c5"></i><i class="cube c6"></i>
            </div>
            <div class="core-label">DataSphere</div>
          </div>

          <div class="terminal terminal-ingest">
            <div class="terminal-screen"><span class="bars"><i></i><i></i><i></i></span></div><b>数据接入</b>
          </div>
          <div class="terminal terminal-dev">
            <div class="terminal-screen code"><span>&lt;/&gt;</span></div><b>数据开发</b>
          </div>
          <div class="terminal terminal-schedule">
            <div class="terminal-screen nodes"><i></i><i></i><i></i></div><b>任务调度</b>
          </div>
          <div class="terminal terminal-govern">
            <div class="terminal-screen shield">✓</div><b>数据治理</b>
          </div>

          <div class="cylinder cyl-a"><i></i><i></i><i></i></div>
          <div class="cylinder cyl-b small"><i></i><i></i></div>
          <div class="tree t1"></div><div class="tree t2"></div><div class="tree t3"></div>
          <div class="wire w1"></div><div class="wire w2"></div><div class="wire w3"></div><div class="wire w4"></div>
        </div>
        <div class="slogan"><span>数 据 让 业 务 更 有 可 能</span><i></i></div>
      </section>

      <section class="login-card" aria-label="登录表单">
        <div v-if="checkingSession" class="session-check">
          <span class="spinner"></span><strong>正在验证登录状态</strong><small>请稍候...</small>
        </div>
        <template v-else>
          <h2>登录</h2>
          <div class="welcome">欢迎回来，使用 DataSphere</div>
          <form @submit.prevent="submit" novalidate>
            <div class="field">
              <label>账号</label>
              <div class="input-wrap">
                <span class="input-icon user-icon"><i></i></span>
                <input v-model="form.username" autocomplete="username" placeholder="请输入账号" :disabled="loading" />
              </div>
            </div>
            <div class="field">
              <label>密码</label>
              <div class="input-wrap">
                <span class="input-icon lock-icon"><i></i></span>
                <input v-model="form.password" :type="passwordVisible?'text':'password'" autocomplete="current-password" placeholder="请输入密码" :disabled="loading" />
                <button class="eye" type="button" :aria-label="passwordVisible?'隐藏密码':'显示密码'" @click="passwordVisible=!passwordVisible"><span :class="{off:!passwordVisible}"></span></button>
              </div>
            </div>
            <div class="options">
              <label class="remember"><input v-model="rememberUsername" type="checkbox" /><span>记住我</span></label>
              <button class="forgot" type="button" @click="forgotPassword">忘记密码？</button>
            </div>
            <button class="login-btn" type="submit" :disabled="loading"><span v-if="loading" class="spinner small"></span><span>{{loading?'登录中...':'登 录'}}</span></button>
          </form>
        </template>
      </section>
    </main>

    <footer><i></i><span>© {{year}} DataSphere · 用数据创造更大的价值</span><i></i></footer>
  </div>
</template>

<style scoped>
.login-page{--blue:#3479f7;--deep:#0b2d62;--muted:#758caf;position:relative;min-height:100vh;overflow:hidden;color:var(--deep);background:radial-gradient(circle at 72% 20%,rgba(86,146,255,.16),transparent 28%),radial-gradient(circle at 24% 78%,rgba(89,154,255,.12),transparent 34%),linear-gradient(180deg,#fbfdff 0%,#f4f9ff 100%)}
.login-page:before{content:"";position:absolute;inset:0;background:linear-gradient(115deg,rgba(255,255,255,.65),transparent 42%,rgba(230,241,255,.28));pointer-events:none}.decor-orbit{position:absolute;border:2px solid rgba(84,139,222,.08);border-radius:50%;pointer-events:none}.orbit-a{width:980px;height:980px;right:-190px;top:-610px}.orbit-b{width:760px;height:760px;right:-240px;top:-425px}.top-note{position:absolute;right:5.3vw;top:5.1vh;z-index:2;display:flex;flex-direction:column;gap:7px;color:#9db0cd;font-size:10px;letter-spacing:2.3px;line-height:1}.top-note i{width:25px;height:1px;margin-bottom:5px;background:#9fb4d2}
.shell{position:relative;z-index:2;width:min(1580px,calc(100vw - 120px));height:100vh;margin:0 auto;display:grid;grid-template-columns:minmax(0,1.18fr) minmax(520px,.82fr);gap:72px;align-items:center}
.hero{align-self:stretch;padding:15.5vh 0 8vh;display:flex;flex-direction:column;min-width:0}.brand{display:flex;align-items:center;gap:26px;margin-bottom:34px}.brand-mark{position:relative;width:92px;height:76px;flex:none}.brand-mark span{position:absolute;left:0;border-radius:11px 26px 26px 11px;background:linear-gradient(135deg,#2c74ff,#4a8dff)}.mark-top{top:0;width:82px;height:26px}.mark-mid{top:25px;left:14px!important;width:63px;height:26px;opacity:.72}.mark-bottom{bottom:0;width:82px;height:26px;background:linear-gradient(135deg,#6fa4ff,#2f72ee)!important}.brand-mark:after{content:"";position:absolute;left:0;top:25px;width:35px;height:26px;background:#f7fbff;clip-path:polygon(0 0,100% 0,58% 100%,0 100%)}.brand-word{display:flex;align-items:baseline;font-size:55px;line-height:1;font-weight:800;letter-spacing:-2px}.brand-word strong{color:#0c2957}.brand-word b{color:#3479f7;font-weight:800}
.hero h1{margin:0 0 20px;font-size:clamp(38px,3.25vw,58px);line-height:1.18;letter-spacing:-2px;font-weight:800}.hero-sub{margin:0;color:#7890b3;font-size:clamp(17px,1.25vw,24px);line-height:1.7;letter-spacing:.2px}
.scene{position:relative;width:min(930px,100%);height:430px;margin-top:18px;transform-origin:left center}.scene-ground{position:absolute;left:-8%;right:4%;bottom:8px;height:185px;border-radius:50%;background:radial-gradient(ellipse at center,rgba(107,157,236,.17),rgba(255,255,255,0) 66%);filter:blur(4px)}.scene-grid{position:absolute;left:-4%;right:5%;bottom:20px;height:180px;opacity:.34;background:repeating-linear-gradient(0deg,rgba(120,160,225,.16) 0 1px,transparent 1px 26px),repeating-linear-gradient(90deg,rgba(120,160,225,.16) 0 1px,transparent 1px 34px);transform:perspective(440px) rotateX(66deg);transform-origin:center bottom}
.scene-base{position:absolute;height:66px;border-radius:20px;border:1px solid rgba(161,190,234,.42);background:linear-gradient(180deg,rgba(255,255,255,.94),rgba(224,237,254,.78));box-shadow:0 24px 40px rgba(82,123,190,.08)}.base-1{left:23%;bottom:35px;width:48%;transform:skewX(-17deg)}.base-2{left:29%;bottom:75px;width:38%;height:78px}.base-3{left:34%;bottom:112px;width:28%;height:92px}
.core-platform{position:absolute;left:36%;bottom:115px;width:200px;height:150px}.core-ring{position:absolute;left:50%;top:14px;border:2px solid rgba(63,125,241,.52);border-radius:50%;transform:translateX(-50%) rotateX(68deg)}.ring-a{width:250px;height:95px}.ring-b{width:175px;height:66px;opacity:.5}.core-cubes{position:absolute;left:50%;top:24px;width:148px;height:104px;transform:translateX(-50%)}.cube{position:absolute;width:56px;height:56px;border:1px solid rgba(255,255,255,.76);background:linear-gradient(145deg,rgba(255,255,255,.88),rgba(95,153,255,.42));box-shadow:10px 16px 28px rgba(76,126,210,.12);transform:skewY(-18deg) rotate(30deg)}.c1{left:46px;top:0}.c2{left:8px;top:28px}.c3{left:82px;top:30px}.c4{left:42px;top:51px;background:linear-gradient(145deg,#71aaff,#2d70ef)}.c5{left:101px;top:8px;opacity:.55}.c6{left:0;top:2px;opacity:.62}.core-label{position:absolute;left:50%;bottom:-27px;transform:translateX(-50%);color:#8aa0bf;font-size:12px}
.terminal{position:absolute;width:112px;height:112px;display:flex;flex-direction:column;align-items:center;justify-content:center;border-radius:18px;background:rgba(255,255,255,.74);border:1px solid rgba(177,202,240,.72);box-shadow:0 18px 35px rgba(83,126,195,.08);backdrop-filter:blur(7px);transform:perspective(500px) rotateY(-10deg)}.terminal b{margin-top:8px;color:#416a9e;font-size:13px}.terminal-screen{width:54px;height:46px;display:grid;place-items:center;color:#3c7df2}.terminal-ingest{left:10%;bottom:140px}.terminal-dev{left:62%;bottom:210px}.terminal-schedule{right:5%;bottom:125px}.terminal-govern{right:25%;bottom:28px}.bars{display:flex;align-items:flex-end;gap:5px;height:32px}.bars i{display:block;width:6px;border-radius:3px;background:#5a92f8}.bars i:nth-child(1){height:12px}.bars i:nth-child(2){height:23px}.bars i:nth-child(3){height:31px}.code span{font-size:22px;font-weight:800}.nodes{position:relative}.nodes i{position:absolute;width:12px;height:12px;border-radius:50%;background:#4a85f4}.nodes i:nth-child(1){left:7px;top:16px}.nodes i:nth-child(2){right:7px;top:6px}.nodes i:nth-child(3){right:11px;bottom:4px}.nodes:before,.nodes:after{content:"";position:absolute;width:28px;height:2px;background:#7aa5ed;transform-origin:left center}.nodes:before{left:16px;top:20px;transform:rotate(-22deg)}.nodes:after{left:18px;top:23px;transform:rotate(18deg)}.shield{font-size:26px;font-weight:800;color:#4d86f5}
.cylinder{position:absolute;left:12%;bottom:35px;width:90px;height:72px}.cylinder i{position:absolute;left:0;width:100%;height:24px;border-radius:50%;border:1px solid rgba(154,185,232,.5);background:linear-gradient(180deg,#fff,#e6f0ff)}.cylinder i:nth-child(1){top:0}.cylinder i:nth-child(2){top:18px}.cylinder i:nth-child(3){top:36px}.cyl-b{left:68%;bottom:168px;width:45px;height:50px}.tree{position:absolute;width:20px;height:52px;border-radius:50% 50% 44% 44%;background:linear-gradient(180deg,#edf6ff,#cfe1fa)}.tree:after{content:"";position:absolute;left:9px;bottom:-16px;width:2px;height:18px;background:#bfd0ea}.t1{left:4%;bottom:72px}.t2{right:0;bottom:87px}.t3{right:16%;bottom:154px}.wire{position:absolute;height:1px;background:linear-gradient(90deg,transparent,#74a6f0,transparent);transform-origin:left center}.w1{left:20%;bottom:190px;width:195px;transform:rotate(-5deg)}.w2{left:54%;bottom:225px;width:145px;transform:rotate(12deg)}.w3{left:56%;bottom:133px;width:220px;transform:rotate(-4deg)}.w4{left:35%;bottom:95px;width:250px;transform:rotate(5deg)}
.slogan{display:flex;align-items:center;gap:18px;margin-top:-10px;color:#98acc9;font-size:12px;letter-spacing:9px}.slogan i{display:block;width:90px;height:1px;background:#c5d3e8}
.login-card{width:100%;max-width:570px;min-height:605px;justify-self:end;padding:58px 58px 50px;border:1px solid rgba(220,229,242,.92);border-radius:22px;background:rgba(255,255,255,.96);box-shadow:0 28px 70px rgba(54,96,164,.11);backdrop-filter:blur(14px)}.login-card h2{margin:0 0 12px;font-size:46px;line-height:1.1;letter-spacing:-1.6px}.welcome{margin-bottom:38px;color:#7890b3;font-size:20px}.field{margin-bottom:25px}.field>label{display:block;margin-bottom:9px;color:#18375f;font-size:17px;font-weight:650}.input-wrap{position:relative;height:64px}.input-wrap input{width:100%;height:100%;padding:0 54px;border:1px solid #d3deee;border-radius:8px;outline:none;background:#fff;color:#17375f;font-size:17px;transition:.18s}.input-wrap input::placeholder{color:#a8b7ca}.input-wrap input:focus{border-color:#6b9bff;box-shadow:0 0 0 4px rgba(52,121,247,.09)}.input-icon{position:absolute;left:19px;top:50%;width:21px;height:21px;transform:translateY(-50%);z-index:2}.user-icon:before{content:"";position:absolute;left:7px;top:1px;width:7px;height:7px;border:2px solid #7893bb;border-radius:50%}.user-icon:after{content:"";position:absolute;left:3px;bottom:1px;width:15px;height:8px;border:2px solid #7893bb;border-bottom:0;border-radius:10px 10px 0 0}.lock-icon:before{content:"";position:absolute;left:3px;top:8px;width:15px;height:12px;border:2px solid #7893bb;border-radius:3px}.lock-icon:after{content:"";position:absolute;left:6px;top:1px;width:9px;height:10px;border:2px solid #7893bb;border-bottom:0;border-radius:8px 8px 0 0}.eye{position:absolute;right:14px;top:50%;width:34px;height:34px;transform:translateY(-50%);border:0;background:transparent;cursor:pointer}.eye span{position:absolute;left:8px;top:11px;width:18px;height:12px;border:2px solid #7893bb;border-radius:50%}.eye span:after{content:"";position:absolute;left:5px;top:2px;width:4px;height:4px;border:2px solid #7893bb;border-radius:50%}.eye span.off:before{content:"";position:absolute;left:-4px;top:4px;width:25px;height:2px;background:#7893bb;transform:rotate(42deg)}
.options{display:flex;align-items:center;justify-content:space-between;margin:2px 0 34px}.remember{display:flex;align-items:center;gap:10px;color:#486183;font-size:17px;cursor:pointer}.remember input{width:21px;height:21px;accent-color:var(--blue)}.forgot{border:0;background:none;color:#2673f4;font-size:17px;cursor:pointer}.login-btn{width:100%;height:64px;display:flex;align-items:center;justify-content:center;gap:10px;border:0;border-radius:8px;background:linear-gradient(90deg,#3479f7,#2f73ef);color:#fff;font-size:20px;font-weight:750;letter-spacing:10px;box-shadow:0 12px 24px rgba(52,121,247,.18);cursor:pointer}.login-btn:hover:not(:disabled){filter:brightness(.98);transform:translateY(-1px)}.login-btn:disabled{opacity:.7;cursor:not-allowed}.session-check{min-height:480px;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#647d9f}.session-check strong{margin-top:16px}.session-check small{margin-top:6px;color:#9aacbf}.spinner{width:28px;height:28px;border:3px solid rgba(52,121,247,.15);border-top-color:#3479f7;border-radius:50%;animation:spin .75s linear infinite}.spinner.small{width:18px;height:18px;border-width:2px;border-color:rgba(255,255,255,.35);border-top-color:#fff}@keyframes spin{to{transform:rotate(360deg)}}
footer{position:absolute;left:50%;bottom:22px;z-index:3;display:flex;align-items:center;gap:20px;transform:translateX(-50%);color:#9aaeca;font-size:12px;white-space:nowrap}footer i{width:36px;height:1px;background:#bdcce1}
@media(max-width:1550px){.shell{width:min(1420px,calc(100vw - 76px));grid-template-columns:minmax(0,1.12fr) minmax(470px,.78fr);gap:50px}.hero{padding-top:12vh}.brand{margin-bottom:24px}.brand-mark{width:78px;height:65px}.brand-word{font-size:46px}.scene{transform:scale(.9);width:104%;margin-top:-2px}.login-card{max-width:520px;min-height:560px;padding:48px 48px 42px}.login-card h2{font-size:40px}.welcome{font-size:18px;margin-bottom:30px}.input-wrap{height:58px}.login-btn{height:58px}}
@media(max-height:820px) and (min-width:1101px){.hero{padding-top:7vh;padding-bottom:6vh}.brand{margin-bottom:16px}.brand-mark{width:66px;height:55px}.brand-word{font-size:40px}.hero h1{font-size:38px;margin-bottom:10px}.hero-sub{font-size:17px;line-height:1.5}.scene{height:330px;margin-top:0;transform:scale(.78);width:122%;transform-origin:left top}.slogan{margin-top:-45px;font-size:10px;letter-spacing:6px}.login-card{max-width:485px;min-height:490px;padding:34px 40px}.login-card h2{font-size:34px}.welcome{font-size:16px;margin-bottom:23px}.field{margin-bottom:17px}.field>label{font-size:15px;margin-bottom:6px}.input-wrap{height:52px}.input-wrap input{font-size:15px}.options{margin-bottom:22px}.remember,.forgot{font-size:14px}.login-btn{height:52px;font-size:17px}.top-note{top:3vh}.session-check{min-height:410px}}
@media(max-width:1100px){.login-page{overflow:auto}.top-note{display:none}.shell{height:auto;min-height:100vh;grid-template-columns:1fr;width:min(760px,calc(100vw - 40px));gap:30px;padding:54px 0 100px}.hero{padding:0}.brand{margin-bottom:22px}.scene{width:100%;height:360px;transform:none}.login-card{justify-self:center;max-width:620px;min-height:0}.slogan{margin-top:2px}footer{position:absolute;bottom:18px}}
@media(max-width:640px){.shell{width:calc(100vw - 28px);padding-top:32px}.brand{gap:15px}.brand-mark{width:58px;height:49px}.brand-word{font-size:34px}.hero h1{font-size:29px;letter-spacing:-1px}.hero-sub{font-size:15px}.scene{height:245px;margin-top:8px;transform:scale(.82);width:122%;transform-origin:left top}.terminal{transform:scale(.85)}.slogan{margin-top:-36px;font-size:9px;letter-spacing:4px}.login-card{padding:32px 24px 30px;border-radius:18px}.login-card h2{font-size:33px}.welcome{font-size:16px;margin-bottom:24px}.field{margin-bottom:18px}.field>label{font-size:15px}.input-wrap{height:54px}.input-wrap input{font-size:15px;padding-left:48px}.options{margin-bottom:24px}.remember,.forgot{font-size:14px}.login-btn{height:54px;font-size:17px}footer{font-size:10px;gap:10px}footer i{width:20px}}
@media(max-width:420px){.brand-word{font-size:30px}.brand-mark{width:52px;height:43px}.hero h1{font-size:26px}.scene{height:210px;transform:scale(.72);width:138%}.slogan{display:none}.login-card{padding:28px 18px}.welcome{font-size:15px}footer{display:none}}
:global(html:has(.login-page)),:global(body:has(.login-page)),:global(#app:has(.login-page)){min-width:0;width:100%;min-height:100%;overflow-x:hidden}
</style>
