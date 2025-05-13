package icu.tianqingyuluo.onlineim;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@MapperScan("icu.tianqingyuluo.onlineim.mapper")
public class OnlineIMApplication {

	public static void main(String[] args) {
		SpringApplication.run(OnlineIMApplication.class, args);
	}

}
