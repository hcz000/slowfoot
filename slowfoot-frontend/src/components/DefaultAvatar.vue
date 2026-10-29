<template>
  <span class="default-avatar" :style="{ '--avatar-size': `${size}px` }" aria-hidden="true">
    <svg viewBox="0 0 64 64" role="img">
      <circle cx="32" cy="32" r="30" fill="#1677f2" />
      <circle cx="32" cy="25" r="9" fill="#fff" />
      <path d="M16 51c1-10 7-16 16-16s15 6 16 16Z" fill="#fff" />
    </svg>
  </span>
</template>

<script setup>
defineProps({ size: { type: Number, default: 42 } })
</script>

<style scoped>
.default-avatar { display: inline-grid; flex: 0 0 var(--avatar-size); place-items: center; width: var(--avatar-size); height: var(--avatar-size); overflow: hidden; border-radius: 50%; }
.default-avatar svg { display: block; width: 100%; height: 100%; }
</style>
