import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { Quasar } from 'quasar'
import langKo from 'quasar/lang/ko-KR'
import materialIcons from 'quasar/icon-set/material-icons'
import 'quasar/src/css/index.sass'
import './styles/app.scss'
import App from './App.vue'
import { router } from './router'
createApp(App).use(createPinia()).use(Quasar, {
  lang: langKo,
  iconSet: { ...materialIcons, arrow: { ...materialIcons.arrow, dropdown: 'M7 10l5 5 5-5z' }, field: { clear: 'M6 6l12 12M6 18L18 6|fill:none;stroke:currentColor;stroke-width:2', error: 'M11 7h2v6h-2zm0 8h2v2h-2zM12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20m0 2a8 8 0 1 1 0 16 8 8 0 0 1 0-16' } },
}).use(router).mount('#app')
