<script setup lang="ts">
import { Lock, Message, User } from "@element-plus/icons-vue";
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import * as authApi from "@/api/modules/auth";

type AuthMode = "login" | "register" | "forget";

const router = useRouter();
const mode = ref<AuthMode>("login");
const countDown = ref(0);
const timer = ref<number | null>(null);
const subtitleText = "Consumption Analytics System";
const animatedSubtitle = ref("");
const subtitleIndex = ref(0);
const subtitleTimer = ref<number | null>(null);
const checkingRegisterEmail = ref(false);
const registerEmailAvailable = ref<boolean | null>(null);
const registerEmailHint = ref("");
const registerEmailCheckDisabled = ref(false);
const mouseX = ref(50);
const mouseY = ref(38);
let registerEmailCheckSerial = 0;

const isLoginMode = computed(() => mode.value === "login");
const isRegisterMode = computed(() => mode.value === "register");
const isForgetMode = computed(() => mode.value === "forget");
const switchOffset = computed(() => (isRegisterMode.value ? "100%" : "0%"));

const formModel = reactive({
  userEmail: "123@test.com",
  userName: "123@test.com",
  password: "tjk896621X",
  password2: "",
  newPassword: "",
  code: "",
});

const clearTimer = () => {
  if (timer.value) {
    clearInterval(timer.value);
    timer.value = null;
  }
};

const clearSubtitleTimer = () => {
  if (subtitleTimer.value) {
    clearTimeout(subtitleTimer.value);
    subtitleTimer.value = null;
  }
};

const startCountDown = () => {
  clearTimer();
  countDown.value = 60;
  timer.value = window.setInterval(() => {
    countDown.value -= 1;
    if (countDown.value <= 0) clearTimer();
  }, 1000);
};

const switchMode = (nextMode: AuthMode) => {
  mode.value = nextMode;
  registerEmailAvailable.value = null;
  registerEmailHint.value = "";
  registerEmailCheckDisabled.value = false;
};

const runSubtitleTyping = () => {
  if (subtitleIndex.value <= subtitleText.length) {
    animatedSubtitle.value = subtitleText.slice(0, subtitleIndex.value);
    subtitleIndex.value += 1;
    subtitleTimer.value = window.setTimeout(runSubtitleTyping, 140);
    return;
  }
  subtitleTimer.value = window.setTimeout(() => {
    subtitleIndex.value = 0;
    runSubtitleTyping();
  }, 900);
};

const handlePointerMove = (event: MouseEvent) => {
  const target = event.currentTarget as HTMLElement | null;
  if (!target) return;
  const rect = target.getBoundingClientRect();
  if (!rect.width || !rect.height) return;
  mouseX.value = ((event.clientX - rect.left) / rect.width) * 100;
  mouseY.value = ((event.clientY - rect.top) / rect.height) * 100;
};

const checkRegisterEmail = async (options: { silent?: boolean } = {}) => {
  const email = formModel.userEmail.trim();
  const { silent = false } = options;

  if (registerEmailCheckDisabled.value) return true;

  if (!email) {
    registerEmailAvailable.value = null;
    registerEmailHint.value = "";
    if (!silent) ElMessage.warning("Please input email");
    return false;
  }

  const reqId = ++registerEmailCheckSerial;
  checkingRegisterEmail.value = true;
  registerEmailHint.value = "";

  try {
    const res = await authApi.judgeIfExist(email);
    if (reqId !== registerEmailCheckSerial) return registerEmailAvailable.value === true;

    const code = res?.data?.code ?? res?.code;
    if (code === 200) {
      registerEmailAvailable.value = true;
      registerEmailHint.value = "Email is available";
      return true;
    }

    registerEmailAvailable.value = false;
    registerEmailHint.value = res?.data?.msg || res?.msg || "Email has been registered";
    if (!silent) ElMessage.error(registerEmailHint.value);
    return false;
  } catch (e: any) {
    if (reqId !== registerEmailCheckSerial) return false;
    const status = Number(e?.response?.status || 0);
    if (status === 401 || status === 403) {
      registerEmailCheckDisabled.value = true;
      registerEmailAvailable.value = null;
      registerEmailHint.value = "Email check endpoint is unauthorized, skipped for now";
      if (!silent) ElMessage.warning(registerEmailHint.value);
      return true;
    }

    registerEmailAvailable.value = false;
    registerEmailHint.value = e?.response?.data?.msg || e?.message || "Check email failed";
    if (!silent) ElMessage.error(registerEmailHint.value);
    return false;
  } finally {
    if (reqId === registerEmailCheckSerial) {
      checkingRegisterEmail.value = false;
    }
  }
};

const onRegisterEmailBlur = async () => {
  if (!isRegisterMode.value) return;
  await checkRegisterEmail();
};

