import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'

import App from '@/app/App.vue'
import router from '@/app/router'
import { queryClient } from '@/lib/queryClient'
import { useAuthStore } from '@/stores/auth'

import '@/assets/index.css'

// Fix for iOS Safari: Radix UI uses onPointerDown events which don't fire correctly
// on iOS Safari without a global pointer event listener initialized first.
// See: https://github.com/radix-ui/primitives/issues/2580
if (typeof document !== 'undefined') {
  document.body.addEventListener('pointerdown', () => {}, { passive: true })
}

const app = createApp(App)

const pinia = createPinia()
app.use(pinia)

app.use(router)
app.use(VueQueryPlugin, { queryClient })

const authStore = useAuthStore()
authStore.initialize()

app.mount('#app')
