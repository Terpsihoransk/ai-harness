package ru.harness.tool;

import ru.harness.agent.AgentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WorkspaceGuardTest {
    @TempDir Path root;

    @Test
    void путьСДругимКорнемПоказываемЦеликом() {
        // Человек может разрешить работу вне проекта, а Glob и Grep печатают каждый найденный путь.
        // Относительным такой путь не сделать, и падать из-за этого нельзя.
        assumeTrue(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"), "разные корни путей бывают на Windows");
        var guard = new WorkspaceGuard(new AgentProperties(20, root.toString(), true));

        assertThat(guard.relativize(Path.of("//server/share/report.txt"))).isEqualTo("//server/share/report.txt");
        assertThat(guard.relativize(root.resolve("src/App.java"))).isEqualTo("src/App.java");
    }
}
