package icu.tianqingyuluo.onlineim.pojo.entity;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

public class UserDetail extends User implements UserIDProvider{

    private final String userid;

    public UserDetail(String username, String password, Collection<? extends GrantedAuthority> authorities, String userid) {
        super(username, password, authorities);
        this.userid = userid;
    }

    public String getID() {
        return userid;
    }
}
