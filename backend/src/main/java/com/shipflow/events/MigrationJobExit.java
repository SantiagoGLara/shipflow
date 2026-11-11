package com.shipflow.events;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "shipflow.migrations.exit-on-complete", havingValue = "true")
public class MigrationJobExit implements ApplicationListener<ApplicationReadyEvent> {

    private final ConfigurableApplicationContext context;

    public MigrationJobExit(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext() == context) {
            System.exit(SpringApplication.exit(context, () -> 0));
        }
    }
}