const sendCode = async () => {
  if (!formModel.userEmail) {
    ElMessage.warning("Please input email");
    return;
  }
  if (isRegisterMode.value) {
    const ok = await checkRegisterEmail({ silent: true });
    if (!ok) {
      ElMessage.error(registerEmailHint.value || "Email has been registered");
      return;
    }
  }
  try {
    await authApi.sendCode(formModel.userEmail);
    ElMessage.success("Code sent");
    startCountDown();
  } catch (e: any) {
    ElMessage.error(e?.message || "Send code failed");
  }
};

const onLogin = async () => {
  try {
    const res = await authApi.login({
      userEmail: formModel.userEmail,
      password: formModel.password,
    });
    const code = res?.data?.code ?? res?.code;
    if (code !== 200) {
      return ElMessage.error(res?.data?.msg || res?.msg || "Login failed");
    }
    const token = res?.data?.data;
    if (token) localStorage.setItem("token", token);
    ElMessage.success("Login success");
    router.push("/home");
  } catch (e: any) {
    ElMessage.error(e?.message || "Login failed");
  }
};

const onRegister = async () => {
  if (!formModel.userEmail) {
    ElMessage.warning("Please input email");
    return;
  }
  if (formModel.password !== formModel.password2) {
    ElMessage.warning("Passwords are not the same");
    return;
  }

  const canRegister = await checkRegisterEmail({ silent: true });
  if (!canRegister) {
    ElMessage.error(registerEmailHint.value || "Email has been registered");
    return;
  }

  try {
    const res = await authApi.register({
      userEmail: formModel.userEmail,
      userName: formModel.userName,
      password: formModel.password,
      validateCode: formModel.code,
    });
    const code = res?.data?.code ?? res?.code;
    if (code !== 200) {
      return ElMessage.error(res?.data?.msg || res?.msg || "Register failed");
    }
    ElMessage.success("Register success, please login");
    switchMode("login");
    formModel.password2 = "";
    formModel.code = "";
  } catch (e: any) {
    ElMessage.error(e?.message || "Register failed");
  }
};

const onForgetPassword = async () => {
  try {
    await authApi.forgetPassword({
      userEmail: formModel.userEmail,
      newPassword: formModel.newPassword,
      validateCode: formModel.code,
    });
    ElMessage.success("Password updated, please login again");
    switchMode("login");
    formModel.newPassword = "";
    formModel.code = "";
  } catch (e: any) {
    ElMessage.error(e?.message || "Update password failed");
  }
};

onMounted(() => {
  runSubtitleTyping();
});

onUnmounted(() => {
  clearTimer();
  clearSubtitleTimer();
});

watch(
  () => formModel.userEmail,
  () => {
    registerEmailAvailable.value = null;
    registerEmailHint.value = "";
    registerEmailCheckSerial += 1;
    checkingRegisterEmail.value = false;
  }
);
</script>

