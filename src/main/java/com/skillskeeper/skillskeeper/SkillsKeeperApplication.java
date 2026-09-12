package com.skillskeeper.skillskeeper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SkillsKeeperApplication {

	public static void main(String[] args) {
		SpringApplication.run(SkillsKeeperApplication.class, args);
	}

}
