<template>
  <div class="app-shell" :class="{ 'focused-shell': route.query.play }">
    <default-header v-if="!route.query.play" :click-event="toggleNavigation" />
    <div class="app-body">
      <navigation v-if="!route.query.play" :open="leftMenu" />
      <main class="app-main"><router-view /></main>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import defaultHeader from './header.vue'
import navigation from './navigation.vue'

const leftMenu = ref(true)
const route = useRoute()
const toggleNavigation = () => { leftMenu.value = !leftMenu.value }
</script>

<style scoped>
.app-shell { min-height: 100vh; background: #f7f7f7; }
.app-body { display: flex; min-height: calc(100vh - 64px); }
.app-main { min-width: 0; flex: 1; background: #f7f7f7; }
.focused-shell { height: 100dvh; overflow: hidden; }
.focused-shell .app-body, .focused-shell .app-main { width: 100%; height: 100%; min-height: 100dvh; }
@media (max-width: 760px) { .app-body { min-height: calc(100vh - 60px); } }
</style>
