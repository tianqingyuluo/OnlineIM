package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupResponseMapper {
    /**
     * 根据群组id返回群组详细信息
     */
    GroupResponse getGroupDetailByID(@Param("groupId") String groupId,@Param("userid") String userid);
    List<GroupResponse> getJoinedGroupsByUserID(String userid);
}
