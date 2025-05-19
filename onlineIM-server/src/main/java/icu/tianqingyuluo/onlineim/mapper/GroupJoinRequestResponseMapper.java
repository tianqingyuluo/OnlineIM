package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupJoinRequestResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GroupJoinRequestResponseMapper {
    List<GroupJoinRequestResponse> getGroupJoinRequests(String groupId);
}
