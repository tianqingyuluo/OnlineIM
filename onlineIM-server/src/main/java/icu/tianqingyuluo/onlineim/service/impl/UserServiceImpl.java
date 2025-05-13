package icu.tianqingyuluo.onlineim.service.impl;
import icu.tianqingyuluo.onlineim.mapper.UserBriefResponseMapper;
import icu.tianqingyuluo.onlineim.mapper.UserMapper;
import icu.tianqingyuluo.onlineim.mapper.UserResponseMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.request.UserUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.User;
import icu.tianqingyuluo.onlineim.service.UserService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final UserResponseMapper userResponseMapper;
    private final UserBriefResponseMapper userBriefResponseMapper;

    public UserServiceImpl(UserMapper userMapper, UserResponseMapper userResponseMapper, UserBriefResponseMapper userBriefResponseMapper) {
        this.userMapper = userMapper;
        this.userResponseMapper = userResponseMapper;
        this.userBriefResponseMapper = userBriefResponseMapper;
    }
    @Override
    public boolean existsByUsername(String username) {
        return userMapper.existsByUsername(username);
    }

    @Override
    public void registerUser(User user){
        userMapper.insert(user);
    }

    @Override
    public UserResponse getUserInfoByUsername(String username) {
        return userResponseMapper.getUserResponseByUsername(username);
    }

    @Override
    public UserResponse getUserInfoByUserID(String ID) {
        return userResponseMapper.getUserResponseByUserID(ID);
    }

    @Override
    public void updateByUsername(String username, UserUpdateRequest userUpdateRequest) {
        userMapper.update(username,userUpdateRequest);
    }

    @Override
    public String getPasswordByUsername(String username) {
        return userMapper.getPasswordByUsername(username);
    }

    @Override
    public void updatePasswordByUsername(String username,String password) {
        userMapper.updatePasswordByUsername(username,password);
    }

    @Override
    public List<UserBriefResponse> searchByUserID(String UserID, int LIMIT, int offset) {
        return userBriefResponseMapper.searchUsersByUserID(UserID, LIMIT, offset);
    }

    @Override
    public List<UserBriefResponse> searchByUsername(String Username, int LIMIT, int offset) {
        return userBriefResponseMapper.searchUsersByUsername(Username, LIMIT, offset);
    }

    @Override
    public UserBriefResponse getUserBriefInfoByUsername(String username) {
        return userBriefResponseMapper.getUserBriefInfoByUsername(username);
    }

    @Override
    public UserBriefResponse getUserBriefInfoByID(String id){
        return  userBriefResponseMapper.getUserBriefInfoByID(id);
    }
}
