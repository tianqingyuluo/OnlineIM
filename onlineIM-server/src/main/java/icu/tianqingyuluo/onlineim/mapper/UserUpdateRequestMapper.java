package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.request.UserUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserUpdateRequestMapper {

    UserUpdateRequestMapper INSTANCE = Mappers.getMapper(UserUpdateRequestMapper.class);
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
    UserUpdateRequest toUserUpdateRequest(User user);
}
