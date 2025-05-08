package icu.tianqingyuluo.onlineim.service.impl;
import icu.tianqingyuluo.onlineim.mapper.UserBriefResponseMapper;
import icu.tianqingyuluo.onlineim.mapper.UserMapper;
import icu.tianqingyuluo.onlineim.mapper.UserResponseMapper;
import icu.tianqingyuluo.onlineim.mapper.UserUpdateRequestMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.request.UserUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserServiceImpl implements UserService{
    @Autowired
    private UserMapper userMapper;
    @Override
    public boolean existsByUsername(String username) {
        User user=userMapper.getByUsername(username);
        return user != null;
    }

    @Override
    public void registerUser(User user){
        userMapper.insert(user);
    }

    @Override
    public UserResponse getUserInfoByUsername(String username) {
        User user=userMapper.getByUsername(username);
        return UserResponseMapper.INSTANCE.toUserResponse(user);
    }

    @Override
    public UserResponse getUserInfoByUserID(String username) {
        User user=userMapper.getById(username);
        return UserResponseMapper.INSTANCE.toUserResponse(user);
    }

    @Override
    public UserUpdateRequest updateByUsername(String username) {
        User user=userMapper.getById(username);
        return UserUpdateRequestMapper.INSTANCE.toUserUpdateRequest(user);
    }

    @Override
    public String getPasswordByUsername(String username) {
        return userMapper.getPasswordByUsername(username);
    }

    @Override
    public void updatePasswordByUsername(String username,String password) {
        userMapper.updatePasswordByUsername(username,password, LocalDateTime.now().toString());
    }

    @Override
    public List<UserBriefResponse> searchByUserID(String keyword, int LIMIT, int offset) {
        return UserBriefResponseMapper.INSTANCE.searchUsersByUserID(keyword, LIMIT, offset);
    }

    @Override
    public List<UserBriefResponse> searchByUsername(String keyword, int LIMIT, int offset) {
        return UserBriefResponseMapper.INSTANCE.searchUsersByUsername(keyword, LIMIT, offset);
    }
}
