// Logging: the request log, the call trace and the startup report.
package com.emis.analytics.logging;

// Imports: Spring's AOP building blocks (part of Spring itself; no extra library needed).
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.springframework.aop.Advisor;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcher;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;

// Switches the call trace on: every public method of a class in controller/, service/ or client/
// runs through CallTraceInterceptor. Spring applies it the same way it applies @Transactional.
@Configuration(proxyBeanMethods = false)
public class CallTraceConfig {

    // The packages whose classes are traced.
    private static final List<String> TRACED_PACKAGES = List.of(
            "com.emis.analytics.controller.", "com.emis.analytics.service.", "com.emis.analytics.client.");

    // The rule "which methods to trace" plus the interceptor. Marked as infrastructure so Spring's
    // proxy creator (the one used for @Transactional) picks it up; static so it is ready early.
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static Advisor callTraceAdvisor() {
        // Which classes: those in our traced packages.
        ClassFilter ourClasses = type -> TRACED_PACKAGES.stream().anyMatch(p -> type.getName().startsWith(p));
        // Which methods: their own public methods (not toString/equals from Java itself).
        StaticMethodMatcher publicMethods = new StaticMethodMatcher() {
            // Decides for one method.
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                // Public, and written in our code.
                return Modifier.isPublic(method.getModifiers())
                        && method.getDeclaringClass().getName().startsWith("com.emis.analytics.");
            }
        };
        // The rule plus the interceptor.
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(
                new ComposablePointcut(ourClasses, publicMethods), new CallTraceInterceptor());
        // Run outside everything else (e.g. outside the database transaction), so timings include it.
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
        // Hand it to Spring.
        return advisor;
    }
}
