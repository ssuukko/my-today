import { createRouter, createWebHistory } from 'vue-router'
import { useSession } from './stores/session'
import PublicPage from './pages/PublicPage.vue'
import HomePage from './pages/HomePage.vue'
import { safeNext } from './navigation'
export const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', redirect: '/welcome' },
  ...['welcome', 'login', 'signup', 'privacy'].map(name => ({ path: `/${name}`, name, component: PublicPage, meta: { public: true } })),
  { path: '/onboarding', component: PublicPage, meta: { onboarding: true } },
  { path: '/admin', name: 'admin', component: HomePage, meta: { protected: true, admin: true } },
  ...['today', 'calendar', 'tasks', 'growth', 'money', 'invest', 'settings'].map(name => ({ path: `/${name}`, name, component: HomePage, meta: { protected: true } })),
  { path: '/:pathMatch(.*)*', redirect: '/welcome' },
] })
router.beforeEach(async to => {
  const store = useSession()
  try { if (!store.checked || !to.meta.public) await store.refresh() } catch { return true }
  if (!store.session) return to.meta.public ? true : { path: '/login', query: { next: to.fullPath } }
  const role = store.session.user.role
  if (to.meta.admin && role !== 'ADMIN') return '/today'
  if (!store.session.onboardingCompleted) return to.path === '/onboarding' || to.path === '/privacy' ? true : { path: '/onboarding', query: { next: safeNext(to.meta.public ? to.query.next : to.fullPath, role) } }
  if (to.meta.onboarding || ['/welcome', '/login', '/signup'].includes(to.path)) return safeNext(to.query.next, role)
  return true
})
