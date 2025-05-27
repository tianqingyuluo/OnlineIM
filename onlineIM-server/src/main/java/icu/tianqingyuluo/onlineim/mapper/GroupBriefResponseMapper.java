package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupResponse;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface GroupBriefResponseMapper {
    /**
     * 获得群组简要信息
     */
    List<GroupResponse> searchGroups(String keyword);
}
