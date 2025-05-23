<template>
  <div class="w-full h-full">
    <h3 class="text-sm font-medium text-gray-700 mb-3">选择好友</h3>
    <div class=" overflow-y-auto space-y-2">
      <template v-if="userGroups.length === 0">
        <div class="flex justify-center items-center py-8 text-gray-500">
          没有好友
        </div>
      </template>
      <template v-else v-for="group in userGroups" :key="group.group_id">
        <div class="group-container">
          <SidebarGroupLabel
              @click="toggleGroup(group.group_id)"
              class="cursor-pointer flex items-center justify-between px-4 py-3 hover:bg-gray-50 transition-colors font-bold"
              style="font-size: 15px"
          >
            {{ group.name }} ({{ group.friends.length }})
            <ChevronDown
                v-if="isGroupExpanded(group.group_id)"
                class="w-5 h-5 transition-transform duration-200"
            />
            <ChevronRight
                v-else
                class="w-5 h-5 transition-transform duration-200"
            />
          </SidebarGroupLabel>

          <transition
              name="slide"
              @enter="el => el.style.height = el.scrollHeight + 'px'"
              @after-enter="el => el.style.height = null"
              @before-leave="el => el.style.height = el.scrollHeight + 'px'"
              @leave="el => el.style.height = 0"
          >
            <div v-show="isGroupExpanded(group.group_id)" class="transition-all duration-300">
              <div
                  v-for="friend in group.friends"
                  :key="friend.user_id"
                  class="flex items-center p-2 hover:bg-gray-50 rounded cursor-pointer"
                  @click="toggleFriendSelection(friend.user_id)"
              >
                <img
                    :src="friend.avatar_url || '/images/default-avatar.png'"
                    class="w-10 h-10 rounded-full mr-2"
                    :alt="friend.nickname"
                />
                <span class="text-sm text-gray-800 mr-2">
                  {{ friend.nickname || friend.username }}
                </span>
                <input
                    type="checkbox"
                    v-model="selectedMembers"
                    :value="friend.user_id"
                    class="h-4 w-4 rounded border-gray-300 text-gray-600 focus:ring-gray-500"
                />
              </div>
            </div>
          </transition>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import { ChevronDown, ChevronRight } from 'lucide-vue-next'
import { SidebarGroupLabel } from '@/components/ui/sidebar'
import { useListStore } from '@/stores/list'

const listStore = useListStore()
const expandedGroups = ref<Record<string, boolean>>({})
const props = defineProps<{
  modelValue: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void
}>()


const selectedMembers = computed({
  get: () => props.modelValue  || [],
  set: (value) => {
    emit('update:modelValue', value)
  }
})


// 初始化所有分组为展开状态
listStore.userGroups.forEach(group => {
  expandedGroups.value[group.group_id] = true
})

function toggleGroup(groupId: string) {
  expandedGroups.value[groupId] = !expandedGroups.value[groupId]
}

function isGroupExpanded(groupId: string) {
  return expandedGroups.value[groupId] ?? true
}

const userGroups = computed(() => {
  return listStore.userGroups
})

function toggleFriendSelection(userId: string) {
  const currentMembers = selectedMembers.value;
  const index = currentMembers.indexOf(userId);
  if (index === -1) {
    // Add member: create a new array with the new member
    selectedMembers.value = [...currentMembers, userId];
  } else {
    // Remove member: create a new array excluding the member
    selectedMembers.value = currentMembers.filter(id => id !== userId);
  }
}
</script>

<style scoped>
.slide-enter-active,
.slide-leave-active {
  transition: height 0.3s ease-in-out;
  overflow: hidden;
}

.group-container {
  transition: all 0.3s ease;
}

.chevron-rotate-enter-active,
.chevron-rotate-leave-active {
  transition: transform 0.3s ease;
}

.chevron-rotate-enter-from,
.chevron-rotate-leave-to {
  transform: rotate(-90deg);
}
</style>