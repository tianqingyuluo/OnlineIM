package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.response.UserResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 用户响应对象映射器
 * 用于将 User 实体映射为 UserResponse DTO
 */
@Mapper
public interface UserResponseMapper {

    UserResponseMapper INSTANCE = Mappers.getMapper(UserResponseMapper.class);
    @Mapping(source = "id", target = "userId")
    @Mapping(source = "avatar", target = "avatarUrl")
    @Mapping(target = "gender", expression = "java(mapGender(user.getGender()))")
    // 自定义方法：将 Integer 类型的性别映射为 String 类型
    default String mapGender(Integer gender) {
        if (gender == null) {
            return null;
        }
        switch (gender) {
            case 1:
                return "male";
            case 2:
                return "female";
            default:
                return "";
        }
    }
    UserResponse toUserResponse(User user);
}