<template>
  <div
    class="auth-page"
    @mousemove="handlePointerMove"
    :style="{
      '--mx': `${mouseX}%`,
      '--my': `${mouseY}%`,
    }"
  >
    <div class="grain-layer"></div>
    <div class="halo-layer"></div>

    <header class="intro-title">
      <h1 class="title-main">消费数据分析系统</h1>
      <p class="title-sub">{{ animatedSubtitle }}<span class="typing-caret">|</span></p>
    </header>

    <section class="auth-card">
      <div class="form-head">
        <div v-if="!isForgetMode" class="switcher">
          <div class="switch-indicator" :style="{ transform: `translateX(${switchOffset})` }"></div>
          <button type="button" class="switch-btn" :class="{ active: isLoginMode }" @click="switchMode('login')">
            Login
          </button>
          <button
            type="button"
            class="switch-btn"
            :class="{ active: isRegisterMode }"
            @click="switchMode('register')"
          >
            Register
          </button>
        </div>
        <h2 v-else>Reset Password</h2>
      </div>

      <transition name="panel" mode="out-in">
        <el-form v-if="isLoginMode" key="login" size="large" autocomplete="off" class="auth-form">
          <el-form-item>
            <el-input v-model="formModel.userEmail" :prefix-icon="Message" placeholder="Email" />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="formModel.password"
              :prefix-icon="Lock"
              type="password"
              show-password
              placeholder="Password"
            />
          </el-form-item>
          <el-form-item class="align-right">
            <el-link type="info" underline="never" @click="switchMode('forget')">Forgot password?</el-link>
          </el-form-item>
          <el-form-item>
            <el-button class="submit-btn" type="primary" @click="onLogin">Login</el-button>
          </el-form-item>
        </el-form>

        <el-form v-else-if="isRegisterMode" key="register" size="large" autocomplete="off" class="auth-form">
          <el-form-item
            :error="registerEmailAvailable === false ? registerEmailHint : ''"
            :validate-status="
              checkingRegisterEmail
                ? 'validating'
                : registerEmailAvailable === true
                  ? 'success'
                  : registerEmailAvailable === false
                    ? 'error'
                    : ''
            "
          >
            <el-input
              v-model.trim="formModel.userEmail"
              :prefix-icon="Message"
              placeholder="Email"
              @blur="onRegisterEmailBlur"
            />
          </el-form-item>
          <p v-if="registerEmailAvailable === true" class="email-ok-tip">{{ registerEmailHint }}</p>
          <el-form-item>
            <el-input v-model="formModel.userName" :prefix-icon="User" placeholder="Username" />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="formModel.password"
              :prefix-icon="Lock"
              type="password"
              show-password
              placeholder="Password"
            />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="formModel.password2"
              :prefix-icon="Lock"
              type="password"
              show-password
              placeholder="Confirm password"
            />
          </el-form-item>
          <el-form-item>
            <el-input v-model="formModel.code" placeholder="Code">
              <template #append>
                <el-button class="code-btn" @click="sendCode" :disabled="countDown > 0">
                  {{ countDown > 0 ? `${countDown}s` : "Send code" }}
                </el-button>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-button class="submit-btn" type="primary" @click="onRegister">Create account</el-button>
          </el-form-item>
        </el-form>

        <el-form v-else key="forget" size="large" autocomplete="off" class="auth-form">
          <el-form-item>
            <el-input v-model="formModel.userEmail" :prefix-icon="Message" placeholder="Email" />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="formModel.newPassword"
              :prefix-icon="Lock"
              type="password"
              show-password
              placeholder="New password"
            />
          </el-form-item>
          <el-form-item>
            <el-input v-model="formModel.code" placeholder="Code">
              <template #append>
                <el-button class="code-btn" @click="sendCode" :disabled="countDown > 0">
                  {{ countDown > 0 ? `${countDown}s` : "Send code" }}
                </el-button>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-button class="submit-btn" type="primary" @click="onForgetPassword">Update password</el-button>
          </el-form-item>
          <el-form-item class="align-right">
            <el-link type="info" underline="never" @click="switchMode('login')">Back to login</el-link>
          </el-form-item>
        </el-form>
      </transition>
    </section>
  </div>
</template>

<style>
.auth-page {
  min-height: 100dvh;
  padding: 24px 16px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(42rem 22rem at var(--mx) var(--my), rgba(255, 255, 255, 0.09), rgba(255, 255, 255, 0)),
    radial-gradient(58rem 38rem at 50% -8%, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0)),
    linear-gradient(160deg, #020202 0%, #0a0a0a 48%, #010101 100%);
  isolation: isolate;
}

.grain-layer {
  position: absolute;
  inset: -40%;
  background-image: radial-gradient(circle, rgba(255, 255, 255, 0.08) 0.55px, transparent 0.65px);
  background-size: 3px 3px;
  opacity: 0.045;
  z-index: -2;
  animation: grain-shift 13s linear infinite;
}

.halo-layer {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(30rem 18rem at 50% 22%, rgba(255, 255, 255, 0.09), rgba(255, 255, 255, 0)),
    radial-gradient(22rem 11rem at 50% 66%, rgba(255, 255, 255, 0.045), rgba(255, 255, 255, 0));
  z-index: -1;
  pointer-events: none;
}

.intro-title {
  width: min(760px, 100%);
  margin-top: 8vh;
  text-align: center;
  opacity: 0;
  transform: translateY(-30px) scale(0.98);
  animation: title-reveal 1.1s cubic-bezier(0.2, 0.76, 0.28, 1) forwards;
  animation-delay: 0.5s;
}

.title-main {
  margin: 0;
  font-size: clamp(28px, 5.2vw, 56px);
  letter-spacing: 0.08em;
  font-weight: 800;
  line-height: 1.12;
  background: linear-gradient(95deg, #0a0a0a 8%, #f7f7f7 46%, #1a1a1a 95%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  -webkit-text-stroke: 1px rgba(6, 6, 6, 0.9);
  text-shadow:
    0 0 8px rgba(255, 255, 255, 0.22),
    0 0 25px rgba(255, 255, 255, 0.16);
}

.title-sub {
  margin: 12px 0 0;
  color: rgba(255, 255, 255, 0.72);
  letter-spacing: 0.22em;
  font-size: 12px;
  text-transform: uppercase;
}

.typing-caret {
  animation: blink-caret 0.9s steps(1, end) infinite;
}

.auth-card {
  width: min(560px, 100%);
  margin-top: 10vh;
  border-radius: 22px;
  border: 1px solid rgba(255, 255, 255, 0.78);
  background:
    linear-gradient(145deg, rgba(0, 0, 0, 0.92), rgba(7, 7, 7, 0.88)),
    radial-gradient(30rem 16rem at 50% 0%, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0));
  box-shadow:
    0 20px 60px rgba(0, 0, 0, 0.64),
    inset 0 0 0 1px rgba(255, 255, 255, 0.08),
    inset 0 16px 36px rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(10px);
  padding: 26px 24px 18px;
  position: relative;
  opacity: 0;
  transform: translateY(38px) scale(0.96);
  animation: panel-reveal 0.95s cubic-bezier(0.18, 0.72, 0.29, 1) forwards;
  animation-delay: 1s;
}

