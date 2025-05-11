package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
@Mapper
public interface UserBriefResponseMapper {

    UserBriefResponse getUserBriefInfoByUsername(String username);

    /**
     * 根据关键词查询用户简要信息，支持分页
     *
     * @param id 关键词（昵称或用户ID）
     * @param limit   每页记录数
     * @param offset  起始偏移量
     * @return 用户简要信息列表
     */
    List<UserBriefResponse> searchUsersByUserID(
            @Param("id") String id,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    List<UserBriefResponse> searchUsersByUsername(
            @Param("username") String username,
            @Param("limit") int limit,
            @Param("offset") int offset
    );
}