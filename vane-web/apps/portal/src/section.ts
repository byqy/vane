import { reactive } from 'vue'
import { i18n } from './i18n'
import { problemText } from './problem'

/**
 * 区块级三态：每个区块独立维护 加载 / 错误（可重试）/ 数据，
 * 避免一个接口失败把整页拖成错误页（08 §8、§18）。
 */
export interface AsyncSection<T> {
  data: T
  loading: boolean
  error: string | null
  load: (fetcher: () => Promise<T>) => Promise<void>
  reset: () => void
}

export function useAsyncSection<T>(initial: T): AsyncSection<T> {
  const state = reactive({
    data: initial,
    loading: false,
    error: null as string | null
  }) as unknown as AsyncSection<T>

  state.load = async (fetcher: () => Promise<T>): Promise<void> => {
    state.loading = true
    state.error = null
    try {
      state.data = await fetcher()
    } catch (e) {
      state.error = problemText(e, i18n.global.t('portal.common.error'))
    } finally {
      state.loading = false
    }
  }

  state.reset = (): void => {
    state.data = initial
    state.error = null
    state.loading = false
  }

  return state
}
