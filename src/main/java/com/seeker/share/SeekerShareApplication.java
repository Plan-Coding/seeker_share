package com.seeker.share;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SeekerShareApplication {

	public static void main(String[] args) {
		SpringApplication.run(SeekerShareApplication.class, args);
	}

}
