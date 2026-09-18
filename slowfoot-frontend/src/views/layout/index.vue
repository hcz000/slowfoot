<template>
  <el-container class="app-shell">
    <default-header :click-event="clickEvent" />
    <el-container class="app-body">
      <navigation :open="leftMenu" />
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import defaultHeader from './header.vue'
import navigation from './navigation.vue'

const leftMenu = ref(true)
const clickEvent = (type) => {
  if (type === 1) leftMenu.value = !leftMenu.value
}
</script>

<style scoped>
.app-shell { display: flex; min-height: 100vh; flex-direction: column; background: #101318; }
.app-body { display: flex; flex: 1 1 auto; width: 100%; min-height: calc(100vh - 72px); flex-direction: row; }
.app-main { flex: 1 1 auto; min-width: 0; min-height: calc(100vh - 72px); padding: 0; background: #101318; }
</style>
