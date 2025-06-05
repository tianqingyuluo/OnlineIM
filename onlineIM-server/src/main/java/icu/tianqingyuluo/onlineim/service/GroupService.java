package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupJoinRequestResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public interface GroupService {

    String createGroup(GroupCreateRequest request, String userID);

    GroupBriefResponse getGroupByID(String groupID);

    GroupResponse getGroupDetailByID(String groupId,String userid);

    GroupResponse updateGroupByID(String groupId, GroupUpdateRequest request, String userID);

    boolean disbandGroupByID(String groupId, String userID);

    List<GroupResponse> getJoinedGroupsByUserID(String userID);

    boolean isUserAdminOrOwner(String groupId, String userID);

    String uploadGroupAvatar(String groupId, MultipartFile avatar, String userID);

    List<GroupMemberResponse> getGroupMembersByGroupID(String groupId);

    boolean inviteUsersToGroup(String groupId, String userIdsToInvite, String inviterId);

    String requestToJoinGroup(String groupId, String applicantId, String message);

    boolean quitGroup(String groupId, String userId);

    List<GroupResponse> searchGroups(String trim);

    List<GroupJoinRequestResponse> getGroupJoinRequests(String groupId,String operatorId);

    boolean handleJoinRequest(String groupId, String requestId, String action, String operatorId);

    boolean canUserInvite(String groupId, String inviterId);

    boolean isUserMember(String groupId, String userID);
}
