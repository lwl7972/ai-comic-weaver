<template>
  <router-view />
</template>

<script setup lang="ts">
// Root component - router-view handles all layout routing
</script>

<style>
/* Global styles */
html, body, #app {
  margin: 0;
  padding: 0;
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
    'Helvetica Neue', Arial, 'Noto Sans SC', sans-serif;
}

/* Element Plus overrides for AI comic theme */
:root {
  --el-color-primary: #6366f1;
  --el-color-primary-light-3: #8b5cf6;
  --el-color-primary-dark-2: #4f46e5;
}
</style>
