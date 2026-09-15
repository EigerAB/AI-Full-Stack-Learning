package demo.springcore;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProductService {
    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        System.out.println("服务已初始化");
    }

    public List<String> names() {
        return repository.findNames();
    }

    public String summary() {
        return "产品数量：" + names().size();
    }

    @PreDestroy
    public void close() {
        System.out.println("服务即将销毁");
    }
}
