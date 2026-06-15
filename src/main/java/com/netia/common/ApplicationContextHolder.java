package com.netia.common;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @deprecated This class was used as a workaround to lazily fetch beans from the Spring
 *   context in places where constructor injection caused circular dependencies. It is an
 *   anti-pattern: static mutable state makes it impossible to test in isolation and hides
 *   real dependencies.
 *
 *   The correct solution is constructor injection with {@code @Lazy} on the problematic
 *   dependency. All usages have been migrated. This class is kept only for backward
 *   compatibility with any external code that may reference it — remove it once all
 *   consumers are confirmed clean.
 */
@Deprecated(since = "1.1.0", forRemoval = true)
@Component
public class ApplicationContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        context = applicationContext;
    }

    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }
}
