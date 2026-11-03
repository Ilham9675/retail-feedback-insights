package com.retailstore.feedback;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// A dummy key lets the Gemini client be created; no request is sent while the context starts
@SpringBootTest(properties = "spring.ai.google.genai.api-key=test-key")
class FeedbackServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
