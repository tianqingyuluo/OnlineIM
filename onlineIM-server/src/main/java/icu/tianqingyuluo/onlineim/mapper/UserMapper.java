package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {
    @Select("SELECT * FROM user WHERE userid = #{userid}")
    User getUserByUserid(String userid);
    
    @Select("SELECT * FROM user WHERE username = #{username}")
    User getUserByUsername(String username);
    
    @Select("SELECT * FROM user WHERE nickname LIKE CONCAT('%', #{keyword}, '%')")
    List<User> searchUsersByNickname(String keyword);
    
    @Select("SELECT * FROM user ORDER BY createat DESC")
    List<User> getAllUsers();
    
    @Insert("INSERT INTO user(userid, username, nickname, password, avatarurl) VALUES(#{userid}, #{username}, #{nickname}, #{password}, #{avatarurl})")
    int insertUser(User user);
    
    @Update("UPDATE user SET nickname = #{nickname} WHERE userid = #{userid}")
    int updateNickname(@Param("userid") String userid, @Param("nickname") String nickname);
    
    @Update("UPDATE user SET password = #{password} WHERE userid = #{userid}")
    int updatePassword(@Param("userid") String userid, @Param("password") String password);
    
    @Update("UPDATE user SET avatarurl = #{avatarurl} WHERE userid = #{userid}")
    int updateAvatar(@Param("userid") String userid, @Param("avatarurl") String avatarurl);
    
    @Update("UPDATE user SET nickname = #{nickname}, avatarurl = #{avatarurl} WHERE userid = #{userid}")
    int updateUserProfile(User user);
    
    @Delete("DELETE FROM user WHERE userid = #{userid}")
    int deleteUser(String userid);
    
    @Select("SELECT COUNT(*) FROM user WHERE username = #{username}")
    int checkUsernameExists(String username);
    
    @Select("SELECT COUNT(*) FROM user")
    int getTotalUserCount();
    
    @Select("SELECT * FROM user ORDER BY createat DESC LIMIT #{limit} OFFSET #{offset}")
    List<User> getUsersWithPagination(@Param("offset") int offset, @Param("limit") int limit);
}
