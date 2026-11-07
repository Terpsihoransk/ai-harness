package ru.harness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import ru.harness.agent.Agent;

@SpringBootApplication
public class AiHarnessApplication {

	private static final Logger log = LoggerFactory.getLogger(AiHarnessApplication.class);

	private static final String STARTUP_PROMPT = "Привет! Ответь одним предложением, кто ты.";

	static void main(String[] args) {
		SpringApplication.run(AiHarnessApplication.class, args);
	}

	/**
	 * Минимальный прогон при старте: один запрос к модели и печать ответа.
	 * Нужен, чтобы убедиться, что Ollama доступна и модель отвечает.
	 */
	@Bean
	CommandLineRunner startupProbe(Agent agent,
	                               @Value("${spring.ai.ollama.base-url}") String baseUrl,
	                               @Value("${spring.ai.ollama.chat.model}") String model) {
		return args -> {
			log.info("Ollama base-url: {}, model: {}", baseUrl, model);
			log.info("→ запрос: {}", STARTUP_PROMPT);
			long startedAt = System.currentTimeMillis();
			try {
				String answer = agent.run(STARTUP_PROMPT);
				long elapsed = System.currentTimeMillis() - startedAt;
				System.out.println("=== Ответ модели (" + elapsed + " мс) ===");
				System.out.println(answer);
				System.out.println("====================");
			}
			catch (Exception e) {
				Throwable root = rootCause(e);
				log.error("Модель не ответила за {} мс. Корневая причина: {}: {}",
						System.currentTimeMillis() - startedAt,
						root.getClass().getSimpleName(), root.getMessage());
				log.error("Полный стек:", e);
			}
		};
	}

	private static Throwable rootCause(Throwable e) {
		Throwable cause = e;
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
		}
		return cause;
	}

}