.form-head {
  margin-bottom: 22px;
}

.form-head h2 {
  margin: 8px 0 0;
  font-size: 24px;
  color: rgba(255, 255, 255, 0.9);
}

.switcher {
  background: rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 4px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  position: relative;
  border: 1px solid rgba(255, 255, 255, 0.22);
}

.switch-indicator {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: calc(100% - 8px);
  border-radius: 9px;
  background: linear-gradient(140deg, rgba(255, 255, 255, 0.9), rgba(220, 220, 220, 0.78));
  box-shadow: 0 0 15px rgba(255, 255, 255, 0.32);
  transition: transform 0.32s ease;
}

.switch-btn {
  border: 0;
  background: transparent;
  height: 40px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.64);
  z-index: 1;
  transition: color 0.24s ease;
}

.switch-btn.active {
  color: #090909;
}

.auth-form {
  width: 100%;
}

.submit-btn {
  width: 100%;
  height: 46px;
  margin-top: 4px;
  border-radius: 11px;
  font-weight: 700;
  border: 1px solid rgba(255, 255, 255, 0.68);
  background: linear-gradient(135deg, #ececec, #b9b9b9);
  color: #090909;
  transition: transform 0.18s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(255, 255, 255, 0.18);
  background: linear-gradient(135deg, #fafafa, #c8c8c8);
}

.submit-btn:active {
  transform: translateY(0);
}

.align-right {
  display: flex;
  justify-content: flex-end;
}

.code-btn {
  min-width: 102px;
  font-weight: 700;
  color: #0e0e0e;
}

.email-ok-tip {
  margin: -10px 0 12px;
  font-size: 12px;
  color: rgba(218, 218, 218, 0.92);
}

.panel-enter-active,
.panel-leave-active {
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.panel-enter-from,
.panel-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

.auth-form .el-form-item {
  margin-bottom: 18px;
}

.auth-form .el-input__wrapper {
  min-height: 46px;
  border-radius: 11px;
  border: 1px solid transparent;
  background: rgba(0, 0, 0, 0.82);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.26);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.auth-form .el-input__wrapper:hover {
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.48),
    0 0 18px rgba(255, 255, 255, 0.06);
}

.auth-form .el-input__wrapper.is-focus {
  transform: translateY(-1px);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.78),
    0 0 22px rgba(255, 255, 255, 0.16);
}

.auth-form .el-input__inner,
.auth-form .el-input__icon,
.auth-form .el-input-group__append,
.auth-form .el-link {
  color: rgba(248, 248, 248, 0.94);
}

.auth-form .el-link:hover {
  color: #ffffff;
}

.auth-form .el-input-group__append .el-button {
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 8px;
  margin: 3px;
  background: linear-gradient(140deg, rgba(255, 255, 255, 0.85), rgba(221, 221, 221, 0.8));
  color: #0f0f0f;
}

.auth-form .el-input-group__append .el-button:hover {
  background: linear-gradient(140deg, #ffffff, #d9d9d9);
}

@keyframes title-reveal {
  from {
    opacity: 0;
    transform: translateY(-30px) scale(0.98);
    filter: blur(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
    filter: blur(0);
  }
}

@keyframes panel-reveal {
  from {
    opacity: 0;
    transform: translateY(38px) scale(0.96);
    filter: blur(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
    filter: blur(0);
  }
}

@keyframes grain-shift {
  0% {
    transform: translate(0, 0);
  }
  25% {
    transform: translate(-2%, 3%);
  }
  50% {
    transform: translate(1%, -2%);
  }
  75% {
    transform: translate(3%, 1%);
  }
  100% {
    transform: translate(0, 0);
  }
}

@keyframes blink-caret {
  0%,
  49% {
    opacity: 1;
  }
  50%,
  100% {
    opacity: 0;
  }
}

@media (max-width: 860px) {
  .intro-title {
    margin-top: 6vh;
  }

  .title-main {
    letter-spacing: 0.05em;
  }

  .auth-card {
    margin-top: 8vh;
    padding: 20px 16px 10px;
  }
}
</style>
