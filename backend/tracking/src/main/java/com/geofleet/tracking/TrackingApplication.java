package com.geofleet.tracking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Slf4j
@SpringBootApplication
@EntityScan("com.geofleet.tracking.model.entity")
@EnableJpaRepositories("com.geofleet.tracking.repository")
@ConfigurationPropertiesScan("com.geofleet.tracking")
@ComponentScan(basePackages = { "com.geofleet.tracking", "com.geofleet.tracking.service" })
public class TrackingApplication {

	public static void main(String[] args) {
		try {
			log.info("🚀 Starting Fleet Tracking Application...");
			SpringApplication.run(TrackingApplication.class, args);
			log.info("✅ Application started successfully!");
		} catch (Exception e) {
			log.error("❌❌❌ APPLICATION FAILED TO START ❌❌❌", e);
			System.exit(1);
		}
	}
}