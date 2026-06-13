<template>
  <img
    ref="imgRef"
    :data-src="src"
    :src="loading || placeholder"
    :alt="alt"
    :style="style"
    :class="['lazy-image', { 'loaded': isLoaded, 'error': isError }]"
    @load="handleLoad"
    @error="handleError"
  />
  <div v-if="showLoading && isLoading" class="lazy-image-loading">加载中...</div>
  <div v-if="showError && isError" class="lazy-image-error">加载失败</div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'

interface Props {
  src: string
  alt?: string
  placeholder?: string
  loading?: string
  showLoading?: boolean
  showError?: boolean
  width?: string | number
  height?: string | number
}

const props = withDefaults(defineProps<Props>(), {
  alt: '',
  placeholder: '/placeholder.png',
  loading: '/loading.gif',
  showLoading: true,
  showError: true,
})

const imgRef = ref<HTMLImageElement | null>(null)
const isLoading = ref(true)
const isLoaded = ref(false)
const isError = ref(false)

const observer = ref<IntersectionObserver | null>(null)

const style = computed(() => ({
  width: props.width ? (typeof props.width === 'number' ? `${props.width}px` : props.width) : '100%',
  height: props.height ? (typeof props.height === 'number' ? `${props.height}px` : props.height) : 'auto',
}))

function handleLoad() {
  isLoading.value = false
  isLoaded.value = true
  isError.value = false
}

function handleError() {
  isLoading.value = false
  isLoaded.value = false
  isError.value = true
}

onMounted(() => {
  observer.value = new IntersectionObserver((entries) => {
    const entry = entries[0]
    if (entry.isIntersecting && imgRef.value) {
      const img = imgRef.value
      const dataSrc = img.getAttribute('data-src')
      if (dataSrc) {
        img.src = dataSrc
        observer.value?.disconnect()
      }
    }
  }, {
    rootMargin: '50px',
  })

  if (imgRef.value) {
    observer.value.observe(imgRef.value)
  }
})
</script>

<style scoped>
.lazy-image {
  transition: opacity 0.3s ease;
  object-fit: cover;
}

.lazy-image.loaded {
  opacity: 1;
}

.lazy-image:not(.loaded) {
  opacity: 0.6;
}

.lazy-image-loading,
.lazy-image-error {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  color: #999;
  font-size: 12px;
}

.lazy-image-error {
  background: #fef0f0;
  color: #f56c6c;
}
</style>
