import { ref, watch } from 'vue'

/**
 * 通用数据加载 composable：统一管理 loading / error / data 状态。
 * 适用于非分页的简单列表加载场景。
 *
 * @param {Function} fetchFn - 异步数据获取函数，返回数据数组
 * @param {Object} options
 * @param {boolean} [options.immediate=true] - 是否在创建时立即加载
 *
 * @returns {{ loading, error, data, refresh, reset }}
 *
 * @example
 * const { loading, error, data, refresh } = useDataLoader(() => fetchInventoryOverview({}))
 */
export function useDataLoader(fetchFn, { immediate = true } = {}) {
  const loading = ref(false)
  const error = ref(null)
  const data = ref([])

  let loadSeq = 0

  async function refresh() {
    loading.value = true
    error.value = null
    const seq = ++loadSeq

    try {
      const result = await fetchFn()
      if (seq !== loadSeq) return
      data.value = Array.isArray(result) ? result : []
    } catch (err) {
      if (seq !== loadSeq) return
      error.value = err instanceof Error ? err.message : String(err)
      data.value = []
    } finally {
      if (seq === loadSeq) loading.value = false
    }
  }

  function reset() {
    data.value = []
    error.value = null
  }

  if (immediate) refresh()

  return { loading, error, data, refresh, reset }
}

/**
 * 带参数的数据加载器：自动监听参数变化并重新加载。
 */
export function useDataLoaderWithParams(fetchFn, paramsRef, { immediate = true } = {}) {
  const { loading, error, data, refresh, reset } = useDataLoader(
    () => fetchFn(paramsRef.value),
    { immediate }
  )

  if (paramsRef && watch) {
    watch(paramsRef, () => refresh(), { deep: true })
  }

  return { loading, error, data, refresh, reset }
}
