package ru.harness.demo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Приёмка живого прогона: проверяет не наш код, а тот, который написала модель.
 *
 * <p>Запускается руками, сразу после демонстрации, пока мишень ещё поправлена:
 *
 * <pre>mvnw test -Dgroups=mission -Dexcluded.groups=</pre>
 *
 * <p>С остальными тестами не едет: метка {@code mission} стоит в списке
 * исключённых в {@code pom.xml}. Иначе он был бы красным всегда — в репозитории
 * мишень лежит нереализованной, а класса {@code SourceLocation} нет вовсе.
 *
 * <p>Отсюда и отражение вместо прямых вызовов. Класс, который мы проверяем,
 * пишет агент, и форму выбирает сам: {@code record}, класс с геттерами, поля
 * наружу. Проверяем смысл, а не форму — и остаёмся собираемыми, когда мишень
 * возвращена на место, а {@code SourceLocation} удалён.
 */
@Tag("mission")
class TargetPracticeMissionTest {

    /** Пример из javadoc мишени — тот, который звучит в кадре. */
    @Test
    void packageOfReturnsPackageAndClassName() throws Exception {
        assertLocation("src/main/java/com/harness/agent/Agent.java",
                "com.harness.agent", "Agent");
    }

    /** Второй путь: ловим ответ, подогнанный под единственный пример из javadoc. */
    @Test
    void packageOfWorksForNestedPackage() throws Exception {
        assertLocation("src/main/java/com/harness/tool/impl/GrepTool.java",
                "com.harness.tool.impl", "GrepTool");
    }

    private static void assertLocation(String sourcePath, String packageName, String className)
            throws Exception {
        Class<?> sourceLocation = sourceLocationClass();
        Method packageOf = theOnlyPackageOf();

        assertThat(packageOf.getReturnType())
                .as("packageOf должен возвращать SourceLocation")
                .isEqualTo(sourceLocation);

        Object location = packageOf.invoke(null, sourcePath);
        assertThat(location).as("packageOf вернул null").isNotNull();
        assertThat(valueOf(location, "packageName"))
                .as("packageName для %s", sourcePath).isEqualTo(packageName);
        assertThat(valueOf(location, "className"))
                .as("className для %s", sourcePath).isEqualTo(className);
    }

    /** Класса может не быть: тогда агент до него просто не дошёл. */
    private static Class<?> sourceLocationClass() {
        try {
            return Class.forName("com.harness.demo.SourceLocation");
        } catch (ClassNotFoundException notWritten) {
            throw new AssertionError("класса com.harness.demo.SourceLocation нет:"
                    + " агент его не завёл", notWritten);
        }
    }

    /**
     * Метод должен быть ровно один. Два {@code packageOf} рядом — это не успех
     * с замечанием, а невыполненное задание: просили поменять существующий.
     */
    private static Method theOnlyPackageOf() {
        List<Method> found = Arrays.stream(TargetPractice.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("packageOf"))
                .toList();
        if (found.isEmpty()) {
            throw new AssertionError("в TargetPractice нет метода packageOf");
        }
        if (found.size() > 1) {
            throw new AssertionError("методов packageOf в TargetPractice: " + found.size()
                    + " — агент завёл второй рядом вместо правки существующего");
        }
        return found.getFirst();
    }

    /** Значение поля, как бы агент его ни оформил: аксессор, геттер, поле. */
    private static Object valueOf(Object instance, String name) throws Exception {
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String candidate : List.of(name, getter)) {
            try {
                Method accessor = instance.getClass().getMethod(candidate);
                return accessor.invoke(instance);
            } catch (NoSuchMethodException keepLooking) {
                // следующий вариант имени
            }
        }
        try {
            Field plain = instance.getClass().getDeclaredField(name);
            plain.setAccessible(true);
            return plain.get(instance);
        } catch (NoSuchFieldException notThere) {
            throw new AssertionError("у SourceLocation нет ни метода, ни поля " + name, notThere);
        }
    }
}
