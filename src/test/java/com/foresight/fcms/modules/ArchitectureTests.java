package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.*;
import java.util.regex.Pattern;

class ArchitectureTests {
    @Test
    void persistenceDependenciesBelongOnlyToOwningServices() throws Exception {
        var root = Path.of("src/main/java");
        int scanned = 0;
        try (var files = Files.walk(root)) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                var pkg = Pattern.compile("package\\s+([\\w.]+)").matcher(source);
                if (!pkg.find()) continue;
                String name = file.getFileName().toString().replaceFirst("\\.java$", "");
                Class<?> c = Class.forName(pkg.group(1) + "." + name);
                scanned++;
                for (var field : c.getDeclaredFields()) {
                    Class<?> dependency = field.getType();
                    if (Repository.class.isAssignableFrom(dependency)
                            || JdbcOperations.class.isAssignableFrom(dependency)
                            || EntityManager.class.isAssignableFrom(dependency)) {
                        assertTrue(
                                c.isAnnotationPresent(Service.class),
                                c.getName() + " accesses persistence outside a service");
                        if (Repository.class.isAssignableFrom(dependency)
                                && !dependency
                                        .getPackageName()
                                        .startsWith("com.foresight.fcms.reference"))
                            assertEquals(
                                    c.getPackageName().split("\\.")[3],
                                    dependency.getPackageName().split("\\.")[3],
                                    c.getName() + " crosses repository ownership");
                    }
                }
                if (c.isAnnotationPresent(Controller.class))
                    for (var method : c.getDeclaredMethods())
                        assertFalse(
                                method.isAnnotationPresent(Transactional.class),
                                "Controller owns a transaction");
            }
        }
        assertTrue(scanned > 20, "Architecture scan must inspect production classes");
    }

    @Test
    void businessAuthorizationNeverChecksRoleNames() throws Exception {
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                var source = Files.readString(file);
                assertFalse(
                        source.contains("hasRole(") || source.contains("hasAnyRole("),
                        file.toString());
            }
        }
    }
}
