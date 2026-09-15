package demo.springcore;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CoreApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                SpringApplication.run(CoreApplication.class, args)) {
            ProductService first = context.getBean(ProductService.class);
            ProductService second = context.getBean(ProductService.class);
            System.out.println("同一个 Bean：" + (first == second));
        }
    }

    @Bean
    public CommandLineRunner demo(ProductService service) {
        return args -> {
            System.out.println(service.names());
            System.out.println(service.summary());
        };
    }
}
