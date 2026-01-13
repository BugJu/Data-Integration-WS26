package uni.dj;

import net.bytebuddy.jar.asm.signature.SignatureReader;
import net.bytebuddy.jar.asm.signature.SignatureVisitor;
import org.objectweb.asm.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/*
    Analyzer that scans project bytecode and compares it against dependency JARs
    to identify potentially unused dependencies.
 */
public class UnusedDependencyAnalyzer {

    private final MavenLogger logger;
    private final Set<String> usedClasses = new HashSet<>();

    // Whitelist für bekannte false-positives
    private static final Set<String> COMPILE_ONLY_PATTERNS = Set.of(
            "lombok", "annotations", "javax/annotation", "jakarta/annotation",
            "org/jetbrains/annotations", "com/google/errorprone/annotations"
    );

    public UnusedDependencyAnalyzer(MavenLogger logger) {
        this.logger = logger;
    }

    /*
        Scans the project's classes directory to find all used class names.
     */
    public void analyzeProjectUsage(File projectClassesDir) throws Exception {
        if (!projectClassesDir.exists()) {
            logger.warn("Classes directory not found: " + projectClassesDir);
            return;
        }

        logger.info("Analyzing project bytecode from: " + projectClassesDir);
        analyzeDirectory(projectClassesDir);
        logger.info("Found " + usedClasses.size() + " used classes");
    }

