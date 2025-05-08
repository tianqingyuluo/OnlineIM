package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import org.apache.ibatis.annotations.Param;
import org.mapstruct.factory.Mappers;

import java.util.List;

public interface UserBriefResponseMapper {
    UserBriefResponseMapper INSTANCE = Mappers.getMapper(UserBriefResponseMapper.class);
    /**
     * 根据关键词查询用户简要信息，支持分页
     *
     * @param UserID 关键词（昵称或用户ID）
     * @param limit   每页记录数
     * @param offset  起始偏移量
     * @return 用户简要信息列表
     */
    List<UserBriefResponse> searchUsersByUserID(
            @Param("UserID") String UserID,
            @Param("limit") int limit,
            @Param("offset") int offset
    );
    List<UserBriefResponse> searchUsersByUsername(
            @Param("Username") String Username,
            @Param("limit") int limit,
            @Param("offset") int offset
    );
}