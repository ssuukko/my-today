<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api/client'
import { useSession } from '../stores/session'

interface Member {
  id: string
  email: string
  displayName: string
  role: 'ADMIN' | 'USER'
  onboardingCompleted: boolean
  createdAt: string
}
interface Members { items: Member[]; nextCursor: string | null; total: number }
interface Invitation { enabled: boolean; inviteToken: string | null }

const store = useSession(), router = useRouter()
const members = ref<Member[]>([]), total = ref(0), cursor = ref<string | null>(null)
const invitation = ref<Invitation | null>(null), revealed = ref(false)
const loading = ref(false), error = ref(''), notice = ref('')

function clear() {
  members.value = []; total.value = 0; cursor.value = null
  invitation.value = null; revealed.value = false; notice.value = ''
}
async function load(append = false) {
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const query = new URLSearchParams({ size: '100' })
    if (append && cursor.value) query.set('cursor', cursor.value)
    const [result, invite] = await Promise.all([
      api<Members>(`/admin/users?${query}`), api<Invitation>('/admin/invitation'),
    ])
    members.value = append ? [...members.value, ...result.items] : result.items
    total.value = result.total; cursor.value = result.nextCursor; invitation.value = invite
  } catch (err) {
    clear()
    if (err instanceof ApiError && err.status === 401) {
      store.session = null; store.checked = true
      await router.replace({ path: '/login', query: { next: '/admin' } })
    } else if (err instanceof ApiError && err.status === 403) {
      try { await store.refresh(); await router.replace('/today') } catch { /* App shows the session connection error. */ }
    } else error.value = err instanceof Error ? err.message : '관리자 정보를 불러오지 못했습니다.'
  } finally { loading.value = false }
}
async function copyInvite() {
  notice.value = ''; error.value = ''
  try {
    if (!invitation.value?.inviteToken || !navigator.clipboard?.writeText) throw new Error('자동 복사를 사용할 수 없습니다. 초대 코드를 표시한 뒤 직접 복사해 주세요.')
    await navigator.clipboard.writeText(invitation.value.inviteToken)
    notice.value = '초대 코드를 복사했습니다.'
  } catch (err) { error.value = err instanceof Error ? err.message : '복사하지 못했습니다. 초대 코드를 표시한 뒤 직접 복사해 주세요.' }
}
onMounted(() => { void load() })
</script>

<template>
  <div class="admin-page">
    <div class="page-heading"><div><div class="eyebrow">ADMINISTRATION</div><h1>회원과 초대 관리</h1><p>회원 기본 정보와 가입 초대를 확인합니다. 개인 기록은 회원 본인의 공간에 보관됩니다.</p></div></div>
    <div v-if="error" class="error-message" role="alert">{{ error }} <q-btn flat label="다시 시도" :loading="loading" @click="load()" /></div>
    <p v-if="notice" class="success-message" role="status">{{ notice }}</p>
    <div v-if="loading" class="planning-loading" role="status"><q-spinner color="primary" size="24px" /> 관리자 정보를 불러오는 중입니다.</div>
    <template v-else-if="invitation">
      <section class="panel admin-invitation"><h2>가입 초대</h2><p>초대받은 회원은 일반 사용자로 가입합니다.</p><template v-if="invitation.enabled && invitation.inviteToken"><q-input :model-value="revealed ? invitation.inviteToken : '••••••••••••••••'" :type="revealed ? 'text' : 'password'" outlined readonly label="가입 초대 코드" autocomplete="off" /><div class="planning-actions q-mt-md"><q-btn flat :label="revealed ? '초대 코드 숨기기' : '초대 코드 표시'" @click="revealed = !revealed" /><q-btn outline color="primary" label="초대 코드 복사" @click="copyInvite" /></div></template><p v-else>현재 가입 초대가 비활성화되어 있습니다.</p></section>
      <section class="panel admin-members"><div class="panel-heading"><h2>회원 목록</h2><span class="tag">전체 {{ total }}명</span></div><p v-if="!members.length" class="empty-state">등록된 회원이 없습니다.</p><ul v-else class="admin-member-list"><li v-for="member in members" :key="member.id"><div class="admin-member-details"><strong>{{ member.displayName }}</strong><span>{{ member.email }}</span><small>{{ member.onboardingCompleted ? '초기 설정 완료' : '초기 설정 전' }} · {{ new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeZone: store.session?.user.timezone || 'Asia/Seoul' }).format(new Date(member.createdAt)) }} 가입</small></div><span class="tag">{{ member.role === 'ADMIN' ? '관리자' : '일반 사용자' }}</span></li></ul><q-btn v-if="cursor" outline color="primary" label="회원 더 불러오기" :loading="loading" @click="load(true)" /></section>
    </template>
  </div>
</template>
