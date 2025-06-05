<template>
  <router-view />
  <Toaster 
    position="top-center"
    :theme="'light'"
    richColors
    closeButton
  />
</template>
<script setup lang="ts">
import {Toaster} from "vue-sonner";
</script>