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
public class Group {
    private String gid;
    private String gname;
    private String gavatar;
    private String ownerid;
    private Date createat;
    private Date updateat;
} 