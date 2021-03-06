package compiler;

import javax.swing.*;
import javax.tools.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;

public class CompilerTest {

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        final List<ByteArrayJavaClass> classFileObjects = new ArrayList<>();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        JavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        fileManager = new ForwardingJavaFileManager<JavaFileManager>(fileManager) {
            public JavaFileObject getJavaFileForOutput(Location location, final String className,
                                                       JavaFileObject.Kind kind, FileObject sibling) throws IOException {
                if (className.startsWith("x.")) {
                    ByteArrayJavaClass fileObject = new ByteArrayJavaClass(className);
                    classFileObjects.add(fileObject);
                    return fileObject;
                } else return super.getJavaFileForOutput(location, className, kind, sibling);
            }
        };

        String frameClassName = args.length == 0 ? "buttons2.ButtonFrame" : args[0];
        JavaFileObject source = buildSource(frameClassName);
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, null,
                null, Arrays.asList(source));
        Boolean result = task.call();

        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics())
            System.out.println(d.getKind() + ": " + d.getMessage(null));
        fileManager.close();
        if (!result) {
            System.out.println("Compilation failed.");
            System.exit(1);
        }

        EventQueue.invokeLater(() -> {
            try {
                Map<String, byte[]> byteCodeMap = new HashMap<>();
                for (ByteArrayJavaClass cl : classFileObjects)
                    byteCodeMap.put(cl.getName().substring(1), cl.getBytes());
                ClassLoader loader = new MapClassLoader(byteCodeMap);
                JFrame frame = (JFrame) loader.loadClass("x.Frame").newInstance();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setTitle("compiler.CompilerTest");
                frame.setVisible(true);
            } catch (IllegalAccessException | InstantiationException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        });
    }

    /*
     * Builds the source for the subclass that implements the addEventHandlers method.
     * @return a file object containing the source in a string builder
     */
    static JavaFileObject buildSource(String superclassName) throws IOException, ClassNotFoundException {
        StringBuilderJavaSource source = new StringBuilderJavaSource("x.Frame");
        source.append("package x;\n");
        source.append("public class Frame extends " + superclassName + " {");
        source.append("protected void addEventHandlers() {");
        final Properties props = new Properties();
        Class<?> clazz = Class.forName(superclassName);
        props.load(clazz.getResourceAsStream("/action.properties"));
        for (Map.Entry<Object, Object> e : props.entrySet()) {
            String beanName = (String) e.getKey();
            String eventCode = (String) e.getValue();
            source.append(beanName + ".addActionListener(new java.awt.event.ActionListener() {");
            source.append("public void actionPerformed(java.awt.event.ActionEvent event) {");
            source.append(eventCode);
            source.append("} } );");
        }
        source.append("} }");
        return source;
    }
}
