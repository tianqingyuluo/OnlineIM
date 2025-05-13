<script setup lang="ts">
import { Ellipsis } from "lucide-vue-next"
import { ref, provide, onMounted, computed } from "vue"
import { useRoute } from 'vue-router'
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import Tools from "@/components/MainPart/tools.vue";
import UserTextArea from "@/components/MainPart/UserTextArea.vue";
import { groupService } from '@/services/group.service'
import type { GroupResponse } from '@/type/group'
import { onActivated } from 'vue'
import GroupInfoCard from '@/components/independent/group/GroupInfoCard.vue'

const route = useRoute()
const currentGroup = ref<GroupResponse | null>(null)

const groupId = computed(() => {
  const id = route.params.id
  return Array.isArray(id) ? id[0] : id
})

onMounted(async () => {
  try {
    console.log("当前群组ID:", groupId.value) // 添加这行检查路由参数
    const groupInfo = await groupService.getGroupInfo(groupId.value)
    console.log("API返回数据:", groupInfo) // 检查API返回
    currentGroup.value = groupInfo
    console.log("设置后的群组信息:", currentGroup.value) // 检查响应式数据
  } catch (error) {
    console.error('获取群组信息失败:', error)
  }
})


// 解析中文日期格式
function parseChineseDate(dateStr: string) {
  if (!dateStr) return new Date();

  // 处理 "2001年1月1，0.00" 这种格式
  const match = dateStr.match(/(\d+)年(\d+)月(\d+)[，,](\d+)\.(\d+)/);
  if (match) {
    const [_, year, month, day, hour, minute] = match;
    return new Date(
        parseInt(year),
        parseInt(month) - 1,
        parseInt(day),
        parseInt(hour),
        parseInt(minute)
    );
  }

  // 尝试解析ISO格式或其它格式
  const date = new Date(dateStr);
  return isNaN(date.getTime()) ? new Date() : date;
}

// 判断是否需要显示时间（5分钟间隔）
function shouldShowTime(index: number, list: any[]) {
  if (index === 0) return true;
  const prevTime = parseChineseDate(list[index - 1].sendTime).getTime();
  const currentTime = parseChineseDate(list[index].sendTime).getTime();
  return currentTime - prevTime > 5 * 60 * 1000;
}

const messageInputRef = ref<HTMLTextAreaElement | null>(null)
provide('messageInputRef', messageInputRef)

function handleEmojiSelect(emoji: string) {
  const textareaEl = document.getElementById('message-2') as HTMLTextAreaElement
  if (textareaEl) {
    // 获取当前光标位置
    const cursorPos = textareaEl.selectionStart || 0
    const currentValue = textareaEl.value || ''
    
    // 在光标位置插入表情
    const newValue =
      currentValue.substring(0, cursorPos) +
      emoji +
      currentValue.substring(cursorPos)

    // 更新文本框值
    textareaEl.value = newValue
    
    // 设置新的光标位置
    const newCursorPos = cursorPos + emoji.length
    textareaEl.selectionStart = newCursorPos
    textareaEl.selectionEnd = newCursorPos
    
    // 保持焦点
    textareaEl.focus()
  }
}

function handleSendClick() {
  const textareaEl = document.getElementById('message-2') as HTMLTextAreaElement
  if (textareaEl) {
    console.log('当前输入内容:', textareaEl.value)
  }
}

onActivated(async () => {
  console.log('组件被激活')
  // 可以在这里添加数据刷新逻辑
})
const showMenu = ref(false)

function toggleMenu() {
  showMenu.value = !showMenu.value
}
</script>

<template>
  <div v-if="currentGroup" class="flex flex-col h-full">
    <!-- 顶栏 -->
    <div class="flex items-center justify-between w-full p-4 border-b relative">
      <span class="text-lg font-semibold">{{ currentGroup.name }}</span>
      <div class="flex space-x-4">
        <button @click="toggleMenu">
          <a href="#" class="flex items-center">
            <Ellipsis class="w-5 h-5" />
          </a>
        </button>
      </div>
      
      <!-- 滑动菜单 -->
      <Transition name="slide">
        <div v-if="showMenu" class="absolute right-0 top-full w-80 bg-white shadow-lg z-50 ">
          <GroupInfoCard :group="currentGroup" />
        </div>
      </Transition>
    </div>

    <!-- 主内容区 -->
    <div class="flex-1 overflow-y-auto p-4">
<!--      <div v-if="currentGroup.MessageList" class="space-y-4">-->
<!--        <template v-for="(msg, index) in currentGroup.MessageList" :key="msg.messageId">-->
<!--          &lt;!&ndash; 时间显示 &ndash;&gt;-->
<!--          <div v-if="shouldShowTime(index, currentGroup.MessageList)" class="flex justify-center">-->
<!--            <span class="text-[13px] text-gray-500 truncate">-->
<!--              {{ new Date(msg.sendTime).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') }}-->
<!--            </span>-->
<!--          </div>-->

<!--          &lt;!&ndash; 消息内容 &ndash;&gt;-->
<!--          <div :class="['flex', msg.sender === theUser.userName ? 'justify-end' : 'justify-start']">-->
<!--            &lt;!&ndash; 对方消息 &ndash;&gt;-->
<!--            <template v-if="msg.sender !== theUser.userName">-->
<!--              <div class="flex items-start max-w-[80%]">-->
<!--                <img :src="currentGroup.groupAvatar" :alt="msg.sender" class="w-10 h-10 rounded-full mr-2" />-->
<!--                <UserTextArea-->
<!--                  :message="msg.message"-->
<!--                  :isSelf="false"-->
<!--                />-->
<!--              </div>-->
<!--            </template>-->

<!--            &lt;!&ndash; 自己的消息 &ndash;&gt;-->
<!--            <template v-else>-->
<!--              <div class="flex items-start">-->
<!--                <UserTextArea-->
<!--                  :message="msg.message"-->
<!--                  :isSelf="true"-->
<!--                />-->
<!--                <img :src="theUser.avatar" :alt="theUser.userName" class="w-10 h-10 rounded-full ml-2" />-->
<!--              </div>-->
<!--            </template>-->
<!--          </div>-->
<!--        </template>-->
<!--      </div>-->
    </div>
    <Tools @select="handleEmojiSelect" />
    <!-- 输入区域 -->
    <div class="relative h-1/4 border-t">
      <Textarea
          id="message-2"
          ref="messageInputRef"
          class="h-full w-full resize-none pr-20 rounded-none focus:ring-0 focus:shadow-none"
          style="outline: none;box-shadow: none; font-size: 24px"
      />
      <Button 
        class="absolute bottom-4 right-4 transition-all duration-200 active:scale-95 hover:bg-primary/90 hover:scale-125"
        @click="handleSendClick"
      >
        发送
      </Button>
    </div>
  </div>
  <div v-else class="flex items-center justify-center h-full">
    加载中...
  </div>
</template>

<style scoped>
/* 滑动动画 */
.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}
</style>