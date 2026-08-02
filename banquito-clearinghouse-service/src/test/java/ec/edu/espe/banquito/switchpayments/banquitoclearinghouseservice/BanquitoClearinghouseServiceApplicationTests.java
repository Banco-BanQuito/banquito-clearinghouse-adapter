package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://localhost:27017/clearingdb",
        "spring.mongodb.database=clearingdb",
        "spring.data.mongodb.uri=mongodb://localhost:27017/clearingdb",
        "spring.data.mongodb.database=clearingdb",
        "CORE_GATEWAY_URL=http://localhost:8080"
})
class BanquitoClearinghouseServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
