<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useSession } from '../stores/session'
import { ApiError, api } from '../api/client'
import PlanningPage from './PlanningPage.vue'
import AdminPage from './AdminPage.vue'
import GrowthPage from './GrowthPage.vue'
import MoneyPage from './MoneyPage.vue'
import InvestPage from './InvestPage.vue'
const store = useSession(), route = useRoute(), router = useRouter()
const nav = [{ path: '/today', label: '오늘', icon: '◷' }, { path: '/calendar', label: '일정', icon: '▦' }, { path: '/tasks', label: '할 일', icon: '☑' }, { path: '/growth', label: '성장', icon: '↗' }, { path: '/money', label: '가계부', icon: '₩' }, { path: '/invest', label: '투자', icon: '▥' }]
const current = computed(() => route.path === '/admin' ? '관리자' : nav.find(n => n.path === route.path)?.label || '설정')
const error = ref(''), busy = ref(false)
const displayName = ref(store.session?.user.displayName || '')
const timezone = ref(store.session?.user.timezone || 'Asia/Seoul')
const baseCurrency = ref(store.session?.user.baseCurrency || 'KRW')
const notice = ref('')
async function logout() {
  busy.value = true; error.value = ''
  try { await store.logout(); await router.replace('/welcome') }
  catch { error.value = '로그아웃하지 못했습니다. 연결을 확인하고 다시 시도해 주세요.' }
  finally { busy.value = false }
}
async function save() {
  busy.value = true; error.value = ''; notice.value = ''
  try {
    await api('/users/me/settings', { method: 'PATCH', body: JSON.stringify({ displayName: displayName.value, timezone: timezone.value, baseCurrency: baseCurrency.value }) })
    await store.refresh(); notice.value = '설정을 저장했습니다.'
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) { store.session = null; store.checked = false; await router.replace('/login'); return }
    error.value = err instanceof Error ? err.message : '설정을 저장하지 못했습니다.'
  } finally { busy.value = false }
}
</script>
<template>
  <div v-if="store.session" class="app-shell">
    <aside class="sidebar"><RouterLink to="/today" class="brand">MY<span>TODAY</span>.</RouterLink><div class="sidebar-caption">MY DAILY SPACE</div><nav aria-label="주 메뉴"><RouterLink v-for="item in nav" :key="item.path" :to="item.path" :class="{ active: route.path === item.path }"><span aria-hidden="true">{{ item.icon }}</span>{{ item.label }}</RouterLink></nav><div class="sidebar-bottom"><span class="beta-badge">PERSONAL BETA</span><p>나의 속도로, 나의 오늘을.</p><RouterLink to="/settings">⚙ 설정</RouterLink></div></aside>
    <main class="workspace"><header class="topbar"><span>{{ current }}</span><div><span class="profile-avatar">{{ store.session.user.displayName.slice(0, 1) }}</span><span class="topbar-name">{{ store.session.user.displayName }}</span><span class="tag account-role">{{ store.session.user.guest ? '게스트 · 24시간 체험' : store.session.user.role === 'ADMIN' ? '관리자' : '일반 사용자' }}</span><q-btn v-if="store.session.user.role === 'ADMIN'" to="/admin" flat label="관리자 화면" /><q-btn flat label="로그아웃" :loading="busy" @click="logout" /></div></header>
      <nav class="mobile-secondary-nav" aria-label="추가 메뉴"><RouterLink to="/growth">성장</RouterLink><RouterLink to="/invest">투자</RouterLink><RouterLink to="/settings">설정</RouterLink></nav>
      <div class="workspace-body"><div v-if="store.session.user.guest" class="daily-note"><div><strong>게스트 체험 중</strong><p>기록은 24시간 동안만 열람할 수 있으며 일반 계정으로 옮겨지지 않습니다.</p></div></div><div v-if="error" role="alert" class="error-message">{{ error }}</div>
        <PlanningPage v-if="['/today', '/tasks', '/calendar'].includes(route.path)" :key="route.path" />
        <AdminPage v-else-if="route.path === '/admin'" />
        <GrowthPage v-else-if="route.path === '/growth'" />
        <MoneyPage v-else-if="route.path === '/money'" />
        <InvestPage v-else-if="route.path === '/invest'" />
        <template v-else-if="route.path === '/settings'"><div class="page-heading"><div><div class="eyebrow">MY PREFERENCES</div><h1>나에게 맞는 설정</h1><p>{{ store.session.user.guest ? '게스트 설정은 24시간 체험에만 적용됩니다.' : '기본 설정은 다른 기기에서도 함께 적용됩니다.' }}</p></div></div><section class="panel settings-panel"><h2>프로필과 지역</h2><p>{{ store.session.user.guest ? '임시 게스트 계정' : store.session.user.email }}</p><form @submit.prevent="save"><q-input v-model="displayName" outlined label="표시 이름" required :maxlength="60" class="q-mb-md"/><q-select v-model="timezone" outlined label="시간대" :options="['Asia/Seoul', 'Asia/Tokyo', 'America/New_York', 'America/Los_Angeles', 'Europe/London', 'UTC']" class="q-mb-md"/><q-select v-model="baseCurrency" outlined label="기본 통화" :options="['KRW', 'USD', 'JPY', 'EUR', 'GBP']" class="q-mb-lg"/><p v-if="notice" role="status" class="success-message">{{ notice }}</p><q-btn type="submit" label="설정 저장" color="primary" unelevated :loading="busy"/></form></section></template>
        <template v-else><div class="page-heading"><div><div class="eyebrow">MY DAILY SPACE</div><h1>{{ current }}</h1></div></div><section class="panel empty-state"><span class="empty-icon" aria-hidden="true">✧</span><h2>{{ current }} 기능을 준비하고 있어요</h2><p>문서의 개발 순서에 따라 제공할 예정입니다.<br>오늘, 일정, 할 일은 지금 사용할 수 있어요.</p><q-btn to="/today" color="primary" outline label="오늘로 돌아가기" /></section></template>
        <footer class="workspace-footer">완벽하지 않아도 괜찮아요. 오늘도 나의 속도로.</footer>
      </div>
    </main><nav class="mobile-nav" aria-label="모바일 메뉴"><RouterLink v-for="item in nav.filter(n => ['/today', '/calendar', '/tasks', '/money'].includes(n.path))" :key="item.path" :to="item.path" :class="{ active: route.path === item.path }"><span aria-hidden="true">{{ item.icon }}</span>{{ item.label }}</RouterLink><RouterLink to="/settings"><span aria-hidden="true">⚙</span>설정</RouterLink></nav>
  </div>
</template>
