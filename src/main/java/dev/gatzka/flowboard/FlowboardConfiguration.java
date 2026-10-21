package dev.gatzka.flowboard;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class FlowboardConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * Executor used to fan out change notifications to connected browsers. Virtual threads keep
     * the fan-out cheap no matter how many sessions are listening.
     */
    @Bean(destroyMethod = "close")
    ExecutorService broadcastExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("flowboard-broadcast-", 0).factory());
    }
}
