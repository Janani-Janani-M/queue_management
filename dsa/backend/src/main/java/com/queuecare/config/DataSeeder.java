package com.queuecare.config;

import com.queuecare.service.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * DataSeeder – runs once when Spring Boot starts.
 *
 * 1. Re-hydrates the in-memory DSA scheduler from today's WAITING records in MySQL.
 * 2. If no records exist yet (fresh day / empty DB), seeds demo data.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final QueueService queueService;

    public DataSeeder(QueueService queueService) {
        this.queueService = queueService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("QueueCare — hydrating in-memory scheduler from MySQL...");
        queueService.hydrateFromDatabase();

        var stats = queueService.getStats();
        if (stats.getWaitingCount() == 0 && stats.getServedToday() == 0) {
            log.info("No data for today — loading demo seed...");
            queueService.resetDemo();
        }

        log.info("Scheduler ready: {} waiting, {} served today.",
                queueService.getStats().getWaitingCount(),
                queueService.getStats().getServedToday());
    }
}
