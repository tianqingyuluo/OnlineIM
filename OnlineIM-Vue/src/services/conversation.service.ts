import api from './api.service';
import type { FormContext } from 'vee-validate';
import { useListStore } from '@/stores/list';
import {type ConversationsResponse} from "@/type/Conversation.ts";

export const conversationService = {
  async getConversations(
    params?: {
      limit?: number;
      offset?: number;
    },
    formContext?: FormContext
  ): Promise<ConversationsResponse> {
    try {
      const response = await api.get<ConversationsResponse>('/conversations', {
        params
      });
      console.log(response.data);
      const listStore = useListStore();
      listStore.conversations = response.data.conversations;
      listStore.total = response.data.total;
      return response.data;
    } catch (error: any) {
      if (formContext && error.response?.data?.errors) {
        formContext.setErrors(error.response.data.errors);
      }
      throw error;
    }
  }
};