package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户数据访问接口
 * 提供用户相关的数据库操作
 */
@Mapper
public interface UserMapper {

    /**
     * 测试数据库连接
     */
    @Select("SELECT 'Connection Successful'")
    String testConnection();

    /**
     * 根据ID查询用户
     */
    @Select("SELECT id, username, nickname, avatar, email, phone, signature, gender, status, last_login_time, last_login_ip, created_at, updated_at FROM users WHERE id = #{id}")
    User getById(String id);
    
    /**
     * 根据用户名查询用户
     */
    @Select("SELECT * FROM users WHERE username = #{username}")
    User getByUsername(String username);

    /**
     * 根据用户名查询密码
     */
    @Select("SELECT password FROM users WHERE username = #{username}")
    String getPasswordByUsername(String username);

    /**
     * 根据用户名或邮箱查询用户
     */
    @Select("SELECT * FROM users WHERE username = #{account} OR email = #{account}")
    User getByUsernameOrEmail(String account);
    
    /**
     * 根据邮箱查询用户
     */
    @Select("SELECT * FROM users WHERE email = #{email}")
    User getByEmail(String email);
    
    /**
     * 根据手机号查询用户
     */
    @Select("SELECT * FROM users WHERE phone = #{phone}")
    User getByPhone(String phone);
    
    /**
     * 获取用户列表（使用XML实现动态查询）
     */
    List<User> getList(@Param("keyword") String keyword, @Param("status") Integer status, @Param("offset") Integer offset, @Param("limit") Integer limit);
    
    /**
     * 统计符合条件的用户数量（使用XML实现动态查询）
     */
    int count(@Param("keyword") String keyword, @Param("status") Integer status);
    
    /**
     * 插入用户
     */
    @Insert("INSERT INTO users(id, username, password, nickname, avatar, email, phone, signature, gender, status, last_login_time, last_login_ip, created_at, updated_at) " +
           "VALUES(#{id}, #{username}, #{password}, #{salt}, #{nickname}, #{avatar}, #{email}, #{phone}, #{signature}, #{gender}, #{birthday}, #{status}, #{lastLoginTime}, #{lastLoginIp}, #{createdAt}, #{updatedAt})")
    int insert(User user);
    
    /**
     * 更新用户信息（使用XML实现动态更新）
     */
    int update(User user);
    
    /**
     * 更新头像
     */
    @Update("UPDATE users SET avatar = #{avatar}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateAvatar(@Param("id") String id, @Param("avatar") String avatar, @Param("updatedAt") String updatedAt);
    
    /**
     * 更新密码
     */
    @Update("UPDATE users SET password = #{password}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updatePassword(@Param("id") String id, @Param("password") String password, @Param("updatedAt") String updatedAt);

    /**
     * 根据用户名更新密码
     */
    @Update("UPDATE users SET password = #{password}, updated_at = #{updatedAt} WHERE username = #{username}")
    int updatePasswordByUsername(@Param("username") String username, @Param("password") String password, @Param("updatedAt") String updatedAt);

    /**
     * 更新登录信息
     */
    @Update("UPDATE users SET last_login_time = #{lastLoginTime}, last_login_ip = #{lastLoginIp}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateLoginInfo(@Param("id") String id, @Param("lastLoginTime") String lastLoginTime, @Param("lastLoginIp") String lastLoginIp, @Param("updatedAt") String updatedAt);
    
    /**
     * 更新用户状态
     */
    @Update("UPDATE users SET status = #{status}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("status") Integer status, @Param("updatedAt") String updatedAt);
    
    /**
     * 删除用户
     */
    @Delete("DELETE FROM users WHERE id = #{id}")
    int delete(String id);
    
    /**
     * 批量获取用户信息（使用XML实现）
     */
    List<User> batchGetUsersByIds(@Param("userIds") List<String> userIds);
    
    /**
     * 模糊搜索用户（使用XML实现）
     */
    List<User> searchUsers(@Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") int offset);
}
