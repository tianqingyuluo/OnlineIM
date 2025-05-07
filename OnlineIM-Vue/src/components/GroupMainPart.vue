<script setup lang="ts">
import { Ellipsis } from "lucide-vue-next"
import { ref, computed } from "vue"
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { useUserStore } from '@/stores/user'
import Tools from "@/components/tools.vue";
import UserTextArea from "@/components/UserTextArea.vue";

const theUser = ref({
  userName: "我",
  avatar: "/images/help.png"
})

const userStore = useUserStore()
const currentGroup = computed(() => userStore.selectedUser)

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
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- 顶栏显示群名 -->
    <div class="flex items-center justify-between w-full p-4 border-b">
      <span class="text-lg font-semibold">{{ currentGroup.groupName }}</span>
      <div class="flex space-x-4">
        <button v-for="item in items" :key="item.title">
          <a :href="item.url" class="flex items-center">
            <component :is="item.icon" class="w-5 h-5" />
          </a>
        </button>
      </div>
    </div>

    <!-- 群消息历史区域 -->
    <div class="flex-1 overflow-y-auto p-4">
      <div v-if="currentGroup.MessageList" class="space-y-4">
        <template v-for="(msg, index) in currentGroup.MessageList" :key="msg.messageId">
          <!-- 时间显示 -->
          <div v-if="shouldShowTime(index, currentGroup.MessageList)" class="flex justify-center">
            <span class="text-[13px] text-gray-500 truncate">
              {{ new Date(msg.sendTime).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') }}
            </span>
          </div>

          <!-- 消息内容 -->
          <div :class="['flex', msg.sender === theUser.userName ? 'justify-end' : 'justify-start']">
            <!-- 对方消息 -->
            <template v-if="msg.sender !== theUser.userName" >
              <div class="flex flex-col items-start max-w-[80%]"> <!-- 恢复原始flex-col结构 -->
                <div class="text-xs text-gray-500 mb-1 ml-12"> <!-- 恢复ml-12 -->
                  {{ msg.sender }}
                </div>
                <div class="flex items-start">
                  <img :src="currentGroup.groupAvatar" :alt="msg.sender" class="w-10 h-10 rounded-full mr-2 mt-1" /> <!-- 仅添加mt-1微调头像位置 -->
                  <UserTextArea
                      :message="msg.message"
                      :isSelf="false"
                  />
                </div>
              </div>
            </template>
            
            <!-- 自己的消息 -->
            <template v-else>
              <div class="flex items-start">
                <UserTextArea 
                  :message="msg.message"
                  :isSelf="true"
                />
                <img :src="theUser.avatar" :alt="theUser.userName" class="w-10 h-10 rounded-full ml-2" />
              </div>
            </template>
          </div>
        </template>
      </div>
    </div>
    <Tools/>
    <!-- 输入区域 -->
    <div class="relative h-1/4 border-t">
      <Textarea
          id="message-2"
          placeholder="请在这里输入"
          class="h-full w-full resize-none pr-20 rounded-none focus:ring-0 focus:shadow-none"
          style="outline: none;box-shadow: none;"
      />
      <Button class="absolute bottom-4 right-4">发送</Button>
    </div>
  </div>
</template>