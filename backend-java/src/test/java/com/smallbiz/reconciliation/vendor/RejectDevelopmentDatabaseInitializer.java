package com.smallbiz.reconciliation.vendor;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class RejectDevelopmentDatabaseInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		TestDatabaseIsolation.assertNotDevelopmentDatabase();
	}
}
