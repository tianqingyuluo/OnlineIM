package icu.tianqingyuluo.onlineim.pojo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginResponse {
    private String token;
    private String userid;
    private String username;
    private String nickname;
    private String avatarurl;
}
