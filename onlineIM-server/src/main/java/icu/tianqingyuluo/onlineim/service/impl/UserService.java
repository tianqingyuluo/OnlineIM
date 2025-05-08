package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.pojo.dto.request.UserUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.User;
import java.util.List;

public interface UserService {
    boolean existsByUsername(String username);
    void registerUser(User user);
    UserResponse getUserInfoByUsername(String username);
    UserResponse getUserInfoByUserID(String username);
    UserUpdateRequest updateByUsername(String username);
    String getPasswordByUsername(String username);
    void updatePasswordByUsername(String username, String password);
    List<UserBriefResponse> searchByUserID(String keyword,int LIMIT,int offset);
    List<UserBriefResponse> searchByUsername(String keyword,int LIMIT,int offset);
}
