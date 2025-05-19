package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupJoinRequestResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.GroupJoinRequest;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 群组加入请求数据访问接口
 */
@Mapper
public interface GroupJoinRequestMapper {

    @Select("SELECT * FROM group_join_requests WHERE id = #{id}")
    GroupJoinRequest getById(String id);

    @Select("SELECT user_id FROM group_join_requests WHERE id = #{id}")
    String getUseridById(String id);

    @Select("SELECT * FROM group_join_requests WHERE group_id = #{groupId} AND status = 0 " +
            "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<GroupJoinRequest> getPendingRequestsByGroupId(@Param("groupId") String groupId, @Param("offset") int offset, @Param("limit") int limit);
    
    @Select("SELECT COUNT(*) FROM group_join_requests WHERE group_id = #{groupId} AND status = 0")
    int countPendingRequestsByGroupId(String groupId);
    
    @Select("SELECT * FROM group_join_requests WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<GroupJoinRequest> getByUserId(String userId);
    
    @Select("SELECT * FROM group_join_requests WHERE group_id = #{groupId} AND user_id = #{userId} AND status = 0")
    GroupJoinRequest getPendingRequestByGroupIdAndUserId(@Param("groupId") String groupId, @Param("userId") String userId);

    @Select("SELECT EXISTS(SELECT 1 FROM group_join_requests WHERE user_id = #{userid} AND status=0)")
    boolean existsByUserid(String userid);

    @Select("SELECT EXISTS(SELECT 1 FROM group_join_requests WHERE id = #{id} AND status=0)")
    boolean isPendingByRequestId(String id);

    @Insert("INSERT INTO group_join_requests(id, group_id, user_id, inviter_id, message,status) " +
            "VALUES(#{id}, #{groupId}, #{userId}, #{inviterId},#{message},#{status})")
    int insert(GroupJoinRequest request);
    
    @Update("UPDATE group_join_requests SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("status") int status);
    
    @Delete("DELETE FROM group_join_requests WHERE id = #{id}")
    int delete(String id);
} 