export interface MessageResponse{
    "message_id": "string",
    "sender_id": "string",
    "receiver_id": "string",
    "content_type": "integer",
    "content": "string",
    "created_at": "string ",//(date-time)
    "status": "integer"//(e.g., 0 for sent, 1 for delivered, 2 for read)
}
export interface MessageWithTotal {
    messages: MessageResponse[];
    total: number;
}