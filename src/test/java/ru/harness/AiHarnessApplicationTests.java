package ru.harness;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestPermissionConfig.class)
@ActiveProfiles("test")
class AiHarnessApplicationTests {

	@Test
	void contextLoads() {
	}

}
