import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

/**
 * 虚拟列表 Store
 * 用于优化大数据列表渲染性能
 */
export const useVirtualListStore = defineStore('virtualList', () => {
  const itemHeight = ref(100)
  const containerHeight = ref(600)
  const scrollTop = ref(0)

  function updateScrollPosition(position: number) {
    scrollTop.value = position
  }

  function setItemHeight(height: number) {
    itemHeight.value = height
  }

  function setContainerHeight(height: number) {
    containerHeight.value = height
  }

  const visibleRange = computed(() => {
    const startIndex = Math.floor(scrollTop.value / itemHeight.value)
    const visibleCount = Math.ceil(containerHeight.value / itemHeight.value)
    const endIndex = Math.min(startIndex + visibleCount, Number.MAX_SAFE_INTEGER)

    return {
      start: Math.max(0, startIndex - 5),
      end: endIndex + 5,
    }
  })

  return {
    itemHeight,
    containerHeight,
    scrollTop,
    visibleRange,
    updateScrollPosition,
    setItemHeight,
    setContainerHeight,
  }
})
