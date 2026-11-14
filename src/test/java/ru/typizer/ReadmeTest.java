package ru.typizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every Java example of README.md compiles, and runs against the conformance mock (SPEC §12): each
 * {@code ```java} block becomes the body of a {@code main} in its own JVM, with
 * {@code TYPIZER_API_KEY}/{@code TYPIZER_BASE_URL} pointing at the mock and the README's schema and a
 * sample PDF in the working directory. {@code ```java spring} blocks need Spring and are only read;
 * {@code ```java compile} blocks need a provider the CI has not (GOST TLS) and are only compiled.
 */
class ReadmeTest {
    private static final Pattern BLOCK = Pattern.compile("```(\\w+)([^\\n]*)\\n(.*?)```", Pattern.DOTALL);
    private static final String IMPORTS = "import java.net.URL;\nimport java.nio.charset.StandardCharsets;\n"
            + "import java.nio.file.*;\nimport java.time.Duration;\nimport java.util.*;\nimport javax.net.ssl.SSLContext;\n"
            + "import ru.typizer.*;\nimport ru.typizer.errors.*;\nimport ru.typizer.models.*;\n";

    static String readme() throws IOException {
        return new String(Files.readAllBytes(Paths.get("README.md")), StandardCharsets.UTF_8);
    }

    static List<String[]> blocks(String lang) throws IOException {
        List<String[]> out = new ArrayList<>();
        Matcher m = BLOCK.matcher(readme());
        while (m.find()) if (m.group(1).equals(lang)) out.add(new String[] {m.group(2).trim(), m.group(3)});
        return out;
    }

    @TestFactory
    Stream<DynamicTest> readmeJavaExamplesRun(@TempDir Path dir) throws Exception {
        List<String[]> java = blocks("java");
        StringBuilder members = new StringBuilder();
        List<String[]> snippets = new ArrayList<>();
        for (String[] b : java) {
            if (b[0].contains("spring")) continue;
            if (b[1].trim().matches("(?s)^(public )?static class .*")) members.append(b[1]);
            else snippets.add(b);
        }
        assertTrue(snippets.size() >= 9, "README examples: " + snippets.size());

        String schema = blocks("json").get(0)[1];
        Files.write(dir.resolve("invoice.schema.json"), schema.getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("invoice.pdf"), ConformanceTest.PDF);

        Path src = Files.createDirectories(dir.resolve("src/readme"));
        Path out = Files.createDirectories(dir.resolve("classes"));
        List<String> files = new ArrayList<>();
        for (int i = 0; i < snippets.size(); i++) {
            String code = "package readme;\n" + IMPORTS + "public class Example" + i + " {\n" + members
                    + "public static void main(String[] args) throws Exception {\n" + snippets.get(i)[1] + "}\n}\n";
            Path f = src.resolve("Example" + i + ".java");
            Files.write(f, code.getBytes(StandardCharsets.UTF_8));
            files.add(f.toString());
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "a JDK is needed to compile the README examples");
        List<String> args = new ArrayList<>(Arrays.asList("-encoding", "UTF-8", "-nowarn", "-d", out.toString(), "-cp",
                System.getProperty("java.class.path")));
        args.addAll(files);
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int rc = javac.run(null, null, errors, args.toArray(new String[0]));
        assertEquals(0, rc, "README examples do not compile:\n" + new String(errors.toByteArray(), StandardCharsets.UTF_8));

        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < snippets.size(); i++) {
            int n = i;
            String firstLine = snippets.get(i)[1].trim().split("\n")[0];
            boolean run = !snippets.get(i)[0].contains("compile");
            tests.add(DynamicTest.dynamicTest("README example " + n + (run ? "" : " (compile only)") + ": " + firstLine, () -> {
                if (run) runExample(dir, out, "readme.Example" + n);
            }));
        }
        return tests.stream();
    }

    private static void runExample(Path dir, Path classes, String mainClass) throws Exception {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "-cp", classes + File.pathSeparator + System.getProperty("java.class.path"),
                mainClass).directory(dir.toFile()).redirectErrorStream(true);
        pb.environment().put("TYPIZER_API_KEY", MockServer.key("ok"));
        pb.environment().put("TYPIZER_BASE_URL", MockServer.baseUrl());
        pb.environment().remove("TYPIZER_LOG");
        Process p = pb.start();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            byte[] b = new byte[8192];
            for (int r; (r = in.read(b)) != -1; ) buf.write(b, 0, r);
        }
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        String output = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), mainClass + " failed:\n" + output);
    }

    @Test
    void readmeNamesThisVersionAndNoInternals() throws IOException {
        String readme = readme();
        assertTrue(readme.contains("<version>" + TypizerClient.VERSION + "</version>"));
        assertTrue(readme.contains("ru.typizer:typizer-java:" + TypizerClient.VERSION));
        String pom = new String(Files.readAllBytes(Paths.get("pom.xml")), StandardCharsets.UTF_8);
        Matcher v = Pattern.compile("<artifactId>typizer-java</artifactId>\\s*<version>([^<]+)</version>").matcher(pom);
        assertTrue(v.find());
        assertEquals(TypizerClient.VERSION, v.group(1), "pom.xml and TypizerClient.VERSION");
        assertTrue(Files.exists(Paths.get("LICENSE")));
        assertTrue(Files.exists(Paths.get("CHANGELOG.md")));
        // Public surfaces never name internal components or vendors.
        List<Path> publicFiles = new ArrayList<>();
        publicFiles.add(Paths.get("README.md"));
        publicFiles.add(Paths.get("pom.xml"));
        publicFiles.add(Paths.get("CHANGELOG.md"));
        try (Stream<Path> s = Files.walk(Paths.get("src/main"))) {
            s.filter(Files::isRegularFile).forEach(publicFiles::add);
        }
        String forbidden = new String(new char[] {'d', 'o', 'c', 'l', 'i', 'n', 'g'});
        for (Path f : publicFiles) {
            String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
            assertFalse(text.contains(forbidden), f + " names an internal component");
        }
    }
}
