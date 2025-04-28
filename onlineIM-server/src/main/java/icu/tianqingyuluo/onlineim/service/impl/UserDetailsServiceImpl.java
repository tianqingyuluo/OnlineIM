package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.mapper.UserMapper;
import icu.tianqingyuluo.onlineim.pojo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 自定义UserDetailsService实现
 * 用于从数据库加载用户信息进行认证
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 从数据库查询用户信息
        User user = userMapper.getUserByUsername(username);
        
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        
        // 创建Spring Security的UserDetails对象
        // 这里简单处理，所有用户都赋予"USER"角色
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}