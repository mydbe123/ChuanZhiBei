import { reactive } from 'vue'

export type ToastType = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  type: ToastType
  message: string
}

export const toasts = reactive<ToastItem[]>([])

let seq = 0

export function pushToast(message: string, type: ToastType = 'info', duration = 3200) {
  const id = ++seq
  toasts.push({ id, type, message })
  window.setTimeout(() => removeToast(id), duration)
  return id
}

export function removeToast(id: number) {
  const index = toasts.findIndex((item) => item.id === id)
  if (index >= 0) toasts.splice(index, 1)
}

export const toast = {
  success: (message: string) => pushToast(message, 'success'),
  error: (message: string) => pushToast(message, 'error', 4200),
  info: (message: string) => pushToast(message, 'info')
}
