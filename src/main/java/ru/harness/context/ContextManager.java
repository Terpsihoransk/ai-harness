package ru.harness.context;

import ru.harness.agent.AgentProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Что агент знает о мире до первого вопроса.
 *
 * <p>Ключевая мысль урока: <b>не всё знание добывается вызовом инструмента</b>.
 * Ветку git, рабочую папку и правила проекта агент спрашивал бы почти всегда,
 * и каждый раз это стоило бы хода. Дешевле сказать ему это сразу.
 *
 * <p>Собранный промпт кэшируется на время сессии. Причина не в скорости: промпт
 * уезжает в модель при <em>каждом</em> запросе, и если он будет меняться от хода
 * к ходу, поплывёт и поведение агента.
 */
@Component
public class ContextManager {

    private final GitStatusProvider gitStatusProvider;
    private final ProjectMemoryLoader projectMemoryLoader;
    private final SystemPromptBuilder systemPromptBuilder;
    private final Path workspaceRoot;
    private @Nullable String cached;


    public ContextManager(GitStatusProvider gitStatusProvider, ProjectMemoryLoader projectMemoryLoader, SystemPromptBuilder systemPromptBuilder, AgentProperties properties) {
        this.gitStatusProvider = gitStatusProvider;
        this.projectMemoryLoader = projectMemoryLoader;
        this.systemPromptBuilder = systemPromptBuilder;
        this.workspaceRoot = Path.of(properties.workspaceRoot()).toAbsolutePath().normalize();
    }

    public String systemPrompt() {
        if(cached == null){
            cached = systemPromptBuilder.build(dynamicContext());
        }
        return cached;
    }

    private List<String> dynamicContext() {
        List<String> sections = new ArrayList<>();
        sections.add(environment());
        String gitStatus = gitStatusProvider.describe();
        if (gitStatus != null) {
            sections.add("# Repository\n" + gitStatus);
        }
        String memory = projectMemoryLoader.load();
        if (memory != null) {
            sections.add("# Project notes (%s)\n%s".formatted(ProjectMemoryLoader.MEMORY_FILE, memory));
        }

        return sections;
    }


    private String environment() {
        return """
                # Environment
                This section and the ones after it were gathered for you before the conversation
                started and are already correct. Answer from them directly. Do not search the
                project to rediscover where you are, what branch you are on, what is uncommitted,
                or what this project is — you already know.
                Working directory: %s
                Platform: %s
                Today: %s"""
                .formatted(workspaceRoot, System.getProperty("os.name"), LocalDate.now());
    }

}
