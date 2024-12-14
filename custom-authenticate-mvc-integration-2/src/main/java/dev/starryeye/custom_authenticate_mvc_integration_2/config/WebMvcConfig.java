package dev.starryeye.custom_authenticate_mvc_integration_2.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {

        /**
         * WebMvcConfigurer, AsyncSupportConfigurer 를 통해
         * Spring Web MVC 에서 비동기 응답에 사용될 별도의 스레드 풀을 설정할 수 있다.
         */

        configurer.setTaskExecutor(webAsyncTaskExecutor());
        configurer.setDefaultTimeout(3000);
    }

    private AsyncTaskExecutor webAsyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Web-Async-");
        executor.initialize();
        return executor;
    }
}
