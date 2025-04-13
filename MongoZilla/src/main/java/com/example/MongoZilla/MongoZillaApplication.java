package com.example.MongoZilla;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource("classpath:application.yml")
public class MongoZillaApplication {

	public static void main(String[] args) {
		SpringApplication.run(MongoZillaApplication.class, args);
	}

}
