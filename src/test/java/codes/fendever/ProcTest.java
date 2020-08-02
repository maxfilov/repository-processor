package codes.fendever;

import org.joor.Reflect;
import org.junit.jupiter.api.Test;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.joor.Reflect.on;
import static org.junit.jupiter.api.Assertions.*;

class ProcTest {
    final String source = "@codes.fendever.CorrelationRepositorySource\n" +
            "public enum AnnotatedClass {" +
            "  FIRST(1L, \"10\")," +
            "  SECOND(2L, \"20\");" +
            "  AnnotatedClass(Long f, String s) {" +
            "    this.f = f;" +
            "    this.s = s;" +
            "  }" +
            "  private Long f;" +
            "  private String s;" +
            "  public Long getF() {" +
            "    return f;" +
            "  }" +
            "  public String getS() {" +
            "    return s;" +
            "  }" +
            "}";

    @Test
    void test() throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        StringBuilder sb = new StringBuilder();
        Proc proc = new Proc(sb);

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Path tmp = Files.createTempDirectory("compile-test-");
        try {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(tmp.toFile()));

            final JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, null, null, Collections.singletonList(new JavaSourceFromString("AnnotatedClass", source)));
            task.setProcessors(Collections.singletonList(proc));
            assertTrue(task.call());
            final JavaCompiler.CompilationTask anotherTask = compiler.getTask(null, fileManager, null, null, null,
                    Arrays.asList(
                            new JavaSourceFromString("AnnotatedClass", source),
                            new JavaSourceFromString("AnnotatedClassRepository", sb.toString())));
            anotherTask.setProcessors(Collections.emptyList());
            anotherTask.call();

            // Convert File to a URL
            URL url = tmp.toFile().toURI().toURL();
            URL[] urls = new URL[]{url};

            // Create a new class loader with the directory
            ClassLoader cl = new URLClassLoader(urls);
            Class<?> annotatedClass = cl.loadClass("AnnotatedClass");
            Class<?> annotatedClassRepository = cl.loadClass("AnnotatedClassRepository");
            Object repo = annotatedClassRepository.newInstance();
            Object found = on(repo).call("findByF", 1L).get();
            assertNotNull(found);
            assertTrue(annotatedClass.isInstance(found));
            assertEquals("10", on(found).call("getS").get());

            found = on(repo).call("findByF", 10L).get();
            assertNull(found);

            found = on(repo).call("findByS", "20").get();
            assertNotNull(found);
            assertTrue(annotatedClass.isInstance(found));
            assertEquals(2L, (Long) on(found).call("getF").get());

            found = on(repo).call("findByS", "300").get();
            assertNull(found);

        } finally {
            deleteDirectory(tmp.toFile());
        }
    }

    private static class JavaSourceFromString extends SimpleJavaFileObject {
        final String code;

        JavaSourceFromString(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    private static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
}

