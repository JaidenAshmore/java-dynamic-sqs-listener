package it.com.jashmore.example;

import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@ImportAutoConfiguration(QueueListenerConfiguration.class)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }
}
