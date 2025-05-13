<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import UserMainPart from '@/components/MainPart/UserMainPart.vue'
import GroupMainPart from '@/components/MainPart/GroupMainPart.vue'

const route = useRoute()
const componentType = computed(() => route.params.type)
</script>

<template>
  <UserMainPart v-if="componentType === 'private'" />
  <GroupMainPart v-else-if="componentType === 'group'" />
</template>