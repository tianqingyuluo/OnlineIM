<script setup lang="ts">
import { SidebarProvider } from "@/components/ui/sidebar";
import AppSidebarLeft from "@/components/AppSidebarLeft.vue";
import AppSidebarRightUser from "@/components/AppSidebarRightUser.vue";
import AppSidebarRightGroup from "@/components/AppSidebarRightGroup.vue";
import UserMainPart from "@/components/UserMainPart.vue";
import GroupMainPart from "@/components/GroupMainPart.vue";
import { ref } from "vue";

const selectedUser = ref(null)
const selectedGroup = ref(null)
const currentView = ref('user') // 'user' or 'group'

function handleUserSelected(user) {
  selectedUser.value = user
  currentView.value = 'user'
}

function handleGroupSelected(group) {
  selectedGroup.value = group
  currentView.value = 'group'
}

</script>

<template>
  <div class="flex h-screen">
    <SidebarProvider style="min-width: 15% ;">
      <AppSidebarLeft />
      <AppSidebarRightUser
          v-if="currentView === 'user'"
          @userSelected="handleUserSelected"
          :defaultSelected="true"
          class="ml-[var(--sidebar-width)]"
      />
      <AppSidebarRightGroup
          v-else-if="currentView === 'group'"
          @groupSelected="handleGroupSelected"
          class="ml-[var(--sidebar-width)]"
      />
    </SidebarProvider>
    <div class="flex-1">
      <UserMainPart v-if="currentView === 'user'" :selectedUser="selectedUser" />
      <GroupMainPart v-else-if="currentView==='group'" :selectedGroup="selectedGroup" />
    </div>
  </div>
</template>