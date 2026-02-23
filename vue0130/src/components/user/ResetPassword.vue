<template>
  <div class="reset-password">
    <el-dialog
      v-model="dialogVisible"
      class="reset-password-dialog"
      width="530px"
      append-to-body
      :close-on-click-modal="false"
      :show-close="true"
    >
      <template #header>
        <div class="dialog-header">
          <div class="header-badge">
            <el-icon><Lock /></el-icon>
          </div>
          <div class="header-texts">
            <h3>{{ text.title }}</h3>
            <p>{{ text.subtitle }}</p>
          </div>
        </div>
      </template>

      <div class="change-password-form">
        <el-form label-position="top" class="form-body">
          <el-form-item :label="text.email">
            <el-input v-model="changePasswordForm.userEmail" :placeholder="text.enterEmail" disabled>
              <template #prefix>
                <el-icon><Message /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item :label="text.code">
            <el-input
              v-model="changePasswordForm.validateCode"
              :placeholder="text.enterCode"
              :maxlength="CODE_MAX_LEN"
              inputmode="numeric"
              autocomplete="one-time-code"
              clearable
              @input="onCodeInput"
            >
              <template #prefix>
                <el-icon><Key /></el-icon>
              </template>
              <template #append>
                <el-button
                  class="code-btn"
                  @click="sendChangePasswordCode"
                  :disabled="countDown > 0"
                  :loading="sendingCode"
                >
                  {{ countDown > 0 ? `${countDown}${text.countdownSuffix}` : text.sendCode }}
                </el-button>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item :label="text.newPassword">
            <el-input
              v-model="changePasswordForm.password"
              type="password"
              :placeholder="text.enterNewPassword"
              show-password
              clearable
            >
              <template #prefix>
                <el-icon><Lock /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item :label="text.confirmPassword" class="confirm-item">
            <el-input
              v-model="changePasswordForm.confirmPassword"
              type="password"
              :placeholder="text.enterConfirmPassword"
              show-password
              clearable
            >
              <template #prefix>
                <el-icon><Lock /></el-icon>
              </template>
            </el-input>
          </el-form-item>
        </el-form>

        <div class="strength-wrap">
          <div class="strength-top">
            <span>{{ text.strength }}</span>
            <b :class="strengthView.className">{{ strengthView.label }}</b>
          </div>
          <div class="strength-track">
            <span class="strength-fill" :class="strengthView.className" :style="{ width: `${strengthView.percent}%` }"></span>
          </div>
        </div>

        <div class="password-hints">
          <span :class="{ ok: passwordChecks.length }">{{ text.ruleLength }}</span>
          <span :class="{ ok: passwordChecks.letter }">{{ text.ruleLetter }}</span>
          <span :class="{ ok: passwordChecks.number }">{{ text.ruleNumber }}</span>
          <span :class="{ ok: passwordChecks.same }">{{ text.ruleMatch }}</span>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button class="cancel-btn" @click="dialogVisible = false">{{ text.cancel }}</el-button>
          <el-button class="submit-btn" type="primary" @click="handleChangePassword" :loading="submitting">
            {{ text.confirmChange }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import * as authApi from '@/api/modules/auth';
import { ElMessage } from 'element-plus';
import { Key, Lock, Message } from '@element-plus/icons-vue';

const text = {
  title: '修改密码',
  subtitle: '为了账号安全，请先完成邮箱验证码校验',
  email: '邮箱',
  code: '验证码',
  newPassword: '新密码',
  confirmPassword: '确认密码',
  enterEmail: '请输入邮箱',
  enterCode: '请输入验证码',
  enterNewPassword: '请输入新密码',
  enterConfirmPassword: '请再次输入密码',
  countdownSuffix: '秒后重试',
  sendCode: '发送验证码',
  cancel: '取消',
  confirmChange: '确认修改',
  fetchUserInfoFailed: '获取用户信息失败',
  emailRequired: '请输入邮箱',
  codeSendSuccess: '验证码发送成功',
  codeSendFailed: '验证码发送失败',
  codeRequired: '请输入验证码',
  newPasswordRequired: '请输入新密码',
  passwordNotMatch: '两次输入的密码不一致',
  changeSuccess: '密码修改成功',
  changeFailed: '密码修改失败',
  passwordWeak: '密码至少 8 位，且包含字母和数字',
  strength: '密码强度',
  strengthNone: '未设置',
  strengthWeak: '弱',
  strengthMedium: '中',
  strengthStrong: '强',
  ruleLength: '8位以上',
  ruleLetter: '包含字母',
  ruleNumber: '包含数字',
  ruleMatch: '两次输入一致',
};

const props = defineProps<{
  visible: boolean;
}>();
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void;
}>();

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});
const router = useRouter();

