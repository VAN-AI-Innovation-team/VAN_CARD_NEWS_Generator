package com.van.cardnews;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// 기동만 확인하는 테스트라 인프로세스 발행 워커까지 돌 이유가 없다.
@SpringBootTest
@TestPropertySource(properties = "app.publish.worker.in-process.enabled=false")
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
