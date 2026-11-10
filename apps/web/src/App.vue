<script setup lang="ts">
import { ref } from 'vue'
import { useSession } from './stores/session'
const store = useSession()
const retrying = ref(false)
async function retry() {
  retrying.value = true
  try { await store.refresh(); window.location.reload() } catch { /* retry screen stays visible */ }
  finally { retrying.value = false }
}
</script>
<template>
  <div v-if="store.unavailable" class="connection-screen" role="alert">
    <div class="brand">MY<span>TODAY</span></div><h1>연결을 확인해 주세요</h1>
    <p>서버에 연결할 수 없어 로그인 상태를 확인하지 못했습니다.</p>
    <q-btn color="primary" unelevated label="다시 시도" :loading="retrying" @click="retry" />
  </div>
  <router-view v-else />
</template>
