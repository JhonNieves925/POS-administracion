<template>
  <SplashScreen v-if="showSplash" @done="showSplash = false" />
  <RouterView v-else />
  <Toast position="top-right" />
  <ConfirmDialog />
</template>

<script setup>
import { ref } from 'vue'
import { RouterView } from 'vue-router'
import Toast from 'primevue/toast'
import ConfirmDialog from 'primevue/confirmdialog'
import SplashScreen from '@/components/SplashScreen.vue'

const showSplash = ref(true)
</script>
