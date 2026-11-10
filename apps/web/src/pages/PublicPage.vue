<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { api, type Session } from '../api/client'
import { useSession } from '../stores/session'
import { safeNext } from '../navigation'
const route = useRoute()
const router = useRouter()
const store = useSession()
const mode = computed(() => route.path.slice(1))
const email = ref(''), password = ref(''), confirmation = ref(''), invite = ref('')
const displayName = ref(store.session?.user.displayName || '')
const timezone = ref(store.session?.user.timezone || 'Asia/Seoul')
const currency = ref(store.session?.user.baseCurrency || 'KRW')
const showPassword = ref(false), busy = ref(false), error = ref(''), success = ref('')
const errorRegion = ref<HTMLElement>()
async function enterGuest() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    store.session = await api<Session>('/auth/guest', { method: 'POST' })
    store.checked = true
    await router.push(safeNext(route.query.next, 'USER'))
  } catch (err) {
    error.value = err instanceof Error ? err.message : '체험을 시작하지 못했습니다. 다시 시도해 주세요.'
    await nextTick(); errorRegion.value?.focus()
  } finally { busy.value = false }
}
watch(mode, () => { error.value = ''; success.value = ''; password.value = ''; confirmation.value = ''; invite.value = ''; if (mode.value === 'onboarding') { displayName.value = store.session?.user.displayName || ''; timezone.value = store.session?.user.timezone || 'Asia/Seoul'; currency.value = store.session?.user.baseCurrency || 'KRW' } })
const title = computed(() => ({ login: '다시 만나서 반가워요', signup: '나만의 오늘을 시작해요', onboarding: '당신에게 맞는 오늘', privacy: '개인정보 안내' })[mode.value] || '오늘을 조금 더 나답게.')
async function submit() {
  busy.value = true; error.value = ''
  try {
    if (mode.value === 'signup') {
      if (new TextEncoder().encode(password.value).length > 72) throw new Error('비밀번호는 UTF-8 기준 72바이트 이내로 입력해 주세요.')
      if (password.value !== confirmation.value) throw new Error('비밀번호 확인이 일치하지 않습니다.')
      await api('/auth/signup', { method: 'POST', body: JSON.stringify({ email: email.value, password: password.value, passwordConfirmation: confirmation.value, displayName: displayName.value, inviteToken: invite.value }) })
      await router.push('/login'); success.value = '계정이 만들어졌습니다. 로그인해 주세요.'
    } else if (mode.value === 'login') {
      store.session = await api<Session>('/auth/login', { method: 'POST', body: JSON.stringify({ email: email.value, password: password.value }) })
      store.checked = true
      const destination = safeNext(route.query.next, store.session.user.role)
      await router.push(store.session.onboardingCompleted ? destination : { path: '/onboarding', query: { next: destination } })
    } else {
      await api('/users/me/settings', { method: 'PATCH', body: JSON.stringify({ displayName: displayName.value, timezone: timezone.value, baseCurrency: currency.value }) })
      await store.refresh()
      if (store.session?.user.role === 'USER' && !store.session.user.guest) {
        try { sessionStorage.setItem('mytoday-first-welcome', store.session.user.id) } catch { /* Storage can be unavailable in private browsing. */ }
      }
      await router.push(safeNext(route.query.next, store.session?.user.role))
    }
    password.value = ''; confirmation.value = ''; invite.value = ''
  } catch (err) {
    error.value = err instanceof TypeError ? '서버에 연결하지 못했습니다. 연결 상태를 확인하고 다시 시도해 주세요.' : err instanceof Error ? err.message : '다시 시도해 주세요.'
    await nextTick(); errorRegion.value?.focus()
  } finally { busy.value = false }
}
</script>
<template>
  <main class="public-shell">
    <section class="story-panel">
      <RouterLink to="/welcome" class="brand" aria-label="MY TODAY 시작 화면">MY<span>TODAY</span><span class="brand-dot">.</span></RouterLink>
      <div class="story-body"><div class="eyebrow">A LITTLE MORE YOU, EVERY DAY</div>
        <h1>흩어진 일상을,<br>하나의 오늘로.</h1><p>계획하고, 실행하고, 돌아보는 시간.<br>당신의 하루가 조금 더 선명해집니다.</p>
        <div class="day-illustration" aria-hidden="true"><div class="mini-date"><span>MY DAILY SPACE</span><strong>작은 실천이 만드는<br>나만의 좋은 하루</strong></div><div class="mini-line"><i class="mini-check">✓</i> 오늘 가장 중요한 일 하나</div><div class="mini-line"><i class="mini-check pale">✓</i> 나를 위한 시간 만들기</div><div class="mini-progress"><span></span></div><div class="mini-footer">완벽한 하루보다, 나다운 하루.</div></div>
      </div><div class="story-footer">나의 시간 · 나의 기록 · 나의 성장</div>
    </section>
    <section class="auth-panel">
      <RouterLink to="/welcome" class="brand mobile-brand">MY<span>TODAY</span>.</RouterLink>
      <div class="auth-content" :key="mode">
        <div class="eyebrow">{{ mode === 'onboarding' ? 'MAKE IT YOURS' : 'YOUR PERSONAL LIFE OS' }}</div>
        <h2>{{ title }}</h2>
        <template v-if="mode === 'welcome'">
          <p class="intro">바쁜 일상 속, 나에게 집중하는 공간.<br>작은 계획부터 차근차근 시작해 보세요.</p>
          <div class="welcome-features"><div><span class="feature-number">01</span><div><strong>오늘에 집중하기</strong><p>일정과 할 일을 한눈에 정리해요.</p></div></div><div><span class="feature-number">02</span><div><strong>꾸준함을 기록하기</strong><p>실제 활동을 모아 나의 성장을 돌아봐요.</p></div></div><div><span class="feature-number">03</span><div><strong>일상을 연결하기</strong><p>시간과 돈, 목표를 하나의 공간에 담아요.</p></div></div></div>
          <q-btn to="/login" label="로그인" color="primary" unelevated class="full-width" />
          <q-btn to="/signup" label="초대받아 시작하기" outline color="primary" class="full-width q-mt-sm" />
          <q-btn label="게스트로 체험하기" flat color="primary" class="full-width q-mt-sm" :loading="busy" @click="enterGuest" />
          <p v-if="error" ref="errorRegion" role="alert" aria-live="polite" tabindex="-1" class="error-message">{{ error }}</p>
          <p class="field-hint">게스트 기록은 다른 사용자와 분리되며 24시간 후 만료됩니다. 계속 사용하려면 초대 가입으로 새 계정을 만드세요.</p>
          <p class="auth-note">현재는 초대받은 사용자만 가입할 수 있습니다.</p>
        </template>
        <template v-else-if="mode === 'privacy'"><p class="intro">이 서비스는 개인용 개발 베타입니다.</p><p>이메일, 암호화된 비밀번호 해시, 표시 이름과 시간대·통화 설정을 서버에 저장합니다. 비밀번호 원문은 저장하지 않습니다. 게스트 기록은 별도 임시 계정에 저장하고 24시간 후 접근이 만료됩니다. 로그인 쿠키는 세션 인증에 사용하며 개인 API 응답은 오프라인 캐시에 저장하지 않습니다.</p><p>공개 가입과 자동 계정 복구는 제공하지 않습니다. 데이터 삭제 및 계정 지원은 초대를 제공한 운영자에게 요청해 주세요.</p><q-btn to="/welcome" outline color="primary" label="시작 화면" /></template>
        <form v-else @submit.prevent="submit">
          <p class="intro">{{ mode === 'login' ? '이메일로 로그인하고 나의 하루를 이어가세요.' : mode === 'signup' ? '초대 코드를 입력해 나만의 공간을 만드세요.' : '기본 설정을 마치면 오늘을 시작할 수 있어요.' }}</p>
          <div v-if="success" class="success-message" role="status">{{ success }}</div>
          <div v-if="error" ref="errorRegion" role="alert" aria-live="polite" tabindex="-1" class="error-message">{{ error }}</div>
          <q-input v-if="mode !== 'login'" v-model="displayName" outlined label="표시 이름" autocomplete="nickname" :maxlength="60" :required="mode === 'onboarding'" class="q-mb-md" />
          <template v-if="mode !== 'onboarding'">
            <q-input v-model="email" outlined type="email" label="이메일" autocomplete="username" required :maxlength="254" class="q-mb-md" />
            <q-input v-model="password" outlined :type="showPassword ? 'text' : 'password'" label="비밀번호" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" required :minlength="mode === 'signup' ? 12 : undefined" :maxlength="72" class="q-mb-sm" />
            <q-checkbox v-model="showPassword" label="비밀번호 표시" dense class="q-mb-md" />
            <template v-if="mode === 'signup'"><p class="field-hint">12자 이상, UTF-8 기준 72바이트 이내로 입력해 주세요.</p><q-input v-model="confirmation" outlined type="password" label="비밀번호 확인" autocomplete="new-password" required :maxlength="72" class="q-mb-md" /><q-input v-model="invite" outlined label="초대 코드" type="password" autocomplete="off" required class="q-mb-md" /><p class="field-hint">가입 전 <RouterLink to="/privacy">개인정보 안내</RouterLink>를 확인해 주세요.</p></template>
          </template>
          <template v-else><q-select v-model="timezone" outlined label="시간대" :options="['Asia/Seoul', 'Asia/Tokyo', 'America/New_York', 'America/Los_Angeles', 'Europe/London', 'UTC']" class="q-mb-md" /><q-select v-model="currency" outlined label="기본 통화" :options="['KRW', 'USD', 'JPY', 'EUR', 'GBP']" class="q-mb-lg" /></template>
          <q-btn type="submit" :label="mode === 'login' ? '로그인' : mode === 'signup' ? '계정 만들기' : '오늘 시작하기'" :loading="busy" unelevated color="primary" class="full-width" />
          <p v-if="mode === 'login'" class="auth-note">처음 오셨나요? <RouterLink to="/signup">초대 가입</RouterLink><br><q-btn label="게스트로 체험하기" flat color="primary" :loading="busy" @click="enterGuest" /><br><span class="recovery-note">계정 복구는 초대를 제공한 운영자에게 문의해 주세요.</span></p>
          <p v-if="mode === 'signup'" class="auth-note">이미 계정이 있나요? <RouterLink to="/login">로그인</RouterLink></p>
        </form>
      </div><footer class="auth-footer">© {{ new Date().getFullYear() }} MY TODAY <RouterLink to="/privacy">개인정보 안내</RouterLink></footer>
    </section>
  </main>
</template>
