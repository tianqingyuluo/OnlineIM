package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.ReadReceiptRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.ReceiptRequest;

public interface ReceiptService {

    boolean handleDelivered(String receiverId, ReceiptRequest request);

    boolean handleRead(String readerId, ReadReceiptRequest request);
}
