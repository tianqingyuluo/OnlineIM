package icu.tianqingyuluo.onlineim.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMember {
    private String groupMemberId;
    private String gid;
    private String userid;
    private Integer role;
    private String groupNickname;
    private Date silenceUntil;
    private Date joinTime;
    private Date lastActiveTime;
} 