package demo.springcore;

import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {
    public List<String> findNames() {
        return List.of("智能助手", "知识库");
    }
}
