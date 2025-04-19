package main.jarrenamerapp;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public class JarRenamerService {
    private final File jarFile;
    private final Map<String, String> mappings;
    private final boolean isPrefixMode;
    private final String prefix;
    private final boolean isReplaceMode;
    private final String textToReplace;
    private final String replacementText;
    private final boolean handleDuplicates;
    private final Set<String> classesToRename;
    private final boolean isThreeFilesMode;
    private final List<String> classNames;
    private final List<String> methodNames;
    private final List<String> fieldNames;

    private final Map<String, Set<String>> classFields = new HashMap<>();
    private final Map<String, String> classToNewName = new HashMap<>();
    private final Map<String, String> fieldMappingGlobal = new HashMap<>();
    private final Map<String, String> methodMappingGlobal = new HashMap<>();

    private final Map<String, Integer> classNameCounters = new HashMap<>();
    private final Map<String, Integer> methodNameCounters = new HashMap<>();
    private final Map<String, Integer> fieldNameCounters = new HashMap<>();

    private final Map<String, Set<String>> usedNamesInClass = new HashMap<>();

    private final Map<String, String> consistentRenamingCache = new HashMap<>();

    public JarRenamerService(File jarFile, File mappingFile, Set<String> classesToRename) throws IOException {
        this.jarFile = jarFile;
        this.mappings = loadMappingsFromFile(mappingFile);
        this.isPrefixMode = false;
        this.prefix = null;
        this.isReplaceMode = false;
        this.textToReplace = null;
        this.replacementText = null;
        this.handleDuplicates = false;
        this.classesToRename = classesToRename;
        this.isThreeFilesMode = false;
        this.classNames = null;
        this.methodNames = null;
        this.fieldNames = null;
    }

    public JarRenamerService(File jarFile, String mappingSource, boolean isContent, Set<String> classesToRename) throws IOException {
        this.jarFile = jarFile;
        if (isContent) {
            this.mappings = parseMappingContent(mappingSource);
        } else {
            this.mappings = loadMappingsFromUrl(mappingSource);
        }
        this.isPrefixMode = false;
        this.prefix = null;
        this.isReplaceMode = false;
        this.textToReplace = null;
        this.replacementText = null;
        this.handleDuplicates = false;
        this.classesToRename = classesToRename;
        this.isThreeFilesMode = false;
        this.classNames = null;
        this.methodNames = null;
        this.fieldNames = null;
    }

    public JarRenamerService(File jarFile, Map<String, String> mappings, boolean isPrefixMode,
                             String prefix, boolean isReplaceMode, String textToReplace,
                             String replacementText, boolean handleDuplicates,
                             Set<String> classesToRename) {
        this.jarFile = jarFile;
        this.mappings = mappings != null ? mappings : new HashMap<>();
        this.isPrefixMode = isPrefixMode;
        this.prefix = prefix;
        this.isReplaceMode = isReplaceMode;
        this.textToReplace = textToReplace;
        this.replacementText = replacementText;
        this.handleDuplicates = handleDuplicates;
        this.classesToRename = classesToRename;
        this.isThreeFilesMode = false;
        this.classNames = null;
        this.methodNames = null;
        this.fieldNames = null;
    }

    public JarRenamerService(File jarFile, List<String> classNames, List<String> methodNames,
                             List<String> fieldNames, boolean handleDuplicates,
                             Set<String> classesToRename) {
        this.jarFile = jarFile;
        this.mappings = new HashMap<>();
        this.isPrefixMode = false;
        this.prefix = null;
        this.isReplaceMode = false;
        this.textToReplace = null;
        this.replacementText = null;
        this.handleDuplicates = handleDuplicates;
        this.classesToRename = classesToRename;
        this.isThreeFilesMode = true;
        this.classNames = classNames;
        this.methodNames = methodNames;
        this.fieldNames = fieldNames;
    }

    public File execute() throws IOException {
        String originalName = jarFile.getName();
        String baseName = originalName.substring(0, originalName.lastIndexOf('.'));
        File outputFile = new File(jarFile.getParentFile(), baseName + "-renamed.jar");
        analyzeClasses();
        analyzeFieldsAndMethods();
        if (handleDuplicates) {
            Set<String> usedClassNames = new HashSet<>();
            for (String className : classFields.keySet()) {
                boolean shouldRename = classesToRename == null || classesToRename.contains(className);

                String newClassName;
                if (shouldRename) {
                    newClassName = getConsistentNameForClass(className);
                    while (usedClassNames.contains(newClassName)) {
                        newClassName = newClassName + "_" + generateDeterministicSuffix(className, classNameCounters);
                    }
                } else {
                    newClassName = className;
                }

                usedClassNames.add(newClassName);
                classToNewName.put(className, newClassName);
                usedNamesInClass.put(className, new HashSet<>());
                if (shouldRename) {
                    String simpleClassName = getSimpleClassName(newClassName);
                    usedNamesInClass.get(className).add(simpleClassName);
                }
            }
        }
        try (JarInputStream jarIn = new JarInputStream(new FileInputStream(jarFile));
             JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(outputFile))) {
            Set<String> processedEntries = new HashSet<>();
            JarEntry entry;
            while ((entry = jarIn.getNextJarEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.endsWith(".class")) {
                    String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');
                    boolean shouldRename = classesToRename == null || classesToRename.contains(className);

                    byte[] classBytes = readAllBytes(jarIn);
                    byte[] transformedClass = transformClass(classBytes, className);

                    String newEntryName;
                    if (shouldRename) {
                        String newClassName = handleDuplicates ?
                                classToNewName.get(className) :
                                getConsistentNameForClass(className);
                        newEntryName = newClassName != null
                                ? newClassName.replace('.', '/') + ".class"
                                : entryName;
                    } else {
                        newEntryName = entryName;
                    }

                    if (!processedEntries.contains(newEntryName)) {
                        processedEntries.add(newEntryName);
                        jarOut.putNextEntry(new JarEntry(newEntryName));
                        jarOut.write(transformedClass);
                    }
                } else {
                    if (!processedEntries.contains(entryName)) {
                        processedEntries.add(entryName);

                        jarOut.putNextEntry(new JarEntry(entryName));
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = jarIn.read(buffer)) != -1) {
                            jarOut.write(buffer, 0, bytesRead);
                        }
                    }
                }

                jarOut.closeEntry();
            }
        }

        return outputFile;
    }

    private String getConsistentNameForClass(String className) {
        if (consistentRenamingCache.containsKey(className)) {
            return consistentRenamingCache.get(className);
        }

        String newName = calculateNewName(className, "class");
        consistentRenamingCache.put(className, newName);
        return newName;
    }

    private String getConsistentNameForMethod(String className, String methodName) {
        String key = className + "." + methodName;
        if (consistentRenamingCache.containsKey(key)) {
            return consistentRenamingCache.get(key);
        }

        String newName = calculateNewName(methodName, "method");
        consistentRenamingCache.put(key, newName);
        return newName;
    }

    private String getConsistentNameForField(String className, String fieldName) {
        String key = className + "." + fieldName;
        if (consistentRenamingCache.containsKey(key)) {
            return consistentRenamingCache.get(key);
        }

        String newName = calculateNewName(fieldName, "field");
        consistentRenamingCache.put(key, newName);
        return newName;
    }

    private void analyzeClasses() throws IOException {
        try (JarInputStream jarIn = new JarInputStream(new FileInputStream(jarFile))) {
            JarEntry entry;
            while ((entry = jarIn.getNextJarEntry()) != null) {
                String entryName = entry.getName();

                if (entryName.endsWith(".class")) {
                    String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');
                    byte[] classBytes = readAllBytes(jarIn);
                    ClassReader reader = new ClassReader(classBytes);
                    FieldAnalyzer analyzer = new FieldAnalyzer();
                    reader.accept(analyzer, 0);
                    classFields.put(className, analyzer.getFieldNames());
                }
            }
        }
    }

    private void analyzeFieldsAndMethods() throws IOException {
        try (JarInputStream jarIn = new JarInputStream(new FileInputStream(jarFile))) {
            JarEntry entry;
            while ((entry = jarIn.getNextJarEntry()) != null) {
                String entryName = entry.getName();

                if (entryName.endsWith(".class")) {
                    String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');

                    boolean shouldRename = classesToRename == null || classesToRename.contains(className);

                    if (shouldRename) {
                        byte[] classBytes = readAllBytes(jarIn);
                        ClassReader reader = new ClassReader(classBytes);
                        MemberAnalyzer analyzer = new MemberAnalyzer(className);
                        reader.accept(analyzer, 0);

                        for (String fieldName : analyzer.getFieldNames()) {
                            String key = className.replace('.', '/') + "." + fieldName;
                            String newFieldName = getConsistentNameForField(className, fieldName);

                            Set<String> usedNames = usedNamesInClass.get(className);
                            if (usedNames == null) {
                                usedNames = new HashSet<>();
                                usedNamesInClass.put(className, usedNames);
                            }

                            String newClassName = classToNewName.get(className);
                            if (newClassName != null) {
                                String simpleClassName = getSimpleClassName(newClassName);
                                if (newFieldName.equals(simpleClassName)) {
                                    newFieldName = newFieldName + "_" + generateDeterministicSuffix(fieldName, fieldNameCounters);
                                }
                            }

                            while (usedNames.contains(newFieldName)) {
                                newFieldName = newFieldName + "_" + generateDeterministicSuffix(fieldName, fieldNameCounters);
                            }

                            usedNames.add(newFieldName);
                            fieldMappingGlobal.put(key, newFieldName);
                        }

                        for (String methodName : analyzer.getMethodNames()) {
                            if (methodName.equals("<init>") || methodName.equals("<clinit>")) {
                                continue;
                            }

                            String key = className.replace('.', '/') + "." + methodName;
                            String newMethodName = getConsistentNameForMethod(className, methodName);

                            Set<String> usedNames = usedNamesInClass.get(className);
                            if (usedNames == null) {
                                usedNames = new HashSet<>();
                                usedNamesInClass.put(className, usedNames);
                            }

                            String newClassName = classToNewName.get(className);
                            if (newClassName != null) {
                                String simpleClassName = getSimpleClassName(newClassName);
                                if (newMethodName.equals(simpleClassName)) {
                                    newMethodName = newMethodName + "_" + generateDeterministicSuffix(methodName, methodNameCounters);
                                }
                            }

                            while (usedNames.contains(newMethodName)) {
                                newMethodName = newMethodName + "_" + generateDeterministicSuffix(methodName, methodNameCounters);
                            }

                            usedNames.add(newMethodName);
                            methodMappingGlobal.put(key, newMethodName);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IOException("Error analyzing fields and methods: " + e.getMessage(), e);
        }
    }

    private byte[] transformClass(byte[] classBytes, String className) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

        ClassRemapper remapper = new ClassRemapper(writer, new CustomRemapper());
        reader.accept(remapper, ClassReader.EXPAND_FRAMES);

        return writer.toByteArray();
    }

    private String calculateNewName(String originalName, String type) {
        if (isThreeFilesMode) {
            switch (type) {
                case "class":
                    if (classNames != null && !classNames.isEmpty()) {
                        int index = Math.abs(originalName.hashCode()) % classNames.size();
                        int lastDot = originalName.lastIndexOf('.');
                        if (lastDot != -1) {
                            String packagePart = originalName.substring(0, lastDot);
                            return packagePart + "." + classNames.get(index);
                        } else {
                            return classNames.get(index);
                        }
                    }
                    break;

                case "method":
                    if (methodNames != null && !methodNames.isEmpty()) {
                        int index = Math.abs(originalName.hashCode()) % methodNames.size();
                        return methodNames.get(index);
                    }
                    break;

                case "field":
                    if (fieldNames != null && !fieldNames.isEmpty()) {
                        int index = Math.abs(originalName.hashCode()) % fieldNames.size();
                        return fieldNames.get(index);
                    }
                    break;
            }
            return originalName;
        }
        if (originalName.startsWith("java.") || originalName.startsWith("javax.") || originalName.startsWith("android.")) {
            return originalName;
        }
        if (mappings.containsKey(originalName)) {
            return mappings.get(originalName);
        }
        if (isPrefixMode) {
            if (type.equals("class")) {
                int lastDot = originalName.lastIndexOf('.');
                if (lastDot == -1) {
                    return prefix + originalName;
                } else {
                    String packagePart = originalName.substring(0, lastDot);
                    String classPart = originalName.substring(lastDot + 1);
                    return packagePart + "." + prefix + classPart;
                }
            } else {
                return prefix + originalName;
            }
        }
        else if (isReplaceMode && originalName.contains(textToReplace)) {
            return originalName.replace(textToReplace, replacementText);
        }
        return originalName;
    }

    private Map<String, String> loadMappingsFromFile(File file) throws IOException {
        return parseMappingContent(Files.readString(file.toPath()));
    }

    private Map<String, String> loadMappingsFromUrl(String urlString) throws IOException {
        try (InputStream is = new URL(urlString).openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            return parseMappingContent(content.toString());
        }
    }

    private Map<String, String> parseMappingContent(String content) {
        Map<String, String> result = new HashMap<>();

        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("->");
            if (parts.length == 2) {
                String originalName = parts[0].trim();
                String newName = parts[1].trim();
                result.put(originalName, newName);
            }
        }

        return result;
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int bytesRead;
        byte[] data = new byte[4096];

        while ((bytesRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }

        return buffer.toByteArray();
    }

    private String generateDeterministicSuffix(String originalName, Map<String, Integer> counters) {
        Integer counter = counters.getOrDefault(originalName, 0);
        counters.put(originalName, counter + 1);
        return String.valueOf(counter);
    }

    private String getSimpleClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot != -1 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }

    private static class FieldAnalyzer extends ClassVisitor {
        private final Set<String> fieldNames = new HashSet<>();

        public FieldAnalyzer() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            fieldNames.add(name);
            return super.visitField(access, name, descriptor, signature, value);
        }

        public Set<String> getFieldNames() {
            return fieldNames;
        }
    }

    private static class MemberAnalyzer extends ClassVisitor {
        private final Set<String> fieldNames = new HashSet<>();
        private final Set<String> methodNames = new HashSet<>();
        private final String className;

        public MemberAnalyzer(String className) {
            super(Opcodes.ASM9);
            this.className = className;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            fieldNames.add(name);
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            methodNames.add(name);
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        public Set<String> getFieldNames() {
            return fieldNames;
        }

        public Set<String> getMethodNames() {
            return methodNames;
        }
    }

    private class CustomRemapper extends Remapper {
        private final Map<String, String> fieldMappings = new HashMap<>();
        private final Map<String, String> methodMappings = new HashMap<>();

        public CustomRemapper() {
            fieldMappings.putAll(fieldMappingGlobal);
            methodMappings.putAll(methodMappingGlobal);
        }

        @Override
        public String map(String internalName) {
            String className = internalName.replace('/', '.');
            String newClassName;
            if (handleDuplicates) {
                newClassName = classToNewName.get(className);
                if (newClassName == null) {
                    if (classesToRename == null || classesToRename.contains(className)) {
                        newClassName = getConsistentNameForClass(className);
                    } else {
                        newClassName = className;
                    }
                }
            } else {
                if (classesToRename == null || classesToRename.contains(className)) {
                    newClassName = getConsistentNameForClass(className);
                } else {
                    newClassName = className;
                }
            }
            return newClassName != null ? newClassName.replace('.', '/') : internalName;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            if (name.equals("<init>") || name.equals("<clinit>")) {
                return name;
            }

            String key = owner + "." + name;
            if (methodMappings.containsKey(key)) {
                return methodMappings.get(key);
            }

            String ownerClassName = owner.replace('/', '.');
            if (classesToRename == null || classesToRename.contains(ownerClassName)) {
                String newName = getConsistentNameForMethod(ownerClassName, name);
                if (handleDuplicates) {
                    Set<String> usedNames = usedNamesInClass.computeIfAbsent(ownerClassName, k -> new HashSet<>());
                    String ownerNewClassName = classToNewName.get(ownerClassName);
                    if (ownerNewClassName != null) {
                        String simpleClassName = getSimpleClassName(ownerNewClassName);
                        if (newName.equals(simpleClassName)) {
                            newName = newName + "_" + generateDeterministicSuffix(name, methodNameCounters);
                        }
                    }
                    while (usedNames.contains(newName)) {
                        newName = newName + "_" + generateDeterministicSuffix(name, methodNameCounters);
                    }

                    usedNames.add(newName);
                }
                methodMappings.put(key, newName);
                return newName;
            }
            return name;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            String key = owner + "." + name;
            if (fieldMappings.containsKey(key)) {
                return fieldMappings.get(key);
            }

            String ownerClassName = owner.replace('/', '.');
            if (classesToRename == null || classesToRename.contains(ownerClassName)) {
                String newName = getConsistentNameForField(ownerClassName, name);
                if (handleDuplicates) {
                    Set<String> usedNames = usedNamesInClass.computeIfAbsent(ownerClassName, k -> new HashSet<>());
                    String ownerNewClassName = classToNewName.get(ownerClassName);
                    if (ownerNewClassName != null) {
                        String simpleClassName = getSimpleClassName(ownerNewClassName);
                        if (newName.equals(simpleClassName)) {
                            newName = newName + "_" + generateDeterministicSuffix(name, fieldNameCounters);
                        }
                    }
                    while (usedNames.contains(newName)) {
                        newName = newName + "_" + generateDeterministicSuffix(name, fieldNameCounters);
                    }

                    usedNames.add(newName);
                }
                fieldMappings.put(key, newName);
                return newName;
            }
            return name;
        }
    }
}