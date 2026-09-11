<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { platformApi } from "../api";

const router = useRouter();
const username = ref("admin");
const password = ref("");
const submitting = ref(false);

async function login() {
  if (!username.value || !password.value) {
    ElMessage.warning("请输入用户名和密码");
    return;
  }
  submitting.value = true;
  try {
    const result = await platformApi.login(username.value, password.value);
    const session = result.data.data;
    localStorage.setItem("platform_access_token", session.token);
    localStorage.setItem("platform_username", session.username);
    await router.replace("/development");
  } catch (error: any) {
    ElMessage.error(
      error?.response?.data?.message || "登录失败，请检查账号信息",
    );
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <main class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <div class="logo-mark">D</div>
        <div>
          <strong>DataWorks Studio</strong>
          <p>大数据开发平台</p>
        </div>
      </div>
      <h1>登录平台</h1>
      <p class="muted">使用平台管理员账号进入开发工作台。</p>
      <form @submit.prevent="login">
        <div class="form-item">
          <label>用户名</label
          ><input
            v-model="username"
            autocomplete="username"
            placeholder="请输入用户名"
          />
        </div>
        <div class="form-item">
          <label>密码</label
          ><input
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
          />
        </div>
        <button
          class="btn-primary login-submit"
          type="submit"
          :disabled="submitting"
        >
          {{ submitting ? "登录中…" : "登录" }}
        </button>
      </form>
    </div>
  </main>
</template>
