package com.obrasviales.ms_operaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.obrasviales")
public class MsOperacionesApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsOperacionesApplication.class, args);
	}

}
