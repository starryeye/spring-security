package dev.starryeye.custom_authenticate_mvc_integration_2.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 코어 스레드 개수(항상 유지)
        executor.setCorePoolSize(5);

        // 최대 스레드 개수(코어를 넘어서는 병렬 처리 필요 시 늘어남)
        executor.setMaxPoolSize(10);

        // 큐 용량
        executor.setQueueCapacity(500);

        // 스레드 keepAliveSeconds = TTL
        // 코어 스레드 외의 추가 스레드가 idle 상태일 때, 이 시간이 지나면 풀에서 제거됨
        executor.setKeepAliveSeconds(60);  // 60초

        // 스레드 이름 접두어
        executor.setThreadNamePrefix("GlobalAsync-");

        // 코어 스레드도 타임아웃(종료) 허용할지
        // executor.setAllowCoreThreadTimeOut(true); // 필요시 설정

        executor.initialize();
        return executor;
    }


    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {

        /**
         * @Async 메서드에서 발생하는 예외 처리 커스터마이징 가능
         */
        return AsyncConfigurer.super.getAsyncUncaughtExceptionHandler();
    }

    @Bean
    public Executor tempExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Temp-Async-");
        executor.initialize();
        return executor;
    }
}