const changePasswordForm = ref({
  userEmail: '',
  password: '',
  confirmPassword: '',
  validateCode: '',
});

const sendingCode = ref(false);
const submitting = ref(false);
const countDown = ref(0);
const timer = ref<number | null>(null);
const CODE_MAX_LEN = 6;

const passwordChecks = computed(() => {
  const password = changePasswordForm.value.password || '';
  const confirm = changePasswordForm.value.confirmPassword || '';
  return {
    length: password.length >= 8,
    letter: /[a-zA-Z]/.test(password),
    number: /\d/.test(password),
    same: Boolean(password) && password === confirm,
  };
});

const strengthView = computed(() => {
  const password = changePasswordForm.value.password || '';
  if (!password) {
    return { label: text.strengthNone, className: 'none', percent: 0 };
  }

  let score = 0;
  if (password.length >= 8) score += 1;
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score += 1;
  if (/[a-zA-Z]/.test(password)) score += 1;
  if (/\d/.test(password)) score += 1;
  if (/[^a-zA-Z0-9]/.test(password) || password.length >= 12) score += 1;

  if (score <= 2) {
    return { label: text.strengthWeak, className: 'weak', percent: 35 };
  }
  if (score <= 4) {
    return { label: text.strengthMedium, className: 'medium', percent: 68 };
  }
  return { label: text.strengthStrong, className: 'strong', percent: 100 };
});

const fetchUserInfo = async () => {
  try {
    const info = await authApi.getUserInfo();
    changePasswordForm.value.userEmail = info?.data?.data?.userEmail || info?.data?.userEmail || '';
  } catch (err: any) {
    ElMessage.error(text.fetchUserInfoFailed);
    console.error(`${text.fetchUserInfoFailed}:`, err);
  }
};

function clearTimer() {
  if (timer.value) {
    clearInterval(timer.value);
    timer.value = null;
  }
}

function normalizeCode(raw: string) {
  return String(raw ?? '')
    .replace(/\D+/g, '')
    .slice(0, CODE_MAX_LEN);
}

function onCodeInput(value: string) {
  const normalized = normalizeCode(value);
  if (normalized !== changePasswordForm.value.validateCode) {
    changePasswordForm.value.validateCode = normalized;
  }
}

onMounted(() => {
  void fetchUserInfo();
});

watch(
  () => props.visible,
  (visible) => {
    if (!visible) return;
    changePasswordForm.value.password = '';
    changePasswordForm.value.confirmPassword = '';
    changePasswordForm.value.validateCode = '';
    submitting.value = false;
    void fetchUserInfo();
  }
);

onUnmounted(() => {
  clearTimer();
});

const sendChangePasswordCode = async () => {
  const email = changePasswordForm.value.userEmail;
  if (!email) {
    ElMessage.warning(text.emailRequired);
    return;
  }
  if (sendingCode.value) return;

  sendingCode.value = true;
  try {
    await authApi.sendCode(email);
    ElMessage.success(text.codeSendSuccess);
    clearTimer();
    countDown.value = 60;
    timer.value = window.setInterval(() => {
      countDown.value--;
      if (countDown.value <= 0) {
        clearTimer();
        countDown.value = 0;
      }
    }, 1000);
  } catch (e) {
    ElMessage.error(text.codeSendFailed);
    console.error(`${text.codeSendFailed}:`, e);
  } finally {
    sendingCode.value = false;
  }
};

