<script setup lang="ts">
import { Ellipsis } from "lucide-vue-next"
import { ref, computed } from "vue"
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";

const props = defineProps<{
  selectedUser?: {
    userName: string
    arg: string
    lastTalk: string
    TalkingList?: Array<{
      messageId: number
      message: string
      sender: string
      sendTo: string
      sendTime: string
    }>
  }
}>()

const theUser = ref({
  userName: "我",
  arg: "/image/help.png"
})

const defaultUser = ref({
  userName: "默认用户",
  arg: "/image/help.png",
  lastTalk: "这是默认用户的最后一条消息",
  TalkingList: [
    {
      messageId: 1,
      message: "你好，这是一条示例消息",
      sender: "默认用户",
      sendTo: "我",
      sendTime: "2023-01-01 12:00"
    },
    {
      messageId: 2,
      message: "你好，这是我的回复",
      sender: "我",
      sendTo: "默认用户",
      sendTime: "2023-01-02 12:05"
    }
  ]
})

const currentUser = computed(() => props.selectedUser || defaultUser.value)

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
    <!-- 顶栏 -->
    <div class="flex items-center justify-between w-full p-4 border-b">
      <span class="font-medium">{{ currentUser.userName }}</span>
      <div class="flex space-x-4">
        <button v-for="item in items" :key="item.title"  >
          <a :href="item.url" class="flex items-center">
            <component :is="item.icon" class="w-5 h-5" />
          </a>
        </button>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="flex-1 overflow-y-auto p-4">
      <div v-if="currentUser.TalkingList" class="space-y-4">
        <template v-for="(msg, index) in currentUser.TalkingList" :key="msg.messageId">
          <!-- 时间显示 -->
          <div v-if="shouldShowTime(index, currentUser.TalkingList)" class="flex justify-center">
            <span class="text-[13px] text-gray-500 truncate">
              {{ new Date(msg.sendTime).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') }}
            </span>
          </div>

          <!-- 消息内容 -->
          <div :class="['flex', msg.sender === theUser.userName ? 'justify-end' : 'justify-start']">
            <!-- 对方消息 -->
            <div v-if="msg.sender !== theUser.userName" class="flex items-start max-w-[80%]">
              <img :src="currentUser.arg" :alt="currentUser.userName" class="w-10 h-10 rounded-full mr-2" />
              <div class="bg-white rounded-lg p-3 shadow-sm max-w-[calc(100%-3rem)]">
                {{ msg.message }}
              </div>
            </div>

            <!-- 我的消息 -->
            <div v-else class="flex items-start max-w-[80%]">
              <div class="bg-blue-50 rounded-lg p-3 shadow-sm mr-2 max-w-[calc(100%-3rem)]">
                {{ msg.message }}
              </div>
              <img :src="theUser.arg" :alt="theUser.userName" class="w-10 h-10 rounded-full" />
            </div>
          </div>
        </template>
      </div>
    </div>
    <!-- 新增工具栏 -->
    <div class="flex items-center justify-between px-4 py-3.5 border-t border-b bg-gray-50">
      <div class="flex space-x-2">
        <!-- 这里可以添加未来的工具栏按钮 -->
      </div>
    </div>
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

<style scoped>
/* 气泡样式微调 */
.bg-white {
  border-top-left-radius: 0;
}
.bg-blue-50 {
  border-top-right-radius: 0;
}
</style>