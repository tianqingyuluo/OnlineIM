package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.response.UserResponse;
import org.apache.ibatis.annotations.*;

/**
 * 用户响应对象映射器
 * 用于将 User 实体映射为 UserResponse DTO
 */
@Mapper
public interface UserResponseMapper {
    UserResponse getUserResponseByUsername(@Param("username") String username);
    UserResponse getUserResponseByUserID(@Param("id") String id);
}