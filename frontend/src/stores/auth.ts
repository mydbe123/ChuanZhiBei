import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api, getToken, setToken } from '@/api/client'
import type { User } from '@/api/types'

interface AuthPayload {
  token: string
  user: User
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const user = ref<User | null>(null)
  const loading = ref(false)

  const isLoggedIn = computed(() => Boolean(token.value))
  const initial = computed(() => {
    const name = user.value?.displayName || user.value?.username || '学'
    return name.slice(0, 1).toUpperCase()
  })

  function apply(payload: AuthPayload) {
    token.value = payload.token
    setToken(payload.token)
    user.value = payload.user
  }

  async function login(username: string, password: string) {
    loading.value = true
    try {
      apply(await api.post<AuthPayload>('/auth/login', { username, password }))
    } finally {
      loading.value = false
    }
  }

  async function register(payload: Record<string, unknown>) {
    loading.value = true
    try {
      apply(await api.post<AuthPayload>('/auth/register', payload))
    } finally {
      loading.value = false
    }
  }

  async function demoLogin() {
    loading.value = true
    try {
      apply(await api.post<AuthPayload>('/auth/demo'))
    } finally {
      loading.value = false
    }
  }

  async function loadMe() {
    if (!token.value) return
    try {
      user.value = await api.get<User>('/auth/me')
    } catch {
      logout()
    }
  }

  async function updateProfile(patch: Record<string, unknown>) {
    user.value = await api.put<User>('/auth/me', patch)
  }

  function logout() {
    token.value = null
    user.value = null
    setToken(null)
  }

  return { token, user, loading, isLoggedIn, initial, login, register, demoLogin, loadMe, updateProfile, logout }
})
