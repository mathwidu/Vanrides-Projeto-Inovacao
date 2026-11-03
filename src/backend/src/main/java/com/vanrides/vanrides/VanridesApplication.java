package com.vanrides.vanrides;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VanridesApplication {

	public static void main(String[] args) {
		closeCompletedOperator(SpringApplication.run(VanridesApplication.class, args));
	}

	static void closeCompletedOperator(org.springframework.context.ConfigurableApplicationContext context) {
		if ("none".equals(context.getEnvironment().getProperty("spring.main.web-application-type"))
				&& context.getEnvironment().getProperty("vanrides.wa.operator.request-file") != null) {
			// Only a successful, explicitly requested CLI run ends the application.
			context.close();
		}
	}


}
