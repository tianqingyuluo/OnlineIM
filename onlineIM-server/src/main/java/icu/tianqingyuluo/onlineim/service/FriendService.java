package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface FriendService {
    List<FriendResponse> fetchFriendsByUsername(String username);
    FriendResponse getByID(String id);
    boolean existFriendByID(String id);
    void updateRemarkByID(String id);
    void updateGroupByID(String id);
}