    /*
        Recursively scans a directory for .class files.
     */
    private void analyzeDirectory(File directory) throws Exception {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                analyzeDirectory(file);
            } else if (file.getName().endsWith(".class")) {
                analyzeClassFile(file);
            }
        }
    }

    /*
        Analyzes a single .class file using ASM.
     */
    private void analyzeClassFile(File classFile) throws Exception {
        try (FileInputStream fis = new FileInputStream(classFile)) {
            ClassReader reader = new ClassReader(fis);
            ClassVisitor visitor = new DependencyCollector();
            reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    /*
        ASM ClassVisitor that collects all class references found in the bytecode.
     */
    private class DependencyCollector extends ClassVisitor {

        public DependencyCollector() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            if (superName != null && !superName.equals("java/lang/Object")) {
                usedClasses.add(superName);
            }

            if (interfaces != null) {
                Collections.addAll(usedClasses, interfaces);
            }

            if (signature != null) {
                collectFromSignature(signature);
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            collectFromDescriptor(descriptor);
            if (signature != null) {
                collectFromSignature(signature);
            }

            // FIX: Rückgabe FieldVisitor für Annotations auf Fields
            return new FieldVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    collectFromDescriptor(descriptor);
                    return createAnnotationVisitor();
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            collectFromDescriptor(descriptor);
            if (signature != null) {
                collectFromSignature(signature);
            }
            if (exceptions != null) {
                Collections.addAll(usedClasses, exceptions);
            }

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitTypeInsn(int opcode, String type) {
                    usedClasses.add(type);
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                    usedClasses.add(owner);
                    collectFromDescriptor(descriptor);
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name,
                                            String descriptor, boolean isInterface) {
                    usedClasses.add(owner);
                    collectFromDescriptor(descriptor);
                }

                @Override
                public void visitInvokeDynamicInsn(String name, String descriptor,
                                                   Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                    collectFromDescriptor(descriptor);

                    // FIX: Bootstrap-Argumente analysieren
                    usedClasses.add(bootstrapMethodHandle.getOwner());
                    collectFromDescriptor(bootstrapMethodHandle.getDesc());

                    for (Object arg : bootstrapMethodArguments) {
                        if (arg instanceof Type) {
                            collectFromType((Type) arg);
                        } else if (arg instanceof Handle h) {
                            usedClasses.add(h.getOwner());
                            collectFromDescriptor(h.getDesc());
                        }
                    }
                }

                @Override
                public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                    collectFromDescriptor(descriptor);
                }

                @Override
                public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                    if (type != null) {
                        usedClasses.add(type);
                    }
                }

                @Override
                public void visitLocalVariable(String name, String descriptor, String signature,
                                               Label start, Label end, int index) {
                    collectFromDescriptor(descriptor);
                    if (signature != null) {
                        collectFromSignature(signature);
                    }
                }

                // FIX: Annotations auf Methoden-Parametern
                @Override
                public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                    collectFromDescriptor(descriptor);
                    return createAnnotationVisitor();
                }

                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    collectFromDescriptor(descriptor);
                    return createAnnotationVisitor();
                }
            };
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            collectFromDescriptor(descriptor);
            return createAnnotationVisitor();
        }
    }

    /*
        Creates an AnnotationVisitor to track class usage within annotations.
        @returns AnnotationVisitor instance.
     */
    private AnnotationVisitor createAnnotationVisitor() {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public void visit(String name, Object value) {
                if (value instanceof Type) {
                    collectFromType((Type) value);
                }
            }

            @Override
            public void visitEnum(String name, String descriptor, String value) {
                collectFromDescriptor(descriptor);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                collectFromDescriptor(descriptor);
                return createAnnotationVisitor(); // Rekursiv für nested annotations
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                return createAnnotationVisitor(); // Arrays in Annotations
            }
        };
    }

    /*
        Extracts class names from a JVM descriptor string.
     */
    private void collectFromDescriptor(String descriptor) {
        if (descriptor == null) return;

        Type type = Type.getType(descriptor);

        if (type.getSort() == Type.OBJECT) {
            usedClasses.add(type.getInternalName());
        } else if (type.getSort() == Type.ARRAY) {
            Type elementType = type.getElementType();
            if (elementType.getSort() == Type.OBJECT) {
                usedClasses.add(elementType.getInternalName());
            }
        } else if (type.getSort() == Type.METHOD) {
            for (Type argType : type.getArgumentTypes()) {
                collectFromType(argType);
            }
            collectFromType(type.getReturnType());
        }
    }

    /*
        Extracts class names from a ASM Type object.
     */
    private void collectFromType(Type type) {
        if (type.getSort() == Type.OBJECT) {
            usedClasses.add(type.getInternalName());
        } else if (type.getSort() == Type.ARRAY) {
            Type elementType = type.getElementType();
            if (elementType.getSort() == Type.OBJECT) {
                usedClasses.add(elementType.getInternalName());
            }
        }
    }

    /*
        Extracts class names from a generic signature string.
     */
    private void collectFromSignature(String signature) {
        if (signature == null) return;

        SignatureReader reader = new SignatureReader(signature);
        reader.accept(new SignatureVisitor(Opcodes.ASM9) {
            @Override
            public void visitClassType(String name) {
                usedClasses.add(name);
            }
        });
    }

    /*
        Checks if a JAR belongs to a known compile-only dependency (e.g., Lombok).
        @returns true if it is a compile-only dependency.
     */
    private boolean isCompileOnlyDependency(File jarFile) {
        String jarName = jarFile.getName().toLowerCase();
        for (String pattern : COMPILE_ONLY_PATTERNS) {
            if (jarName.contains(pattern.replace("/", ""))) {
                return true;
            }
        }
        return false;
    }

    /*
        Checks if any classes from the given dependency JAR are used by the project.
        @returns true if the dependency is used.
     */
    public boolean isDependencyUsed(File dependencyJar) {
        if (!dependencyJar.exists() || !dependencyJar.getName().endsWith(".jar")) {
            return true;
        }

        // FIX: Compile-only Dependencies immer als "used" markieren
        if (isCompileOnlyDependency(dependencyJar)) {
            logger.debug("Compile-only dependency (whitelist): " + dependencyJar.getName());
            return true;
        }

        try {
            Set<String> jarClasses = extractClassNamesFromJar(dependencyJar);

            for (String jarClass : jarClasses) {
                if (usedClasses.contains(jarClass)) {
                    logger.debug("Dependency used: " + dependencyJar.getName() + " (class: " + jarClass + ")");
                    return true;
                }
            }

            logger.debug("Dependency POTENTIALLY UNUSED: " + dependencyJar.getName()
                    + " (may be used via reflection/constants)");
            return false;

        } catch (Exception e) {
            logger.warn("Error analyzing JAR: " + dependencyJar.getName() + " - " + e.getMessage());
            return true; // Bei Fehlern als "used" annehmen
        }
    }

    /*
        Extracts all class names present in a JAR file.
        @returns Set of class internal names.
     */
    private Set<String> extractClassNamesFromJar(File jarFile) throws Exception {
        Set<String> classNames = new HashSet<>();

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".class") && !name.startsWith("META-INF")) {
                    String className = name.replace(".class", "");
                    classNames.add(className);
                }
            }
        }

        return classNames;
    }
}