package ru.harness;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.harness.agent.Agent;

/**
 * Demo режим, в котором можно протестировать что ответит модель
 */
@Component
@Profile("!test")
public class DemoRunner implements CommandLineRunner {

    private final Agent agent;

    public DemoRunner(Agent agent) {
        this.agent = agent;
    }

    @Override
    public void run(String... args) {
        System.out.println(agent.run("В классе TargetPractice не реализован метод packageOf. Найди файл, прочитай его и реализуй этот метод."));
    }
}
