// Java -> Kotlin source converter used to port PocketFlow's Java code.
//
// It is not a general-purpose tool. It uses the real Java compiler (javac) to
// parse and type-check the input, then prints Kotlin that keeps Java's
// semantics as literally as possible:
//   * every reference type becomes nullable and every dereference gets "!!",
//     so a null fails at the same place it would in Java;
//   * numeric widening, unboxing and string concatenation are made explicit
//     using the types javac computed;
//   * JDK calls are either mapped (see MEMBER_MAP) or left as they are and
//     backed by Java-named helpers in the Kotlin `compat` packages.
//
// Usage: java tools/j2k/J2K.java <classpath> <output dir> <File.java>...
import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class J2K {
    static Trees trees; static Types types; static Elements elements;
    static final Set<String> sourceTypes = new HashSet<>();
    static int problems = 0;
    static String currentPackage = "";

    public static void main(String[] args) throws Exception {
        String classpath = args[0]; Path out = Paths.get(args[1]);
        // "--stub=X.java" files are compiled for their API only (they describe the Kotlin look-alikes) and are not converted.
        List<File> files = new ArrayList<>(); Set<String> stubs = new HashSet<>();
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("--stub=")) { File f = new File(args[i].substring(7)); files.add(f); stubs.add(f.getAbsoluteFile().toPath().normalize().toString()); }
            else files.add(new File(args[i]));
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        JavacTask task = (JavacTask) compiler.getTask(null, fm, diagnostics, List.of("-cp", classpath, "-proc:none", "-Xlint:none", "-nowarn"), null, fm.getJavaFileObjectsFromFiles(files));
        List<CompilationUnitTree> units = new ArrayList<>();
        for (CompilationUnitTree u : task.parse()) if (!stubs.contains(Paths.get(u.getSourceFile().toUri()).toAbsolutePath().normalize().toString())) units.add(u);
        task.analyze();
        trees = Trees.instance(task); types = task.getTypes(); elements = task.getElements();
        int errors = 0;
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) if (d.getKind() == Diagnostic.Kind.ERROR) {
            if ("compiler.err.except.never.thrown.in.try".equals(d.getCode())) continue;   // the Kotlin look-alikes declare no checked exceptions
            if (errors++ < 60) System.err.println("javac: " + (d.getSource() == null ? "" : new File(d.getSource().getName()).getName()) + ":" + d.getLineNumber() + " " + d.getMessage(Locale.ROOT).replace('\n', ' '));
        }
        if (errors > 0) System.err.println("javac reported " + errors + " errors; output may be incomplete");
        for (CompilationUnitTree u : units) for (Tree t : u.getTypeDecls()) if (t instanceof ClassTree) collect((TypeElement) trees.getElement(new TreePath(new TreePath(u), t)));
        Files.createDirectories(out);
        for (CompilationUnitTree u : units) {
            String name = new File(u.getSourceFile().getName()).getName().replace(".java", ".kt");
            Files.writeString(out.resolve(name), new Emitter(u).unit(), StandardCharsets.UTF_8);
        }
        System.err.println("converted " + units.size() + " files, " + problems + " unsupported constructs");
        if (errors > 0) System.exit(2);
    }
    static void collect(TypeElement e) { if (e == null) return; sourceTypes.add(e.getQualifiedName().toString()); for (Element c : e.getEnclosedElements()) if (c instanceof TypeElement) collect((TypeElement) c); }

    // ------------------------------------------------------------------ names and types
    static final Set<String> KEYWORDS = Set.of("as", "fun", "in", "interface", "is", "object", "typealias", "typeof", "val", "var", "when");
    static String id(Object name) { String s = name.toString(); return KEYWORDS.contains(s) ? "`" + s + "`" : s; }

    static final Map<String, String> TYPE_MAP = new HashMap<>();
    static {
        String[][] m = {
            {"java.lang.Object", "Any"}, {"java.lang.String", "String"}, {"java.lang.CharSequence", "CharSequence"}, {"java.lang.Number", "Number"},
            {"java.lang.Integer", "Int"}, {"java.lang.Long", "Long"}, {"java.lang.Double", "Double"}, {"java.lang.Float", "Float"}, {"java.lang.Boolean", "Boolean"},
            {"java.lang.Character", "Char"}, {"java.lang.Byte", "Byte"}, {"java.lang.Short", "Short"}, {"java.lang.Void", "Unit"},
            {"java.lang.Iterable", "Iterable"}, {"java.lang.Comparable", "Comparable"}, {"java.lang.StringBuilder", "StringBuilder"}, {"java.lang.Throwable", "Throwable"},
            {"java.lang.Exception", "Exception"}, {"java.lang.RuntimeException", "RuntimeException"}, {"java.lang.Error", "Error"},
            {"java.lang.IllegalArgumentException", "IllegalArgumentException"}, {"java.lang.IllegalStateException", "IllegalStateException"},
            {"java.lang.NumberFormatException", "NumberFormatException"}, {"java.lang.ArithmeticException", "ArithmeticException"},
            {"java.lang.UnsupportedOperationException", "UnsupportedOperationException"}, {"java.lang.IndexOutOfBoundsException", "IndexOutOfBoundsException"},
            {"java.lang.NullPointerException", "NullPointerException"}, {"java.lang.ClassCastException", "ClassCastException"}, {"java.lang.AssertionError", "AssertionError"},
            {"java.util.NoSuchElementException", "NoSuchElementException"}, {"java.util.ConcurrentModificationException", "ConcurrentModificationException"},
            {"java.util.List", "MutableList"}, {"java.util.Collection", "MutableCollection"}, {"java.util.Map", "MutableMap"}, {"java.util.Set", "MutableSet"},
            {"java.util.Iterator", "MutableIterator"}, {"java.util.Map.Entry", "MutableMap.MutableEntry"}, {"java.util.Comparator", "Comparator"},
            {"java.util.ArrayList", "ArrayList"}, {"java.util.HashMap", "HashMap"}, {"java.util.LinkedHashMap", "LinkedHashMap"}, {"java.util.HashSet", "HashSet"}, {"java.util.LinkedHashSet", "LinkedHashSet"},
            {"java.lang.Math", "compat.util.Math"}, {"java.lang.System", "compat.util.System"},
            {"java.nio.charset.StandardCharsets", "compat.text.Charsets"}, {"java.nio.charset.Charset", "compat.text.Charset"},
            {"java.lang.AutoCloseable", "AutoCloseable"}, {"java.io.Closeable", "AutoCloseable"},
        };
        for (String[] e : m) TYPE_MAP.put(e[0], e[1]);
    }
    // JDK packages that have Kotlin look-alikes with the same class and member names.
    static final String[][] PACKAGE_MAP = {
        {"java.time.", "compat.time."}, {"java.math.", "compat.math."}, {"java.util.zip.", "compat.zip."}, {"java.io.", "compat.io."},
        {"java.net.", "compat.net."}, {"java.text.", "compat.text."},
    };
    static final Map<String, String> CLASS_MAP = Map.of(
        "java.util.Locale", "compat.util.Locale", "java.util.UUID", "compat.util.UUID", "java.util.Currency", "compat.util.Currency",
        "java.security.MessageDigest", "compat.security.MessageDigest");
    /** Compat name of a JDK class that is implemented as a Kotlin class (getters become properties), else null. */
    static String compatName(String q) {
        String direct = CLASS_MAP.get(q); if (direct != null) return direct;
        for (String[] m : PACKAGE_MAP) if (q.startsWith(m[0])) return m[1] + q.substring(m[0].length());
        return null;
    }
    static String qualified(TypeElement e) {
        String q = e.getQualifiedName().toString();
        String mapped = TYPE_MAP.get(q); if (mapped != null) return mapped;
        String compat = compatName(q); if (compat != null) return compat;
        if (sourceTypes.contains(q)) {      // same package: use the (possibly nested) simple name
            Deque<String> parts = new ArrayDeque<>(); Element c = e;
            while (c instanceof TypeElement) { parts.addFirst(c.getSimpleName().toString()); c = c.getEnclosingElement(); }
            String pkg = c instanceof PackageElement ? ((PackageElement) c).getQualifiedName().toString() : "";
            return (pkg.equals(currentPackage) || pkg.isEmpty() ? "" : pkg + ".") + String.join(".", parts);
        }
        if (q.startsWith("java.") || q.startsWith("javax.")) {   // unmapped JDK type: expected in compat.j under its simple (nested) name
            Deque<String> parts = new ArrayDeque<>(); Element c = e;
            while (c instanceof TypeElement) { parts.addFirst(c.getSimpleName().toString()); c = c.getEnclosingElement(); }
            return "compat.j." + String.join(".", parts);
        }
        return q;
    }
    static boolean isObject(TypeMirror t) { return t != null && t.getKind() == TypeKind.DECLARED && ((TypeElement) ((DeclaredType) t).asElement()).getQualifiedName().contentEquals("java.lang.Object"); }
    static boolean isPrimitive(TypeMirror t) { return t != null && t.getKind().isPrimitive(); }
    static boolean isString(TypeMirror t) { return t != null && t.getKind() == TypeKind.DECLARED && ((TypeElement) ((DeclaredType) t).asElement()).getQualifiedName().contentEquals("java.lang.String"); }
    static String boxedKind(TypeMirror t) {
        if (t == null || t.getKind() != TypeKind.DECLARED) return null;
        switch (((TypeElement) ((DeclaredType) t).asElement()).getQualifiedName().toString()) {
            case "java.lang.Integer": return "Int"; case "java.lang.Long": return "Long"; case "java.lang.Double": return "Double"; case "java.lang.Float": return "Float";
            case "java.lang.Boolean": return "Boolean"; case "java.lang.Character": return "Char"; case "java.lang.Byte": return "Byte"; case "java.lang.Short": return "Short";
            default: return null;
        }
    }
    static String primitiveName(TypeMirror t) {
        switch (t.getKind()) {
            case INT: return "Int"; case LONG: return "Long"; case DOUBLE: return "Double"; case FLOAT: return "Float"; case BOOLEAN: return "Boolean";
            case CHAR: return "Char"; case BYTE: return "Byte"; case SHORT: return "Short"; default: return null;
        }
    }
    /** Kotlin numeric kind of a primitive or boxed type, else null. */
    static String kind(TypeMirror t) { if (t == null) return null; String p = primitiveName(t); return p != null ? p : boxedKind(t); }
    static int rank(String kind) { switch (kind) { case "Byte": return 1; case "Short": return 2; case "Char": return 2; case "Int": return 3; case "Long": return 4; case "Float": return 5; case "Double": return 6; default: return 0; } }

    /** Kotlin type without the trailing nullability mark. */
    static String typeNN(TypeMirror t) {
        if (t == null) return "Any";
        switch (t.getKind()) {
            case VOID: return "Unit";
            case NULL: return "Nothing";
            case ARRAY: {
                TypeMirror c = ((ArrayType) t).getComponentType();
                if (c.getKind().isPrimitive()) return primitiveName(c) + "Array";
                return "Array<" + typeNN(c) + ">";
            }
            case TYPEVAR: return t.toString();
            case WILDCARD: {
                WildcardType w = (WildcardType) t;
                if (w.getExtendsBound() != null) return "out " + typeNN(w.getExtendsBound());
                if (w.getSuperBound() != null) return "in " + typeNN(w.getSuperBound());
                return "*";
            }
            case INTERSECTION: return typeNN(((IntersectionType) t).getBounds().get(0));
            case DECLARED: {
                DeclaredType d = (DeclaredType) t;
                String name = qualified((TypeElement) d.asElement());
                TypeMirror outer = d.getEnclosingType();
                if (d.getTypeArguments().isEmpty()) {
                    int params = ((TypeElement) d.asElement()).getTypeParameters().size();
                    if (params > 0) return name + "<" + String.join(", ", Collections.nCopies(params, "*")) + ">";   // raw type
                    return name;
                }
                return name + "<" + d.getTypeArguments().stream().map(J2K::typeNN).collect(Collectors.joining(", ")) + ">";
            }
            case ERROR: return t.toString();
            default: { String p = primitiveName(t); return p != null ? p : t.toString(); }
        }
    }
    /** Kotlin type: primitives stay non-null, everything else is nullable. */
    static String type(TypeMirror t) {
        if (t == null) return "Any?";
        if (t.getKind().isPrimitive() || t.getKind() == TypeKind.VOID) return typeNN(t);
        return typeNN(t) + "?";
    }

    static boolean isKotlinClass(Element owner) {
        if (!(owner instanceof TypeElement)) return false;
        String q = ((TypeElement) owner).getQualifiedName().toString();
        if (!TYPE_MAP.containsKey(q) && compatName(q) != null) return true;
        for (AnnotationMirror a : owner.getAnnotationMirrors()) if (a.getAnnotationType().toString().equals("kotlin.Metadata")) return true;
        return owner.getEnclosingElement() instanceof TypeElement && isKotlinClass(owner.getEnclosingElement());
    }
    static boolean isSource(Element owner) { return owner instanceof TypeElement && sourceTypes.contains(((TypeElement) owner).getQualifiedName().toString()); }
    static boolean annotated(Element e, String simpleName) {
        for (AnnotationMirror a : e.getAnnotationMirrors()) if (a.getAnnotationType().asElement().getSimpleName().contentEquals(simpleName)) return true;
        return false;
    }

    // JDK members that have a different name or shape in Kotlin. $r = receiver, $0.. = arguments.
    static final Map<String, String> MEMBER_MAP = new HashMap<>();
    static {
        String[][] m = {
            {"java.lang.String#length/0", "$r.length"}, {"java.lang.CharSequence#length/0", "$r.length"}, {"java.lang.StringBuilder#length/0", "$r.length"},
            {"java.lang.String#charAt/1", "$r[$0]"}, {"java.lang.CharSequence#charAt/1", "$r[$0]"}, {"java.lang.StringBuilder#charAt/1", "$r[$0]"},
            {"java.lang.String#toLowerCase/0", "$r.lowercase()"}, {"java.lang.String#toUpperCase/0", "$r.uppercase()"},
            {"java.lang.String#toLowerCase/1", "$r.lowercase()"}, {"java.lang.String#toUpperCase/1", "$r.uppercase()"},
            {"java.lang.String#split/1", "$r.javaSplit($0)"}, {"java.lang.String#split/2", "$r.javaSplit($0, $1)"},
            {"java.lang.String#matches/1", "$r.javaMatches($0)"}, {"java.lang.String#replaceAll/2", "$r.javaReplaceAll($0, $1)"}, {"java.lang.String#replaceFirst/2", "$r.javaReplaceFirst($0, $1)"},
            {"java.lang.String#trim/0", "$r.javaTrim()"}, {"java.lang.String#getBytes/1", "$r.toByteArray($0)"}, {"java.lang.String#getBytes/0", "$r.toByteArray()"},
            {"java.lang.String#isEmpty/0", "$r.isEmpty()"}, {"java.lang.String#equalsIgnoreCase/1", "$r.equals($0, ignoreCase = true)"},
            {"java.lang.String#compareToIgnoreCase/1", "$r.compareTo($0, ignoreCase = true)"}, {"java.lang.String#toCharArray/0", "$r.toCharArray()"},
            {"java.lang.String#contains/1", "$r.contains($0)"}, {"java.lang.String#codePointAt/1", "$r[$0].code"},
            {"java.util.Collection#size/0", "$r.size"}, {"java.util.List#size/0", "$r.size"}, {"java.util.Set#size/0", "$r.size"}, {"java.util.Map#size/0", "$r.size"},
            {"java.util.ArrayList#size/0", "$r.size"}, {"java.util.HashMap#size/0", "$r.size"}, {"java.util.LinkedHashMap#size/0", "$r.size"}, {"java.util.HashSet#size/0", "$r.size"}, {"java.util.LinkedHashSet#size/0", "$r.size"},
            {"java.util.Map#keySet/0", "$r.keys"}, {"java.util.Map#values/0", "$r.values"}, {"java.util.Map#entrySet/0", "$r.entries"},
            {"java.util.HashMap#keySet/0", "$r.keys"}, {"java.util.HashMap#values/0", "$r.values"}, {"java.util.HashMap#entrySet/0", "$r.entries"},
            {"java.util.LinkedHashMap#keySet/0", "$r.keys"}, {"java.util.LinkedHashMap#values/0", "$r.values"}, {"java.util.LinkedHashMap#entrySet/0", "$r.entries"},
            {"java.util.Map.Entry#getKey/0", "$r.key"}, {"java.util.Map.Entry#getValue/0", "$r.value"}, {"java.util.Map.Entry#setValue/1", "$r.setValue($0)"},
            {"java.util.Map#getOrDefault/2", "$r.javaGetOrDefault($0, $1)"}, {"java.util.HashMap#getOrDefault/2", "$r.javaGetOrDefault($0, $1)"}, {"java.util.LinkedHashMap#getOrDefault/2", "$r.javaGetOrDefault($0, $1)"},
            {"java.util.Map#putIfAbsent/2", "$r.javaPutIfAbsent($0, $1)"}, {"java.util.Map#computeIfAbsent/2", "$r.javaComputeIfAbsent($0, $1)"}, {"java.util.Map#merge/3", "$r.javaMerge($0, $1, $2)"},
            {"java.util.Map#forEach/1", "$r.javaForEach($0)"}, {"java.lang.Iterable#forEach/1", "$r.javaForEach($0)"}, {"java.util.List#forEach/1", "$r.javaForEach($0)"},
            {"java.util.List#sort/1", "$r.javaSort($0)"}, {"java.util.ArrayList#sort/1", "$r.javaSort($0)"}, {"java.util.Collection#removeIf/1", "$r.javaRemoveIf($0)"}, {"java.util.List#removeIf/1", "$r.javaRemoveIf($0)"}, {"java.util.ArrayList#removeIf/1", "$r.javaRemoveIf($0)"},
            {"java.util.List#toArray/1", "$r.toTypedArray()"}, {"java.util.Collection#toArray/1", "$r.toTypedArray()"}, {"java.util.ArrayList#toArray/1", "$r.toTypedArray()"},
            {"java.util.List#subList/2", "$r.subList($0, $1)"},
            {"java.util.Collection#stream/0", "$r.javaStream()"}, {"java.util.Comparator#thenComparing/1", "$r.javaThenComparing($0)"},
            {"java.util.Comparator#reversed/0", "$r.reversed()"},
            {"java.nio.charset.Charset#name/0", "$r.name"}, {"java.lang.StringBuilder#append/3", "$r.appendRange($0, $1, $1 + $2)"}, {"java.util.Iterator#forEachRemaining/1", "$r.javaForEachRemaining($0)"}, {"java.lang.Throwable#getMessage/0", "$r.message"}, {"java.lang.Throwable#getCause/0", "$r.cause"}, {"java.lang.Throwable#printStackTrace/0", "$r.printStackTrace()"},
            {"java.lang.Object#getClass/0", "$r::class"}, {"java.lang.Enum#name/0", "$r.name"}, {"java.lang.Enum#ordinal/0", "$r.ordinal"},
            {"java.lang.Integer#intValue/0", "$r"}, {"java.lang.Long#longValue/0", "$r"}, {"java.lang.Long#intValue/0", "$r.toInt()"}, {"java.lang.Integer#longValue/0", "$r.toLong()"},
            {"java.lang.Double#doubleValue/0", "$r"}, {"java.lang.Number#intValue/0", "$r.toInt()"}, {"java.lang.Number#longValue/0", "$r.toLong()"}, {"java.lang.Number#doubleValue/0", "$r.toDouble()"},
            {"java.lang.Boolean#booleanValue/0", "$r"},
        };
        for (String[] e : m) MEMBER_MAP.put(e[0], e[1]);
    }
    static final Set<String> NULL_TOLERANT = Set.of("java.util.Objects", "java.lang.String", "java.lang.StringBuilder", "java.lang.AbstractStringBuilder", "java.lang.Object", "java.io.PrintStream", "java.util.StringJoiner");
    /** java.util.List.remove(int) is removeAt in Kotlin; remove(Object) keeps its name. */
    static String memberTemplate(ExecutableElement m) {
        TypeElement owner = (TypeElement) m.getEnclosingElement();
        String q = owner.getQualifiedName().toString(); String name = m.getSimpleName().toString(); int n = m.getParameters().size();
        if (name.equals("remove") && n == 1 && m.getParameters().get(0).asType().getKind() == TypeKind.INT && isSubtype(owner, "java.util.List")) return "$r.removeAt($0)";
        Deque<TypeMirror> todo = new ArrayDeque<>(); todo.add(owner.asType()); Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            TypeMirror t = todo.poll(); if (t.getKind() != TypeKind.DECLARED) continue;
            String key = ((TypeElement) ((DeclaredType) t).asElement()).getQualifiedName().toString();
            if (!seen.add(key)) continue;
            String template = MEMBER_MAP.get(key + "#" + name + "/" + n); if (template != null) return template;
            todo.addAll(types.directSupertypes(t));
        }
        return null;
    }
    static boolean isSubtype(TypeElement e, String superName) {
        TypeElement target = elements.getTypeElement(superName);
        return target != null && types.isSubtype(types.erasure(e.asType()), types.erasure(target.asType()));
    }

    // ------------------------------------------------------------------ emitter
    static class Emitter {
        final CompilationUnitTree unit; final StringBuilder out = new StringBuilder(); int indent = 0; int labels = 0;
        final Deque<String> lambdaLabels = new ArrayDeque<>();          // innermost lambda label, or "" inside a method/anonymous function
        final Deque<TypeMirror> returnTypes = new LinkedList<>();     // null entry = "must be a non-null String" (toString)
        Emitter(CompilationUnitTree unit) { this.unit = unit; }

        String unit() {
            out.append("// GENERATED by tools/j2k from ").append(new File(unit.getSourceFile().getName()).getName()).append(" - do not edit.\n");
            out.append("@file:Suppress(\"ALL\")\n");
            currentPackage = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            if (unit.getPackageName() != null) out.append("package ").append(unit.getPackageName()).append("\n\n");
            out.append("import compat.j.*\nimport compat.text.*\nimport compat.util.*\n\n");
            TreePath root = new TreePath(unit);
            for (Tree t : unit.getTypeDecls()) if (t instanceof ClassTree) { classDecl(new TreePath(root, t), false); out.append('\n'); }
            return out.toString();
        }
        void line(String s) { for (int i = 0; i < indent; i++) out.append("    "); out.append(s).append('\n'); }
        String unsupported(Tree t, String what) { problems++; long pos = trees.getSourcePositions().getStartPosition(unit, t); long ln = unit.getLineMap().getLineNumber(Math.max(pos, 0));
            System.err.println("unsupported " + what + " at " + new File(unit.getSourceFile().getName()).getName() + ":" + ln); return "TODO(\"" + what + "\")"; }
        TypeMirror typeOf(TreePath p) { return trees.getTypeMirror(p); }
        Element elementOf(TreePath p) { return trees.getElement(p); }
        static TreePath child(TreePath p, Tree t) { return new TreePath(p, t); }

        // ---------------------------------------------------------- declarations
        static String visibility(Set<Modifier> mods, boolean nested) {
            if (mods.contains(Modifier.PRIVATE)) return nested ? "internal " : "private ";
            if (mods.contains(Modifier.PROTECTED)) return "protected ";
            if (mods.contains(Modifier.PUBLIC)) return "";
            return "internal ";
        }
        String typeParams(List<? extends TypeParameterTree> params, TreePath p) {
            if (params.isEmpty()) return "";
            List<String> parts = new ArrayList<>();
            for (TypeParameterTree tp : params) {
                String s = tp.getName().toString();
                if (!tp.getBounds().isEmpty()) s += " : " + typeNN(typeOf(child(p, tp.getBounds().get(0))));
                parts.add(s);
            }
            return "<" + String.join(", ", parts) + ">";
        }

        void classDecl(TreePath p, boolean nested) {
            ClassTree c = (ClassTree) p.getLeaf(); TypeElement e = (TypeElement) elementOf(p);
            Set<Modifier> mods = c.getModifiers().getFlags();
            boolean isInterface = c.getKind() == Tree.Kind.INTERFACE;
            if (c.getKind() == Tree.Kind.ENUM || c.getKind() == Tree.Kind.ANNOTATION_TYPE || c.getKind().name().equals("RECORD")) { line(unsupported(c, c.getKind().toString())); return; }
            StringBuilder head = new StringBuilder(visibility(mods, false).replace("private ", nested ? "internal " : "private "));
            if (isInterface) {
                long abstractMethods = e.getEnclosedElements().stream().filter(x -> x.getKind() == ElementKind.METHOD && x.getModifiers().contains(Modifier.ABSTRACT)).count();
                head.append(abstractMethods == 1 && c.getTypeParameters().isEmpty() ? "fun interface " : "interface ");
            } else {
                if (mods.contains(Modifier.ABSTRACT)) head.append("abstract "); else if (!mods.contains(Modifier.FINAL)) head.append("open ");
                if (nested && !mods.contains(Modifier.STATIC) && e.getNestingKind() == NestingKind.MEMBER) head.append("inner ");
                head.append("class ");
            }
            head.append(id(c.getSimpleName())).append(typeParams(c.getTypeParameters(), p));
            head.append(supertypes(p, c, isInterface, hasConstructors(c)));
            line(head + " {");
            indent++;
            classBody(p, c, isInterface);
            indent--;
            line("}");
        }
        static boolean hasConstructors(ClassTree c) {
            for (Tree m : c.getMembers()) if (m instanceof MethodTree && ((MethodTree) m).getName().contentEquals("<init>")) return true;
            return false;
        }
        String supertypes(TreePath p, ClassTree c, boolean isInterface, boolean explicitConstructors) {
            List<String> supers = new ArrayList<>();
            if (c.getExtendsClause() != null) supers.add(typeNN(typeOf(child(p, c.getExtendsClause()))) + (explicitConstructors ? "" : "()"));
            for (Tree i : c.getImplementsClause()) supers.add(typeNN(typeOf(child(p, i))));
            return supers.isEmpty() ? "" : " : " + String.join(", ", supers);
        }
        void classBody(TreePath p, ClassTree c, boolean isInterface) {
            List<Tree> statics = new ArrayList<>(), instance = new ArrayList<>();
            for (Tree m : c.getMembers()) {
                boolean isStatic;
                if (m instanceof VariableTree) isStatic = ((VariableTree) m).getModifiers().getFlags().contains(Modifier.STATIC) || isInterface;
                else if (m instanceof MethodTree) isStatic = ((MethodTree) m).getModifiers().getFlags().contains(Modifier.STATIC);
                else if (m instanceof BlockTree) isStatic = ((BlockTree) m).isStatic();
                else isStatic = false;
                (isStatic ? statics : instance).add(m);
            }
            for (Tree m : instance) member(p, c, m, isInterface);
            if (!statics.isEmpty()) {
                line("companion object {");
                indent++;
                for (Tree m : statics) member(p, c, m, isInterface);
                indent--;
                line("}");
            }
        }
        void member(TreePath classPath, ClassTree owner, Tree m, boolean inInterface) {
            TreePath p = child(classPath, m);
            if (m instanceof VariableTree) field(p, (VariableTree) m);
            else if (m instanceof MethodTree) method(p, owner, (MethodTree) m, inInterface);
            else if (m instanceof ClassTree) classDecl(p, true);
            else if (m instanceof BlockTree) { line("init {"); indent++; statements(p, ((BlockTree) m).getStatements()); indent--; line("}"); }
            else line(unsupported(m, "member " + m.getKind()));
        }
        static String defaultValue(TypeMirror t) {
            switch (t.getKind()) {
                case BOOLEAN: return "false"; case LONG: return "0L"; case DOUBLE: return "0.0"; case FLOAT: return "0f"; case CHAR: return "'\\u0000'";
                case INT: case SHORT: case BYTE: return "0"; default: return "null";
            }
        }
        void field(TreePath p, VariableTree v) {
            TypeMirror t = elementOf(p).asType();
            String init = v.getInitializer() == null ? defaultValue(t) : exprAs(child(p, v.getInitializer()), t);
            boolean nestedOwner = elementOf(p).getEnclosingElement().getEnclosingElement() instanceof TypeElement;
            line(visibility(v.getModifiers().getFlags(), nestedOwner) + "var " + id(v.getName()) + ": " + type(t) + " = " + init);
        }
        boolean overrides(ExecutableElement m) {
            if (annotated(m, "Override")) return true;
            if (m.getModifiers().contains(Modifier.STATIC) || m.getModifiers().contains(Modifier.PRIVATE)) return false;
            TypeElement owner = (TypeElement) m.getEnclosingElement();
            for (TypeMirror s : types.directSupertypes(owner.asType())) if (overridesIn(m, owner, s)) return true;
            return false;
        }
        boolean overridesIn(ExecutableElement m, TypeElement owner, TypeMirror superType) {
            if (superType.getKind() != TypeKind.DECLARED) return false;
            TypeElement s = (TypeElement) ((DeclaredType) superType).asElement();
            for (Element e : s.getEnclosedElements()) if (e instanceof ExecutableElement && e.getSimpleName().contentEquals(m.getSimpleName()) && elements.overrides(m, (ExecutableElement) e, owner)) return true;
            for (TypeMirror t : types.directSupertypes(superType)) if (overridesIn(m, owner, t)) return true;
            return false;
        }
        String parameters(TreePath p, List<? extends VariableTree> params) {
            List<String> parts = new ArrayList<>();
            for (VariableTree v : params) {
                TypeMirror t = elementOf(child(p, v)).asType();
                boolean varargs = ((VariableElement) elementOf(child(p, v))).getEnclosingElement() instanceof ExecutableElement && ((ExecutableElement) elementOf(child(p, v)).getEnclosingElement()).isVarArgs() && v == params.get(params.size() - 1);
                parts.add(varargs ? "vararg " + id(v.getName()) + ": " + (boxedKind(((ArrayType) t).getComponentType()) == null && !isObject(((ArrayType) t).getComponentType()) ? typeNN(((ArrayType) t).getComponentType()) : type(((ArrayType) t).getComponentType())) : id(v.getName()) + ": " + type(t));
            }
            return String.join(", ", parts);
        }
        void method(TreePath p, ClassTree owner, MethodTree m, boolean inInterface) {
            ExecutableElement e = (ExecutableElement) elementOf(p);
            Set<Modifier> mods = m.getModifiers().getFlags();
            boolean nestedOwner = e.getEnclosingElement().getEnclosingElement() instanceof TypeElement;
            if (m.getName().contentEquals("<init>") && ((TypeElement) e.getEnclosingElement()).getNestingKind() == NestingKind.ANONYMOUS) return;   // javac's synthetic constructor
            if (m.getName().contentEquals("<init>")) {
                List<? extends StatementTree> body = m.getBody().getStatements(); String delegate = "";
                int skip = 0;
                if (!body.isEmpty() && body.get(0) instanceof ExpressionStatementTree && ((ExpressionStatementTree) body.get(0)).getExpression() instanceof MethodInvocationTree) {
                    MethodInvocationTree call = (MethodInvocationTree) ((ExpressionStatementTree) body.get(0)).getExpression();
                    String target = call.getMethodSelect().toString();
                    if (target.equals("super") || target.equals("this")) {
                        TreePath callPath = child(child(child(p, m.getBody()), body.get(0)), call);
                        delegate = " : " + target + "(" + arguments(callPath, (ExecutableElement) elementOf(callPath), call.getArguments()) + ")";
                        skip = 1;
                    }
                }
                if (delegate.isEmpty() && owner.getExtendsClause() != null) delegate = " : super()";
                line(visibility(mods, nestedOwner) + "constructor(" + parameters(p, m.getParameters()) + ")" + delegate + " {");
                indent++; returnTypes.push(types.getNoType(TypeKind.VOID)); lambdaLabels.push("");
                statements(child(p, m.getBody()), body.subList(skip, body.size()));
                lambdaLabels.pop(); returnTypes.pop(); indent--;
                line("}");
                return;
            }
            StringBuilder head = new StringBuilder();
            boolean isOverride = overrides(e);
            boolean isStatic = mods.contains(Modifier.STATIC);
            String vis = visibility(mods, nestedOwner);
            if (isOverride) { head.append(mods.contains(Modifier.PROTECTED) ? "protected " : "").append("override "); }
            else {
                head.append(inInterface ? "" : vis);
                if (mods.contains(Modifier.ABSTRACT) && !inInterface) head.append("abstract ");
                else if (!isStatic && !inInterface && !mods.contains(Modifier.FINAL) && !mods.contains(Modifier.PRIVATE) && !owner.getModifiers().getFlags().contains(Modifier.FINAL)) head.append("open ");
            }
            String name = m.getName().toString();
            TypeMirror ret = e.getReturnType();
            String retText = name.equals("toString") && m.getParameters().isEmpty() ? "String" : name.equals("hashCode") && m.getParameters().isEmpty() ? "Int" : type(ret);
            head.append("fun ").append(typeParams(m.getTypeParameters(), p).isEmpty() ? "" : typeParams(m.getTypeParameters(), p) + " ").append(id(name)).append("(").append(parameters(p, m.getParameters())).append(")");
            if (ret.getKind() != TypeKind.VOID) head.append(": ").append(retText);
            if (m.getBody() == null) { line(head.toString()); return; }
            line(head + " {");
            indent++; returnTypes.push(name.equals("toString") && m.getParameters().isEmpty() ? null : ret); lambdaLabels.push("");
            // Java parameters are assignable; Kotlin's are not.
            for (VariableTree v : m.getParameters()) if (assigned(m.getBody(), v.getName())) line("var " + id(v.getName()) + " = " + id(v.getName()));
            statements(child(p, m.getBody()), m.getBody().getStatements());
            lambdaLabels.pop(); returnTypes.pop(); indent--;
            line("}");
        }
        /** True when the body assigns to a simple name (used to shadow reassigned parameters). */
        static boolean assigned(Tree body, Name name) {
            boolean[] found = {false};
            new TreeScanner<Void, Void>() {
                void check(Tree target) { if (target instanceof IdentifierTree && ((IdentifierTree) target).getName().contentEquals(name)) found[0] = true; }
                @Override public Void visitAssignment(AssignmentTree t, Void v) { check(t.getVariable()); return super.visitAssignment(t, v); }
                @Override public Void visitCompoundAssignment(CompoundAssignmentTree t, Void v) { check(t.getVariable()); return super.visitCompoundAssignment(t, v); }
                @Override public Void visitUnary(UnaryTree t, Void v) { if (t.getKind().name().contains("INCREMENT") || t.getKind().name().contains("DECREMENT")) check(t.getExpression()); return super.visitUnary(t, v); }
            }.scan(body, null);
            return found[0];
        }

        // ---------------------------------------------------------- statements
        void statements(TreePath parent, List<? extends StatementTree> list) { for (StatementTree s : list) statement(child(parent, s)); }
        void block(TreePath p) {
            Tree t = p.getLeaf();
            if (t instanceof BlockTree) statements(p, ((BlockTree) t).getStatements()); else statement(p);
        }
        void statement(TreePath p) {
            Tree t = p.getLeaf();
            switch (t.getKind()) {
                case BLOCK: line("run {"); indent++; statements(p, ((BlockTree) t).getStatements()); indent--; line("}"); break;
                case EMPTY_STATEMENT: break;
                case VARIABLE: {
                    VariableTree v = (VariableTree) t; TypeMirror vt = elementOf(p).asType();
                    line("var " + id(v.getName()) + ": " + type(vt) + (v.getInitializer() == null ? "" : " = " + exprAs(child(p, v.getInitializer()), vt)));
                    break;
                }
                case EXPRESSION_STATEMENT: line(statementExpr(child(p, ((ExpressionStatementTree) t).getExpression()))); break;
                case IF: {
                    IfTree i = (IfTree) t;
                    line("if (" + condition(child(p, i.getCondition())) + ") {"); indent++; block(child(p, i.getThenStatement())); indent--;
                    if (i.getElseStatement() != null) { line("} else {"); indent++; block(child(p, i.getElseStatement())); indent--; }
                    line("}"); break;
                }
                case WHILE_LOOP: {
                    WhileLoopTree w = (WhileLoopTree) t;
                    line(label(p) + "while (" + condition(child(p, w.getCondition())) + ") {"); indent++; block(child(p, w.getStatement())); indent--; line("}"); break;
                }
                case DO_WHILE_LOOP: {
                    DoWhileLoopTree w = (DoWhileLoopTree) t;
                    line(label(p) + "do {"); indent++; block(child(p, w.getStatement())); indent--; line("} while (" + condition(child(p, w.getCondition())) + ")"); break;
                }
                case FOR_LOOP: forLoop(p, (ForLoopTree) t); break;
                case ENHANCED_FOR_LOOP: {
                    EnhancedForLoopTree f = (EnhancedForLoopTree) t;
                    TypeMirror vt = elementOf(child(p, f.getVariable())).asType();
                    String name = id(f.getVariable().getName()); boolean reassigned = assigned(f.getStatement(), f.getVariable().getName());
                    line(label(p) + "for (" + (reassigned ? name + "_" : name) + " in " + receiver(child(p, f.getExpression())) + ") {"); indent++;
                    if (reassigned) line("var " + name + ": " + type(vt) + " = " + name + "_");
                    block(child(p, f.getStatement())); indent--; line("}"); break;
                }
                case RETURN: {
                    ReturnTree r = (ReturnTree) t; String label = lambdaLabels.isEmpty() || lambdaLabels.peek().isEmpty() ? "" : "@" + lambdaLabels.peek();
                    if (r.getExpression() == null) { line("return" + label); break; }
                    TypeMirror target = returnTypes.peek();
                    boolean nonNull = !label.isEmpty() && target != null && !isPrimitive(target) && kind(target) == null && !isNullLiteral(r.getExpression());   // lambda results are non-null
                    String value = target == null ? "(" + expr(child(p, r.getExpression())) + ").toString()" : nonNull ? receiver(child(p, r.getExpression())) : exprAs(child(p, r.getExpression()), target);
                    line("return" + label + " " + value); break;
                }
                case THROW: line("throw " + receiver(child(p, ((ThrowTree) t).getExpression()))); break;
                case BREAK: line("break" + (((BreakTree) t).getLabel() == null ? "" : "@" + ((BreakTree) t).getLabel())); break;
                case CONTINUE: line("continue" + (((ContinueTree) t).getLabel() == null ? "" : "@" + ((ContinueTree) t).getLabel())); break;
                case LABELED_STATEMENT: statement(child(p, ((LabeledStatementTree) t).getStatement())); break;
                case TRY: tryStatement(p, (TryTree) t); break;
                case SWITCH: switchStatement(p, (SwitchTree) t); break;
                case SYNCHRONIZED: {
                    SynchronizedTree s = (SynchronizedTree) t;
                    line("synchronized(" + receiver(child(p, s.getExpression())) + ") {"); indent++; statements(child(p, s.getBlock()), s.getBlock().getStatements()); indent--; line("}"); break;
                }
                case CLASS: classDecl(p, false); break;
                default: line(unsupported(t, "statement " + t.getKind()));
            }
        }
        String label(TreePath p) { Tree parent = p.getParentPath().getLeaf(); return parent instanceof LabeledStatementTree ? ((LabeledStatementTree) parent).getLabel() + "@ " : ""; }
        static boolean hasContinue(Tree body) {
            boolean[] found = {false};
            new TreeScanner<Void, Void>() {
                int depth = 0;
                @Override public Void visitContinue(ContinueTree t, Void v) { if (depth == 0 || t.getLabel() != null) found[0] = true; return null; }
                @Override public Void visitForLoop(ForLoopTree t, Void v) { depth++; super.visitForLoop(t, v); depth--; return null; }
                @Override public Void visitEnhancedForLoop(EnhancedForLoopTree t, Void v) { depth++; super.visitEnhancedForLoop(t, v); depth--; return null; }
                @Override public Void visitWhileLoop(WhileLoopTree t, Void v) { depth++; super.visitWhileLoop(t, v); depth--; return null; }
                @Override public Void visitDoWhileLoop(DoWhileLoopTree t, Void v) { depth++; super.visitDoWhileLoop(t, v); depth--; return null; }
                @Override public Void visitLambdaExpression(LambdaExpressionTree t, Void v) { return null; }
                @Override public Void visitClass(ClassTree t, Void v) { return null; }
            }.scan(body, null);
            return found[0];
        }
        void forLoop(TreePath p, ForLoopTree f) {
            line("run {"); indent++;
            for (StatementTree s : f.getInitializer()) statement(child(p, s));
            line(label(p) + "while (" + (f.getCondition() == null ? "true" : condition(child(p, f.getCondition()))) + ") {"); indent++;
            boolean guard = hasContinue(f.getStatement()) && !f.getUpdate().isEmpty();
            if (guard) { line("try {"); indent++; }
            block(child(p, f.getStatement()));
            if (guard) { indent--; line("} finally {"); indent++; }
            for (ExpressionStatementTree u : f.getUpdate()) line(statementExpr(child(child(p, u), u.getExpression())));
            if (guard) { indent--; line("}"); }
            indent--; line("}");
            indent--; line("}");
        }
        void tryStatement(TreePath p, TryTree t) {
            int resources = 0;
            for (Tree r : t.getResources()) {
                TreePath rp = child(p, r);
                if (r instanceof VariableTree) {
                    VariableTree v = (VariableTree) r;
                    line("(" + expr(child(rp, v.getInitializer())) + ").use { " + id(v.getName()) + " ->");
                } else line("(" + expr(rp) + ").use {");
                indent++; resources++;
            }
            boolean plain = t.getCatches().isEmpty() && t.getFinallyBlock() == null;
            if (!plain) { line("try {"); indent++; }
            statements(child(p, t.getBlock()), t.getBlock().getStatements());
            if (!plain) indent--;
            for (CatchTree c : t.getCatches()) {
                TreePath cp = child(p, c); Tree declared = c.getParameter().getType();
                List<? extends Tree> alternatives = declared instanceof UnionTypeTree ? ((UnionTypeTree) declared).getTypeAlternatives() : List.of(declared);
                for (Tree alt : alternatives) {
                    line("} catch (" + id(c.getParameter().getName()) + ": " + typeNN(typeOf(child(child(cp, c.getParameter()), alt))) + ") {");
                    indent++; statements(child(cp, c.getBlock()), c.getBlock().getStatements()); indent--;
                }
            }
            if (t.getFinallyBlock() != null) { line("} finally {"); indent++; statements(child(p, t.getFinallyBlock()), t.getFinallyBlock().getStatements()); indent--; }
            if (!plain) line("}");
            for (int i = 0; i < resources; i++) { indent--; line("}"); }
        }
        void switchStatement(TreePath p, SwitchTree s) {
            // Supported shape: every case group ends with break/return/throw/continue (no fall-through into a non-empty group).
            line("when (" + expr(child(p, s.getExpression())) + ") {"); indent++;
            List<String> pending = new ArrayList<>();
            for (CaseTree c : s.getCases()) {
                TreePath cp = child(p, c);
                if (c.getExpressions().isEmpty()) pending.add("else"); else for (ExpressionTree e : c.getExpressions()) pending.add(expr(child(cp, e)));
                List<? extends StatementTree> body = c.getStatements();
                if (body == null || body.isEmpty()) continue;
                StatementTree last = body.get(body.size() - 1);
                boolean endsWithBreak = last instanceof BreakTree && ((BreakTree) last).getLabel() == null;
                if (!(endsWithBreak || last instanceof ReturnTree || last instanceof ThrowTree || last instanceof ContinueTree) && c != s.getCases().get(s.getCases().size() - 1)) unsupported(c, "switch fall-through");
                line((pending.contains("else") ? "else" : String.join(", ", pending)) + " -> {"); pending.clear(); indent++;
                statements(cp, endsWithBreak ? body.subList(0, body.size() - 1) : body);
                indent--; line("}");
            }
            indent--; line("}");
        }

        // ---------------------------------------------------------- expressions
        static boolean isLiteralLike(Tree t) {
            while (t instanceof ParenthesizedTree) t = ((ParenthesizedTree) t).getExpression();
            if (t instanceof LiteralTree) return ((LiteralTree) t).getValue() != null;
            if (t instanceof NewClassTree || t instanceof NewArrayTree) return true;
            if (t instanceof IdentifierTree) { String n = ((IdentifierTree) t).getName().toString(); return n.equals("this") || n.equals("super"); }
            return false;
        }
        static boolean isNullLiteral(Tree t) {
            while (t instanceof ParenthesizedTree) t = ((ParenthesizedTree) t).getExpression();
            return t instanceof LiteralTree && ((LiteralTree) t).getValue() == null;
        }
        static boolean simple(String s) { return s.matches("[\\w.`]+(\\(\\))?|\"[^\"]*\"") || (s.startsWith("(") && s.endsWith(")") && balanced(s)); }
        static boolean balanced(String s) { int d = 0; for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c == '(') d++; else if (c == ')') { d--; if (d == 0 && i < s.length() - 1) return false; } } return d == 0; }
        static String paren(String s) { return simple(s) ? s : "(" + s + ")"; }

        /** Expression used as a receiver or where Kotlin needs a non-null value. */
        String receiver(TreePath p) {
            Tree t = p.getLeaf(); String s = expr(p); TypeMirror type = typeOf(p);
            if (isPrimitive(type) || isLiteralLike(t)) return paren(s);
            Element e = elementOf(p);
            if (e != null && (e.getKind().isClass() || e.getKind().isInterface() || e.getKind() == ElementKind.PACKAGE) && (t instanceof IdentifierTree || t instanceof MemberSelectTree)) return s;   // a type name
            return paren(s) + "!!";
        }
        /** Boolean condition: unbox if needed. */
        String condition(TreePath p) { return isPrimitive(typeOf(p)) ? expr(p) : paren(expr(p)) + "!!"; }

        /** Expression converted to the given Java type (unboxing and numeric widening made explicit). */
        String exprAs(TreePath p, TypeMirror target) {
            Tree t = p.getLeaf(); TypeMirror source = typeOf(p); String s = expr(p);
            if (target == null || source == null) return s;
            String targetKind = kind(target), sourceKind = kind(source);
            if (isNullLiteral(t)) return s;
            if (targetKind != null && sourceKind != null) {
                if (isPrimitive(target) && !isPrimitive(source)) s = paren(s) + "!!";
                if (!targetKind.equals(sourceKind)) s = convert(t, s, sourceKind, targetKind);
                return s;
            }
            if (isPrimitive(target) && !isPrimitive(source)) return "(" + paren(s) + " as " + typeNN(target) + ")";      // e.g. Object -> int
            return s;
        }
        static String convert(Tree t, String s, String from, String to) {
            while (t instanceof ParenthesizedTree) t = ((ParenthesizedTree) t).getExpression();
            if (t instanceof LiteralTree && from.equals("Int") && ((LiteralTree) t).getValue() instanceof Integer) {
                int v = (Integer) ((LiteralTree) t).getValue();
                if (to.equals("Long")) return v + "L"; if (to.equals("Double")) return v + ".0"; if (to.equals("Float")) return v + "f";
            }
            if (from.equals("Boolean") || to.equals("Boolean")) return s;
            if (from.equals("Char")) return to.equals("Int") ? paren(s) + ".code" : paren(s) + ".code.to" + to + "()";
            if (to.equals("Char")) return paren(s) + ".toInt().toChar()";
            return paren(s) + ".to" + to + "()";
        }

        /** Expression statement: assignments and increments are statements in Kotlin. */
        String statementExpr(TreePath p) {
            Tree t = p.getLeaf();
            if (t instanceof AssignmentTree) { AssignmentTree a = (AssignmentTree) t; String element = elementAssignment(p, a); if (element != null) return element;
                return target(child(p, a.getVariable())) + " = " + exprAs(child(p, a.getExpression()), typeOf(child(p, a.getVariable()))); }
            if (t instanceof CompoundAssignmentTree) return compound(p, (CompoundAssignmentTree) t);
            if (t instanceof ParenthesizedTree) return statementExpr(child(p, ((ParenthesizedTree) t).getExpression()));
            return expr(p);
        }
        /**
         * Storing into an array of references. Array components are non-null in the Kotlin output, so a stored
         * value is asserted non-null, and storing null goes through a view of the array with nullable components.
         */
        String elementAssignment(TreePath p, AssignmentTree a) {
            Tree variable = a.getVariable();
            while (variable instanceof ParenthesizedTree) variable = ((ParenthesizedTree) variable).getExpression();
            if (!(variable instanceof ArrayAccessTree)) return null;
            TreePath vp = child(p, a.getVariable()); while (vp.getLeaf() != variable) vp = child(vp, ((ParenthesizedTree) vp.getLeaf()).getExpression());
            TypeMirror component = typeOf(vp);
            if (component == null || isPrimitive(component)) return null;
            ArrayAccessTree access = (ArrayAccessTree) variable;
            String array = receiver(child(vp, access.getExpression())), index = exprAs(child(vp, access.getIndex()), types.getPrimitiveType(TypeKind.INT));
            TreePath ep = child(p, a.getExpression());
            if (isNullLiteral(ep.getLeaf())) return "(" + array + " as Array<" + typeNN(component) + "?>)[" + index + "] = null";
            return array + "[" + index + "] = " + receiver(ep);
        }
        /** Assignment target: no "!!" on the variable itself, only on its qualifier. */
        String target(TreePath p) {
            Tree t = p.getLeaf();
            if (t instanceof ParenthesizedTree) return target(child(p, ((ParenthesizedTree) t).getExpression()));
            return expr(p);
        }
        static final Map<Tree.Kind, String> COMPOUND = new EnumMap<>(Tree.Kind.class);
        static final Map<Tree.Kind, String> BINARY = new EnumMap<>(Tree.Kind.class);
        static {
            COMPOUND.put(Tree.Kind.PLUS_ASSIGNMENT, "+"); COMPOUND.put(Tree.Kind.MINUS_ASSIGNMENT, "-"); COMPOUND.put(Tree.Kind.MULTIPLY_ASSIGNMENT, "*"); COMPOUND.put(Tree.Kind.DIVIDE_ASSIGNMENT, "/");
            COMPOUND.put(Tree.Kind.REMAINDER_ASSIGNMENT, "%"); COMPOUND.put(Tree.Kind.AND_ASSIGNMENT, "and"); COMPOUND.put(Tree.Kind.OR_ASSIGNMENT, "or"); COMPOUND.put(Tree.Kind.XOR_ASSIGNMENT, "xor");
            COMPOUND.put(Tree.Kind.LEFT_SHIFT_ASSIGNMENT, "shl"); COMPOUND.put(Tree.Kind.RIGHT_SHIFT_ASSIGNMENT, "shr"); COMPOUND.put(Tree.Kind.UNSIGNED_RIGHT_SHIFT_ASSIGNMENT, "ushr");
            BINARY.put(Tree.Kind.PLUS, "+"); BINARY.put(Tree.Kind.MINUS, "-"); BINARY.put(Tree.Kind.MULTIPLY, "*"); BINARY.put(Tree.Kind.DIVIDE, "/"); BINARY.put(Tree.Kind.REMAINDER, "%");
            BINARY.put(Tree.Kind.LESS_THAN, "<"); BINARY.put(Tree.Kind.GREATER_THAN, ">"); BINARY.put(Tree.Kind.LESS_THAN_EQUAL, "<="); BINARY.put(Tree.Kind.GREATER_THAN_EQUAL, ">=");
            BINARY.put(Tree.Kind.CONDITIONAL_AND, "&&"); BINARY.put(Tree.Kind.CONDITIONAL_OR, "||"); BINARY.put(Tree.Kind.AND, "and"); BINARY.put(Tree.Kind.OR, "or"); BINARY.put(Tree.Kind.XOR, "xor");
            BINARY.put(Tree.Kind.LEFT_SHIFT, "shl"); BINARY.put(Tree.Kind.RIGHT_SHIFT, "shr"); BINARY.put(Tree.Kind.UNSIGNED_RIGHT_SHIFT, "ushr");
        }
        String compound(TreePath p, CompoundAssignmentTree a) {
            TreePath vp = child(p, a.getVariable()), ep = child(p, a.getExpression());
            TypeMirror vt = typeOf(vp), et = typeOf(ep); String op = COMPOUND.get(a.getKind()); String lhs = target(vp);
            if (isString(vt)) return lhs + " = " + lhs + " + " + paren(expr(ep));
            String vk = kind(vt), ek = kind(et);
            String left = isPrimitive(vt) ? lhs : paren(lhs) + "!!";
            String right = isPrimitive(et) ? paren(expr(ep)) : paren(expr(ep)) + "!!";
            if (vk != null && ek != null && vk.equals("Char")) return lhs + " = (" + left + ".code " + op + " " + right + ").toChar()";
            boolean shift = op.startsWith("sh") || op.equals("ushr");
            if (vk != null && ek != null && !vk.equals(ek) && !shift) {
                if (rank(ek) < rank(vk)) return lhs + " = " + left + " " + op + " " + convert(a.getExpression(), right, ek, vk);
                return lhs + " = (" + left + " " + op + " " + right + ").to" + vk + "()";      // Java's implicit narrowing cast
            }
            return lhs + " = " + left + " " + op + " " + right;
        }

        String expr(TreePath p) {
            Tree t = p.getLeaf();
            switch (t.getKind()) {
                case PARENTHESIZED: return "(" + expr(child(p, ((ParenthesizedTree) t).getExpression())) + ")";
                case IDENTIFIER: return identifier(p, (IdentifierTree) t);
                case MEMBER_SELECT: return memberSelect(p, (MemberSelectTree) t);
                case METHOD_INVOCATION: return invocation(p, (MethodInvocationTree) t);
                case NEW_CLASS: return newClass(p, (NewClassTree) t);
                case NEW_ARRAY: return newArray(p, (NewArrayTree) t);
                case ARRAY_ACCESS: { ArrayAccessTree a = (ArrayAccessTree) t; return receiver(child(p, a.getExpression())) + "[" + exprAs(child(p, a.getIndex()), types.getPrimitiveType(TypeKind.INT)) + "]"; }
                case ASSIGNMENT: { AssignmentTree a = (AssignmentTree) t; TreePath vp = child(p, a.getVariable());
                    if (elementAssignment(p, a) != null) return "run { " + elementAssignment(p, a) + "; " + expr(vp) + " }";
                    return "(" + exprAs(child(p, a.getExpression()), typeOf(vp)) + ").also { " + target(vp) + " = it }"; }
                case CONDITIONAL_EXPRESSION: { ConditionalExpressionTree c = (ConditionalExpressionTree) t; TypeMirror type = typeOf(p);
                    return "(if (" + condition(child(p, c.getCondition())) + ") " + exprAs(child(p, c.getTrueExpression()), type) + " else " + exprAs(child(p, c.getFalseExpression()), type) + ")"; }
                case INSTANCE_OF: { InstanceOfTree i = (InstanceOfTree) t; TypeMirror tested = typeOf(child(p, i.getType()));
                    String testedName = tested != null && tested.getKind() == TypeKind.ARRAY && !((ArrayType) tested).getComponentType().getKind().isPrimitive() ? "Array<*>" : typeNN(tested);
                    return "(" + expr(child(p, i.getExpression())) + " is " + testedName + ")"; }
                case TYPE_CAST: return cast(p, (TypeCastTree) t);
                case LAMBDA_EXPRESSION: return lambda(p, (LambdaExpressionTree) t);
                case MEMBER_REFERENCE: return memberReference(p, (MemberReferenceTree) t);
                case INT_LITERAL: case LONG_LITERAL: case FLOAT_LITERAL: case DOUBLE_LITERAL: case BOOLEAN_LITERAL: case CHAR_LITERAL: case STRING_LITERAL: case NULL_LITERAL: return literal((LiteralTree) t);
                case POSTFIX_INCREMENT: return target(child(p, ((UnaryTree) t).getExpression())) + "++";
                case POSTFIX_DECREMENT: return target(child(p, ((UnaryTree) t).getExpression())) + "--";
                case PREFIX_INCREMENT: return "++" + target(child(p, ((UnaryTree) t).getExpression()));
                case PREFIX_DECREMENT: return "--" + target(child(p, ((UnaryTree) t).getExpression()));
                case UNARY_MINUS: return "-" + operand(child(p, ((UnaryTree) t).getExpression()));
                case UNARY_PLUS: return operand(child(p, ((UnaryTree) t).getExpression()));
                case LOGICAL_COMPLEMENT: return "!" + paren(condition(child(p, ((UnaryTree) t).getExpression())));
                case BITWISE_COMPLEMENT: return operand(child(p, ((UnaryTree) t).getExpression())) + ".inv()";
                case EQUAL_TO: case NOT_EQUAL_TO: return equality(p, (BinaryTree) t);
                default:
                    if (t instanceof BinaryTree) return binary(p, (BinaryTree) t);
                    if (t instanceof CompoundAssignmentTree) { CompoundAssignmentTree a = (CompoundAssignmentTree) t; return "run { " + compound(p, a) + "; " + target(child(p, a.getVariable())) + " }"; }
                    return unsupported(t, "expression " + t.getKind());
            }
        }
        /** Numeric operand: unboxed. */
        String operand(TreePath p) { return isPrimitive(typeOf(p)) ? paren(expr(p)) : paren(expr(p)) + "!!"; }
        String literal(LiteralTree l) {
            Object v = l.getValue();
            if (v == null) return "null";
            if (v instanceof String) return quote((String) v);
            if (v instanceof Character) { char c = (Character) v; return c == '\'' ? "'\\''" : c == '\\' ? "'\\\\'" : c == '\n' ? "'\\n'" : c == '\t' ? "'\\t'" : c == '\r' ? "'\\r'" : c == '$' ? "'$'" : (c < 0x20 || c == 0x7f || (c >= 0x2000 && c <= 0x206f)) ? String.format("'\\u%04x'", (int) c) : "'" + c + "'"; }
            if (v instanceof Long) return v + "L";
            if (v instanceof Float) return v + "f";
            if (v instanceof Double) { double d = (Double) v; if (Double.isNaN(d)) return "Double.NaN"; if (Double.isInfinite(d)) return d > 0 ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY"; String s = v.toString(); return s.contains("E") ? s.replace("E", "e") : s; }
            return v.toString();
        }
        static String quote(String s) {
            StringBuilder b = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '\\': b.append("\\\\"); break; case '"': b.append("\\\""); break; case '$': b.append("\\$"); break;
                    case '\n': b.append("\\n"); break; case '\t': b.append("\\t"); break; case '\r': b.append("\\r"); break; case '\b': b.append("\\b"); break;
                    default: if (c < 0x20 || c == 0x7f || (c >= 0x2000 && c <= 0x206f) || c == 0xfeff) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
                }
            }
            return b.append('"').toString();
        }
        String identifier(TreePath p, IdentifierTree t) {
            Element e = elementOf(p); String name = t.getName().toString();
            if (name.equals("this") || name.equals("super")) return name;
            if (e != null && (e.getKind().isClass() || e.getKind().isInterface())) return typeNN(e.asType()).replaceAll("<.*>$", "");
            return id(name);
        }
        String memberSelect(TreePath p, MemberSelectTree t) {
            Element e = elementOf(p); String name = t.getIdentifier().toString(); TreePath qp = child(p, t.getExpression());
            if (e != null && (e.getKind().isClass() || e.getKind().isInterface())) return typeNN(e.asType()).replaceAll("<.*>$", "");
            if (name.equals("this")) return "this@" + t.getExpression();          // Outer.this
            if (name.equals("class")) return typeNN(typeOf(qp)) + "::class";
            TypeMirror qt = typeOf(qp);
            if (name.equals("length") && qt != null && qt.getKind() == TypeKind.ARRAY) return receiver(qp) + ".size";
            if (e != null && e.getKind() == ElementKind.FIELD && e.getModifiers().contains(Modifier.STATIC)) {
                TypeElement owner = (TypeElement) e.getEnclosingElement(); String q = owner.getQualifiedName().toString();
                if (q.equals("java.lang.Integer") || q.equals("java.lang.Long") || q.equals("java.lang.Double") || q.equals("java.lang.Float") || q.equals("java.lang.Short") || q.equals("java.lang.Byte"))
                    return boxedKind(owner.asType()) + "." + (name.equals("MIN_VALUE") && (q.endsWith("Double") || q.endsWith("Float")) ? "MIN_VALUE" : name);
                return qualified(owner) + "." + id(name);
            }
            return receiver(qp) + "." + id(name);
        }
        String cast(TreePath p, TypeCastTree t) {
            TreePath ep = child(p, t.getExpression()); TypeMirror target = typeOf(child(p, t.getType())), source = typeOf(ep);
            String tk = kind(target), sk = kind(source);
            if (isPrimitive(target) && sk != null) { String s = isPrimitive(source) ? expr(ep) : paren(expr(ep)) + "!!"; return tk.equals(sk) ? s : convert(t.getExpression(), s, sk, tk); }
            if (isPrimitive(target)) return "(" + paren(expr(ep)) + " as " + typeNN(target) + ")";
            if (tk != null && sk != null && !tk.equals(sk)) return convert(t.getExpression(), expr(ep), sk, tk);
            return "(" + paren(expr(ep)) + " as " + type(target) + ")";
        }
        String equality(TreePath p, BinaryTree b) {
            TreePath lp = child(p, b.getLeftOperand()), rp = child(p, b.getRightOperand());
            TypeMirror lt = typeOf(lp), rt = typeOf(rp); boolean eq = b.getKind() == Tree.Kind.EQUAL_TO;
            if (isNullLiteral(b.getLeftOperand()) || isNullLiteral(b.getRightOperand())) return expr(lp) + (eq ? " == " : " != ") + expr(rp);
            String lk = kind(lt), rk = kind(rt);
            if (lk != null && rk != null && (isPrimitive(lt) || isPrimitive(rt))) {
                String l = operand(lp), r = operand(rp);
                if (!lk.equals(rk)) { if (rank(lk) < rank(rk)) l = convert(b.getLeftOperand(), l, lk, rk); else r = convert(b.getRightOperand(), r, rk, lk); }
                return l + (eq ? " == " : " != ") + r;
            }
            if (isString(lt) || isString(rt) || (lk != null && rk != null)) return expr(lp) + (eq ? " == " : " != ") + expr(rp);       // value comparison is what the source means
            return expr(lp) + (eq ? " === " : " !== ") + expr(rp);
        }
        String binary(TreePath p, BinaryTree b) {
            TreePath lp = child(p, b.getLeftOperand()), rp = child(p, b.getRightOperand());
            TypeMirror lt = typeOf(lp), rt = typeOf(rp), type = typeOf(p); String op = BINARY.get(b.getKind());
            if (op == null) return unsupported(b, "operator " + b.getKind());
            if (b.getKind() == Tree.Kind.PLUS && isString(type)) {
                String l = isString(lt) ? expr(lp) : (isPrimitive(lt) ? paren(expr(lp)) + ".toString()" : "(" + expr(lp) + ").toString()");
                String r = expr(rp);
                if (b.getRightOperand() instanceof BinaryTree || b.getRightOperand() instanceof ConditionalExpressionTree) r = paren(r);
                return l + " + " + r;
            }
            if (op.equals("&&") || op.equals("||")) return condition(lp) + " " + op + " " + condition(rp);
            String lk = kind(lt), rk = kind(rt); String l = operand(lp), r = operand(rp);
            if ("Boolean".equals(lk)) return l + " " + op + " " + r;
            // Java promotes char/byte/short to int in arithmetic.
            if ("Char".equals(lk)) l = l + ".code"; else if ("Byte".equals(lk) || "Short".equals(lk)) l = l + ".toInt()";
            if ("Char".equals(rk)) r = r + ".code"; else if ("Byte".equals(rk) || "Short".equals(rk)) r = r + ".toInt()";
            boolean shift = op.startsWith("sh") || op.equals("ushr");
            if (shift && "Long".equals(rk)) r = r + ".toInt()";
            if ((op.equals("and") || op.equals("or") || op.equals("xor")) && lk != null && rk != null && !lk.equals(rk)) {      // bitwise ops need equal types
                String lk2 = lk.equals("Long") ? "Long" : "Int", rk2 = rk.equals("Long") ? "Long" : "Int";
                if (!lk2.equals(rk2)) { if (lk2.equals("Int")) l = l + ".toLong()"; else r = r + ".toLong()"; }
            }
            return l + " " + op + " " + r;
        }

        // ---------------------------------------------------------- calls
        /** Formats arguments for a resolved method, converting each to its parameter type. */
        String arguments(TreePath callPath, ExecutableElement m, List<? extends ExpressionTree> args) {
            List<String> parts = argumentList(callPath, m, args);
            return String.join(", ", parts);
        }
        List<String> argumentList(TreePath callPath, ExecutableElement m, List<? extends ExpressionTree> args) {
            List<String> parts = new ArrayList<>();
            if (m == null) { for (ExpressionTree a : args) parts.add(expr(child(callPath, a))); return parts; }
            List<? extends VariableElement> params = m.getParameters(); Element owner = m.getEnclosingElement();
            boolean source = isSource(owner), kotlin = isKotlinClass(owner);
            for (int i = 0; i < args.size(); i++) {
                TreePath ap = child(callPath, args.get(i)); TypeMirror at = typeOf(ap);
                VariableElement param; TypeMirror pt; boolean spread = false;
                if (m.isVarArgs() && i >= params.size() - 1) {
                    param = params.get(params.size() - 1); TypeMirror arrayType = param.asType();
                    if (args.size() == params.size() && at != null && types.isAssignable(types.erasure(at), types.erasure(arrayType))) { pt = arrayType; spread = true; }
                    else pt = ((ArrayType) arrayType).getComponentType();
                } else { param = params.get(i); pt = param.asType(); }
                String s;
                if (isNullLiteral(args.get(i))) s = "null";
                else if (isPrimitive(pt) || (kind(pt) != null && kind(at) != null)) s = exprAs(ap, pt);
                else if (source || isPrimitive(at) || isLiteralLike(args.get(i))) s = expr(ap);
                else if (kotlin) s = annotated(param, "Nullable") ? expr(ap) : receiver(ap);
                else {
                    // JDK method: parameters typed Object accept null, everything else is treated as non-null.
                    // JDK method: only a few take "any object, possibly null"; everything else is treated as non-null.
                    boolean acceptsNull = isObject(pt) && owner instanceof TypeElement && NULL_TOLERANT.contains(((TypeElement) owner).getQualifiedName().toString());
                    s = acceptsNull ? expr(ap) : receiver(ap);
                }
                parts.add(spread ? "*" + paren(s) + (s.endsWith("!!") ? "" : "!!") : s);
            }
            return parts;
        }
        static String fill(String template, String receiver, List<String> args) {
            String s = template.replace("$r", receiver);
            for (int i = args.size() - 1; i >= 0; i--) s = s.replace("$" + i, args.get(i));
            return s;
        }
        static String propertyName(String method) {
            if (method.startsWith("is") && method.length() > 2 && Character.isUpperCase(method.charAt(2))) return method;
            String rest = method.substring(3);
            // Kotlin keeps a leading acronym upper-case only when the whole prefix is upper-case (getURL -> url is rare here).
            return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
        }
        String invocation(TreePath p, MethodInvocationTree t) {
            Element element = elementOf(p);
            ExecutableElement m = element instanceof ExecutableElement ? (ExecutableElement) element : null;
            ExpressionTree select = t.getMethodSelect();
            List<String> args = argumentList(p, m, t.getArguments());
            String name = select instanceof MemberSelectTree ? ((MemberSelectTree) select).getIdentifier().toString() : select.toString();
            String typeArgs = "";
            if (select instanceof MemberSelectTree && name.equals("clone") && args.isEmpty()) {
                TreePath qp = child(child(p, select), ((MemberSelectTree) select).getExpression());
                TypeMirror qt = typeOf(qp);
                if (qt != null && qt.getKind() == TypeKind.ARRAY) return receiver(qp) + ".copyOf()";      // arrays have no clone() outside the JVM
            }
            if (m == null) return (select instanceof MemberSelectTree ? receiver(child(child(p, select), ((MemberSelectTree) select).getExpression())) + "." : "") + id(name) + "(" + String.join(", ", args) + ")";
            TypeElement owner = (TypeElement) m.getEnclosingElement(); boolean isStatic = m.getModifiers().contains(Modifier.STATIC);
            String receiver;
            if (select instanceof MemberSelectTree) {
                TreePath qp = child(child(p, select), ((MemberSelectTree) select).getExpression());
                receiver = isStatic ? staticOwner(owner) : receiver(qp);
            } else receiver = null;
            if (isStatic && receiver == null && !isSource(owner)) receiver = staticOwner(owner);     // static import or inherited static
            String template = memberTemplate(m);
            if (template != null) return fill(template, receiver == null ? "this" : receiver, args);
            String prefix = receiver == null ? "" : receiver + ".";
            if (!m.getTypeParameters().isEmpty() && m.getReturnType().getKind() == TypeKind.TYPEVAR) {
                // A type parameter that only appears as the result cannot be inferred by Kotlin from a bare call.
                boolean inParameters = false;
                for (VariableElement v : m.getParameters()) if (v.asType().toString().contains(m.getReturnType().toString())) inParameters = true;
                TypeMirror inferred = typeOf(p);
                if (!inParameters && inferred != null && inferred.getKind() == TypeKind.DECLARED) typeArgs = "<" + typeNN(inferred) + ">";
            }
            if (isKotlinClass(owner) && !isStatic) {
                String propertyPrefix = receiver == null ? implicitThis(p, owner) + "." : prefix;
                boolean getter = args.isEmpty() && ((name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) || (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2)) && m.getReturnType().getKind() == TypeKind.BOOLEAN));
                if (getter && !keepCall(owner, m)) return propertyPrefix + id(propertyName(name));
                if (args.size() == 1 && name.startsWith("set") && name.length() > 3 && Character.isUpperCase(name.charAt(3)) && m.getReturnType().getKind() == TypeKind.VOID) {
                    String property = keepCall(owner, m) ? null : hasGetter(owner, "get" + name.substring(3)) ? propertyName("get" + name.substring(3)) : hasGetter(owner, "is" + name.substring(3)) ? "is" + name.substring(3) : null;
                    if (property != null) return "run { " + propertyPrefix + id(property) + " = " + args.get(0) + " }";
                }
            }
            return prefix + id(name) + typeArgs + "(" + String.join(", ", args) + ")";
        }
        /** "this", or "this@Outer" when the member belongs to an enclosing class rather than the innermost one. */
        String implicitThis(TreePath p, TypeElement owner) {
            boolean innermost = true;
            for (TreePath q = p; q != null; q = q.getParentPath()) {
                if (!(q.getLeaf() instanceof ClassTree)) continue;
                Element c = trees.getElement(q);
                if (c instanceof TypeElement && types.isSubtype(types.erasure(c.asType()), types.erasure(owner.asType()))) return innermost ? "this" : "this@" + c.getSimpleName();
                innermost = false;
            }
            return "this";
        }
        static boolean hasGetter(TypeElement owner, String getter) {
            for (Element e : elements.getAllMembers(owner)) if (e instanceof ExecutableElement && ((ExecutableElement) e).getParameters().isEmpty() && e.getSimpleName().contentEquals(getter)
                    && !annotated(e, "Fun") && (!getter.startsWith("is") || ((ExecutableElement) e).getReturnType().getKind() == TypeKind.BOOLEAN)) return true;
            return false;
        }
        /** True when the method, or the one it overrides somewhere up the hierarchy, is marked @Fun (a real function that only looks like an accessor). */
        static boolean keepCall(TypeElement owner, ExecutableElement m) {
            if (annotated(m, "Fun")) return true;
            java.util.ArrayDeque<TypeMirror> queue = new java.util.ArrayDeque<>(types.directSupertypes(owner.asType()));
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            while (!queue.isEmpty()) {
                TypeMirror t = queue.poll(); Element te = types.asElement(t);
                if (!(te instanceof TypeElement) || !seen.add(te.toString())) continue;
                for (Element e : te.getEnclosedElements()) if (e instanceof ExecutableElement && e.getSimpleName().contentEquals(m.getSimpleName())
                        && ((ExecutableElement) e).getParameters().size() == m.getParameters().size() && annotated(e, "Fun")) return true;
                queue.addAll(types.directSupertypes(t));
            }
            return false;
        }
        /** Receiver text for a static member of the given class. */
        static String staticOwner(TypeElement owner) {
            String q = owner.getQualifiedName().toString();
            String boxed = boxedKind(owner.asType()); if (boxed != null && !q.equals("java.lang.Character")) return boxed;
            if (q.equals("java.lang.Character")) return "compat.util.Character";
            if (q.equals("java.lang.String")) return "String";
            if (q.equals("java.util.Comparator")) return "compat.j.Comparators";
            return qualified(owner);
        }
        String newClass(TreePath p, NewClassTree t) {
            Element e = elementOf(p); ExecutableElement constructor = e instanceof ExecutableElement ? (ExecutableElement) e : null;
            TypeMirror type = typeOf(p); String args = arguments(p, constructor, t.getArguments());
            if (t.getClassBody() != null) {
                TypeElement anonymous = (TypeElement) elementOf(child(p, t.getClassBody()));
                TypeMirror superType = anonymous.getSuperclass(); boolean iface = !anonymous.getInterfaces().isEmpty();
                String header = iface ? typeNN(anonymous.getInterfaces().get(0)) : typeNN(superType) + "(" + args + ")";
                StringBuilder saved = new StringBuilder(out); int savedIndent = indent; out.setLength(0);
                out.append("object : ").append(header).append(" {\n"); indent = savedIndent + 1;
                lambdaLabels.push(""); classBody(child(p, t.getClassBody()), t.getClassBody(), false); lambdaLabels.pop();
                indent = savedIndent; for (int i = 0; i < indent; i++) out.append("    "); out.append("}");
                String text = out.toString(); out.setLength(0); out.append(saved);
                return text;
            }
            String name = typeNN(type);
            if (type.getKind() == TypeKind.DECLARED) {
                TypeElement created = (TypeElement) ((DeclaredType) type).asElement();
                // An inner (non-static) class is created through the enclosing instance, which is implicit here.
                if (created.getNestingKind() == NestingKind.MEMBER && !created.getModifiers().contains(Modifier.STATIC) && created.getKind() == ElementKind.CLASS && isSource(created) && t.getEnclosingExpression() == null) name = created.getSimpleName().toString();
            }
            if (t.getEnclosingExpression() != null) return receiver(child(p, t.getEnclosingExpression())) + "." + ((TypeElement) ((DeclaredType) type).asElement()).getSimpleName() + "(" + args + ")";
            return name + "(" + args + ")";
        }
        String newArray(TreePath p, NewArrayTree t) {
            ArrayType type = (ArrayType) typeOf(p); TypeMirror component = type.getComponentType();
            if (t.getInitializers() != null) {
                String items = t.getInitializers().stream().map(i -> exprAs(child(p, i), component)).collect(Collectors.joining(", "));
                if (component.getKind().isPrimitive()) return primitiveName(component).toLowerCase(Locale.ROOT).replace("int", "int") + "ArrayOf(" + items + ")";
                if (t.getInitializers().stream().anyMatch(i -> isNullLiteral(i)))     // slots that start empty
                    return "(arrayOf<" + typeNN(component) + "?>(" + t.getInitializers().stream().map(i -> expr(child(p, i))).collect(Collectors.joining(", ")) + ") as Array<" + typeNN(component) + ">)";
                return "arrayOf<" + typeNN(component) + ">(" + t.getInitializers().stream().map(i -> isPrimitive(typeOf(child(p, i))) || isLiteralLike(i) ? exprAs(child(p, i), component) : receiver(child(p, i))).collect(Collectors.joining(", ")) + ")";
            }
            String size = exprAs(child(p, t.getDimensions().get(0)), types.getPrimitiveType(TypeKind.INT));
            if (t.getDimensions().size() > 1) return unsupported(t, "multi-dimensional array");
            if (component.getKind().isPrimitive()) return primitiveName(component) + "Array(" + size + ")";
            return "(arrayOfNulls<" + typeNN(component) + ">(" + size + ") as Array<" + typeNN(component) + ">)";
        }
        /** The single abstract method of a functional interface type. */
        static ExecutableElement functionalMethod(TypeMirror type) {
            if (type == null || type.getKind() != TypeKind.DECLARED) return null;
            for (Element e : elements.getAllMembers((TypeElement) ((DeclaredType) type).asElement()))
                if (e instanceof ExecutableElement && e.getModifiers().contains(Modifier.ABSTRACT) && !(e.getEnclosingElement().toString().equals("java.lang.Object"))
                    && !List.of("equals", "hashCode", "toString").contains(e.getSimpleName().toString())) return (ExecutableElement) e;
            return null;
        }
        String lambda(TreePath p, LambdaExpressionTree t) {
            TypeMirror type = typeOf(p); ExecutableElement sam = functionalMethod(type);
            String label = "l" + (++labels);
            String params = t.getParameters().stream().map(v -> id(v.getName())).collect(Collectors.joining(", "));
            TypeMirror ret = sam == null ? null : ((ExecutableType) types.asMemberOf((DeclaredType) type, sam)).getReturnType();
            StringBuilder saved = new StringBuilder(out); int savedIndent = indent; out.setLength(0);
            String head = typeNN(type) + " " + label + "@{" + (params.isEmpty() ? "" : " " + params + " ->");
            if (t.getBodyKind() == LambdaExpressionTree.BodyKind.EXPRESSION) {
                TreePath bp = child(p, t.getBody());
                String body = ret == null || ret.getKind() == TypeKind.VOID ? statementExpr(bp)
                    : (!isPrimitive(ret) && kind(ret) == null && !isNullLiteral(t.getBody()) ? receiver(bp) : exprAs(bp, ret));
                out.setLength(0); out.append(saved);
                return head + " " + body + " }";
            }
            out.append(head).append('\n'); indent = savedIndent + 1;
            lambdaLabels.push(label); returnTypes.push(ret == null ? types.getNoType(TypeKind.VOID) : ret);
            statements(child(p, t.getBody()), ((BlockTree) t.getBody()).getStatements());
            returnTypes.pop(); lambdaLabels.pop();
            indent = savedIndent; for (int i = 0; i < indent; i++) out.append("    "); out.append("}");
            String text = out.toString(); out.setLength(0); out.append(saved);
            return text;
        }
        String memberReference(TreePath p, MemberReferenceTree t) {
            TypeMirror type = typeOf(p); ExecutableElement sam = functionalMethod(type); Element target = elementOf(p);
            if (sam == null || !(target instanceof ExecutableElement)) return unsupported(t, "method reference");
            ExecutableElement m = (ExecutableElement) target; int n = sam.getParameters().size();
            List<String> names = new ArrayList<>(); for (int i = 0; i < n; i++) names.add("a" + i);
            TreePath qp = child(p, t.getQualifierExpression()); Element q = elementOf(qp);
            boolean qualifierIsType = q != null && (q.getKind().isClass() || q.getKind().isInterface());
            String call;
            if (t.getMode() == MemberReferenceTree.ReferenceMode.NEW) call = typeNN(typeOf(qp)) + "(" + String.join(", ", names) + ")";
            else if (m.getModifiers().contains(Modifier.STATIC)) call = callText(m, staticOwner((TypeElement) m.getEnclosingElement()), names);
            else if (qualifierIsType) call = callText(m, names.get(0) + "!!", names.subList(1, n));
            else call = callText(m, receiver(qp), names);
            return typeNN(type) + " {" + (n == 0 ? "" : " " + String.join(", ", names) + " ->") + " " + call + " }";
        }
        static String callText(ExecutableElement m, String receiver, List<String> args) {
            String template = memberTemplate(m);
            if (template != null) return fill(template, receiver, args);
            return receiver + "." + id(m.getSimpleName()) + "(" + String.join(", ", args) + ")";
        }
    }
}