const handleChangePassword = async () => {
  const form = changePasswordForm.value;

  if (!form.userEmail) {
    ElMessage.warning(text.emailRequired);
    return;
  }
  if (!form.validateCode) {
    ElMessage.warning(text.codeRequired);
    return;
  }
  form.validateCode = normalizeCode(form.validateCode);
  if (!form.password) {
    ElMessage.warning(text.newPasswordRequired);
    return;
  }
  if (!passwordChecks.value.length || !passwordChecks.value.letter || !passwordChecks.value.number) {
    ElMessage.warning(text.passwordWeak);
    return;
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning(text.passwordNotMatch);
    return;
  }
  if (submitting.value) return;

  submitting.value = true;
  try {
    const res = await authApi.updatePassword({
      userEmail: form.userEmail,
      password: form.password,
      validateCode: form.validateCode,
    });

    const code = res?.data?.code ?? res?.code;
    if (code !== 200) {
      ElMessage.error(res?.data?.msg || res?.msg || text.changeFailed);
      return;
    }

    ElMessage.success(text.changeSuccess);
    dialogVisible.value = false;
    changePasswordForm.value = {
      userEmail: form.userEmail,
      password: '',
      confirmPassword: '',
      validateCode: '',
    };
    clearTimer();
    countDown.value = 0;
    localStorage.removeItem('token');
    sessionStorage.removeItem('token');
    await router.replace('/login');
  } catch (e) {
    ElMessage.error(text.changeFailed);
    console.error(`${text.changeFailed}:`, e);
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.reset-password {
  width: 0;
  height: 0;
  overflow: visible;
}

:deep(.reset-password-dialog .el-dialog) {
  border-radius: 18px;
  overflow: hidden;
  border: 1px solid rgba(20, 20, 20, 0.58);
  background:
    radial-gradient(46rem 30rem at -10% -16%, rgba(255, 255, 255, 0.5), rgba(255, 255, 255, 0)),
    linear-gradient(158deg, rgba(255, 255, 255, 0.96), rgba(230, 230, 230, 0.95));
  box-shadow:
    0 24px 58px rgba(0, 0, 0, 0.36),
    0 8px 20px rgba(0, 0, 0, 0.2);
}

:deep(.reset-password-dialog .el-dialog__header) {
  margin: 0;
  padding: 20px 24px 12px;
  border-bottom: 1px solid rgba(20, 20, 20, 0.12);
}

:deep(.reset-password-dialog .el-dialog__body) {
  padding: 16px 24px 10px;
}

:deep(.reset-password-dialog .el-dialog__footer) {
  padding: 12px 24px 22px;
}

:deep(.reset-password-dialog .el-dialog__headerbtn) {
  top: 16px;
  right: 16px;
}

:deep(.reset-password-dialog .el-dialog__headerbtn .el-dialog__close) {
  color: rgba(25, 25, 25, 0.72);
  transition: color 0.2s ease;
}

:deep(.reset-password-dialog .el-dialog__headerbtn:hover .el-dialog__close) {
  color: #111;
}

.dialog-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-badge {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #111, #3d3d3d);
  box-shadow: 0 9px 20px rgba(17, 17, 17, 0.35);
}

.header-texts h3 {
  margin: 0;
  font-size: 24px;
  line-height: 1.08;
  color: #111;
}

.header-texts p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #5b5b5b;
}

.form-body {
  margin-top: 4px;
}

:deep(.form-body .el-form-item) {
  margin-bottom: 14px;
}

:deep(.form-body .confirm-item) {
  margin-bottom: 8px;
}

:deep(.form-body .el-form-item__label) {
  color: #2f2f2f;
  font-weight: 600;
  line-height: 1.2;
  padding-bottom: 7px;
}

:deep(.form-body .el-input__wrapper) {
  border-radius: 12px;
  padding: 2px 12px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 0 0 1px rgba(17, 17, 17, 0.2) inset;
  transition: box-shadow 0.2s ease, background-color 0.2s ease;
}

:deep(.form-body .el-input) {
  width: 100%;
}

:deep(.form-body .el-input-group) {
  width: 100%;
  max-width: 100%;
  display: flex;
  overflow: hidden;
  border-radius: 12px;
}

:deep(.form-body .el-input-group .el-input__wrapper) {
  flex: 1 1 auto;
  min-width: 0;
  border-radius: 12px 0 0 12px;
}

:deep(.form-body .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(17, 17, 17, 0.35) inset;
}

:deep(.form-body .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1.6px rgba(17, 17, 17, 0.88) inset;
}

:deep(.form-body .el-input-group__append) {
  flex: 0 0 auto;
  padding: 0;
  margin-left: 0 !important;
  border-left: 1px solid rgba(17, 17, 17, 0.16);
  background: #f2f2f2;
  border-radius: 0 12px 12px 0;
}

:deep(.form-body .el-input-group__append .el-button) {
  margin: 0;
}

.code-btn {
  border: none;
  border-radius: 0 12px 12px 0;
  min-width: 108px;
  height: 34px;
  color: #fff;
  background: linear-gradient(135deg, #131313, #2f2f2f);
}

.code-btn:hover {
  color: #fff;
  background: linear-gradient(135deg, #090909, #3b3b3b);
}

.strength-wrap {
  margin-top: 6px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(17, 17, 17, 0.13);
  background: rgba(255, 255, 255, 0.58);
}

.strength-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #5d5d5d;
}

.strength-top b {
  font-size: 12px;
  font-weight: 700;
}

.strength-top b.none {
  color: #7c7c7c;
}

.strength-top b.weak {
  color: #b42318;
}

.strength-top b.medium {
  color: #b54708;
}

.strength-top b.strong {
  color: #067647;
}

.strength-track {
  margin-top: 7px;
  height: 7px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(17, 17, 17, 0.12);
}

.strength-fill {
  display: block;
  height: 100%;
  width: 0;
  border-radius: 999px;
  transition: width 0.22s ease;
}

.strength-fill.none {
  background: #b9b9b9;
}

.strength-fill.weak {
  background: linear-gradient(90deg, #ef4444, #f87171);
}

.strength-fill.medium {
  background: linear-gradient(90deg, #f59e0b, #fbbf24);
}

.strength-fill.strong {
  background: linear-gradient(90deg, #16a34a, #22c55e);
}

.password-hints {
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.password-hints span {
  font-size: 12px;
  color: #5d5d5d;
  padding: 6px 10px;
  border-radius: 10px;
  border: 1px solid rgba(17, 17, 17, 0.2);
  background: rgba(255, 255, 255, 0.84);
  transition: all 0.2s ease;
}

.password-hints span.ok {
  color: #fff;
  border-color: rgba(12, 12, 12, 0.55);
  background: linear-gradient(135deg, #111, #303030);
  box-shadow: 0 7px 15px rgba(0, 0, 0, 0.2);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

:deep(.dialog-footer .el-button) {
  min-width: 94px;
  border-radius: 10px;
  transition: all 0.2s ease;
}

.cancel-btn {
  border-color: rgba(17, 17, 17, 0.26);
  color: #2e2e2e;
  background: rgba(255, 255, 255, 0.7);
}

.cancel-btn:hover {
  border-color: rgba(17, 17, 17, 0.5);
  color: #111;
  background: #fff;
}

.submit-btn {
  border: 1px solid rgba(15, 15, 15, 0.86);
  background: linear-gradient(135deg, #121212, #3b3b3b);
  color: #fff;
}

.submit-btn:hover {
  border-color: #111;
  background: linear-gradient(135deg, #0a0a0a, #505050);
}

@media (max-width: 640px) {
  :deep(.reset-password-dialog) {
    width: calc(100vw - 24px) !important;
    margin: 12px auto;
  }

  :deep(.reset-password-dialog .el-dialog__header),
  :deep(.reset-password-dialog .el-dialog__body),
  :deep(.reset-password-dialog .el-dialog__footer) {
    padding-left: 16px;
    padding-right: 16px;
  }
}
</style>
