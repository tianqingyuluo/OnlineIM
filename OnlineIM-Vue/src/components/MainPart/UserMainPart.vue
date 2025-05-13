<script setup lang="ts">
import { Ellipsis } from "lucide-vue-next"
import { ref, provide, computed, onMounted } from "vue"
import { useRoute } from 'vue-router'
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import Tools from "@/components/MainPart/tools.vue";
import UserTextArea from "@/components/MainPart/UserTextArea.vue";
import { userService } from '@/services/user.service'
import type { User } from '@/type/User'

const route = useRoute()
const currentUser = ref<User | null>(null)

const userId = computed(() => {
  const id = route.params.id
  return Array.isArray(id) ? id[0] : id
})

onMounted(async () => {
  try {
    currentUser.value = await userService.getUserById(userId.value)
  } catch (error) {
    console.error('获取用户信息失败:', error)
  }
})

const items = [
  {
    title: "更多",
    url: "#",
    icon: Ellipsis,
  }
]

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

// 在onBeforeMount中创建ref和provide


const messageInputRef = ref<HTMLTextAreaElement | null>(null)
provide('messageInputRef', messageInputRef)
console.log("currentUser 数据:", currentUser.value);



// 添加处理表情选择的函数
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
</script>

<template>
  <div v-if="currentUser" class="flex flex-col h-full">
    <!-- 顶栏 -->
    <div class="flex items-center justify-between w-full p-4 border-b">
      <span class="text-lg font-semibold">{{ currentUser.username }}</span>
      <div class="flex space-x-4">
        <button v-for="item in items" :key="item.title">
          <a :href="item.url" class="flex items-center">
            <component :is="item.icon" class="w-5 h-5" />
          </a>
        </button>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="flex-1 overflow-y-auto p-4">
      <!-- 这里可以取消注释并修改为使用currentUser.TalkingList -->

<!--      <div v-if="currentUser.TalkingList" class="space-y-4">-->
<!--        <template v-for="(msg, index) in currentUser.TalkingList" :key="msg.messageId">-->
<!--          &lt;!&ndash; 时间显示 &ndash;&gt;-->
<!--          <div v-if="shouldShowTime(index, currentUser.TalkingList)" class="flex justify-center">-->
<!--            <span class="text-[13px] text-gray-500 truncate">-->
<!--              {{ new Date(msg.sendTime).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') }}-->
<!--            </span>-->
<!--          </div>-->

<!--          &lt;!&ndash; 消息内容 &ndash;&gt;-->
<!--          <div :class="['flex', msg.sender === theUser.userName ? 'justify-end' : 'justify-start']">-->
<!--            &lt;!&ndash; 对方消息 &ndash;&gt;-->
<!--            <template v-if="msg.sender !== theUser.userName">-->
<!--              <div class="flex items-start max-w-[80%]">-->
<!--                <img :src="currentUser.avatar" :alt="currentUser.userName" class="w-10 h-10 rounded-full mr-2" />-->
<!--                <UserTextArea -->
<!--                  :message="msg.message"-->
<!--                  :isSelf="false"-->
<!--                />-->
<!--              </div>-->
<!--            </template>-->
<!--            -->
<!--            &lt;!&ndash; 自己的消息 &ndash;&gt;-->
<!--            <template v-else>-->
<!--              <div class="flex items-start">-->
<!--                <UserTextArea -->
<!--                  :message="msg.message"-->
<!--                  :isSelf="true"-->
<!--                />-->
<!--                <img :src="theUser.arg" :alt="theUser.userName" class="w-10 h-10 rounded-full ml-2" />-->
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
/* 移除气泡样式，因为已经移到 UserTextArea 组件中 */
</style>