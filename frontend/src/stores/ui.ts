import { defineStore } from 'pinia'
import { ref } from 'vue'

const THEME_KEY = 'zhixue.theme'
const SIDEBAR_KEY = 'zhixue.sidebar'

function initialTheme(): 'light' | 'dark' {
  const saved = localStorage.getItem(THEME_KEY)
  if (saved === 'light' || saved === 'dark') return saved
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export const useUiStore = defineStore('ui', () => {
  const theme = ref<'light' | 'dark'>(initialTheme())
  const sidebarCollapsed = ref(localStorage.getItem(SIDEBAR_KEY) === 'collapsed')

  function applyTheme() {
    document.documentElement.dataset.theme = theme.value
  }

  function toggleTheme() {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    localStorage.setItem(THEME_KEY, theme.value)
    applyTheme()
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem(SIDEBAR_KEY, sidebarCollapsed.value ? 'collapsed' : 'expanded')
  }

  return { theme, sidebarCollapsed, applyTheme, toggleTheme, toggleSidebar }
})
