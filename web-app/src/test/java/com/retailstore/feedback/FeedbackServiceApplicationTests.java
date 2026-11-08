package com.retailstore.feedback;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// The test profile uses an in-memory database and a dummy Gemini key; no request is sent while the context starts
@SpringBootTest
@ActiveProfiles("test")
class FeedbackServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
