import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, ApiError, type Session } from '../api/client'
export const useSession = defineStore('session', () => {
  const session = ref<Session | null>(null)
  const checked = ref(false)
  const unavailable = ref(false)
  async function refresh() {
    unavailable.value = false
    try { session.value = await api<Session>('/auth/session'); checked.value = true }
    catch (error) {
      session.value = null
      if (error instanceof ApiError && error.status === 401) checked.value = true
      else { checked.value = false; unavailable.value = true; throw error }
    }
  }
  async function logout() {
    await api('/auth/logout', { method: 'POST' })
    session.value = null; checked.value = true
  }
  return { session, checked, unavailable, refresh, logout }
})
