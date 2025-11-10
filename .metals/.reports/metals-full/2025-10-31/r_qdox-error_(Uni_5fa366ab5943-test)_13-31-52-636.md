error id: file:///C:/Users/david/Documents/Uni/BA/opal/OPAL/bi/src/test/fixtures-java/projects/type_annotations/RVTypeAnnotationUser.java
file:///C:/Users/david/Documents/Uni/BA/opal/OPAL/bi/src/test/fixtures-java/projects/type_annotations/RVTypeAnnotationUser.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[22,44]

error in qdox parser
file content:
```java
offset: 616
uri: file:///C:/Users/david/Documents/Uni/BA/opal/OPAL/bi/src/test/fixtures-java/projects/type_annotations/RVTypeAnnotationUser.java
text:
```scala
/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package type_annotations;

import java.io.Serializable;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * This class tests some (corner) cases related to type annotations.
 *
 * NOTE<br />
 * This class is only meant to be (automatically) compiled by OPAL's build script.
 *
 * @author Michael Eichberg
 */
public abstract class RVTypeAnnotationUser<@@@RVTypeAnnotation T extends @RVTypeAnnotation Serializable & Cloneable>
        implements List<@RVTypeAnnotation Object>, @RVTypeAnnotation("superinterface") Serializable {

    public @RVTypeAnnotation("on nested class declaration") class Nested {
        public @RVTypeAnnotation("on nested class declaration") class SubNested {
            public @RVTypeAnnotation("on nested class declaration") class SubSubNested {
            }
        }
    }

    private static final Object process(Object o) {return o;} // just a helper method

    private static final @RVTypeAnnotation("field declaration") long serialVersionUID = 1L;

    public List<@RVTypeAnnotation("annotation of generic type parameter") T> ser;

    @SuppressWarnings("all")
    public Object onNestedTypeAnnotations() throws Exception {
        Nested nested =  new RVTypeAnnotationUser.@RVTypeAnnotation("on nested class instantiation") Nested();
        Nested.SubNested subNested = nested.new @RVTypeAnnotation("on nested^2 class instantiation") SubNested();
        Nested.SubNested.SubSubNested subSubNested = subNested.new @RVTypeAnnotation("on nested^2 class instantiation") SubSubNested();

        return subSubNested;
    }

    public Object localVariableDeclarationTypeAnnotations() throws Exception {
        @RVTypeAnnotation("local variable annotation")
        List<@RVTypeAnnotation("type parameter annotation") T> l = new ArrayList<>();
        return l;
    }

    public void wildcardsRelatedTypeAnnotation() throws Exception {
        List<@RVTypeAnnotation ? extends Cloneable> l = new ArrayList<>();
        process(l);
    }

    @SuppressWarnings("unchecked")
    public void arrayRelatedTypeAnnotation() throws Exception {
        @RVTypeAnnotation("array as such") int[] @RVTypeAnnotation("2nd dimension of array")[] ls = new int[10][1];
        process(ls);

        List<@RVTypeAnnotation("type parameter annotation of array of generic type") Serializable>[] lgs = new List[10];
        process(lgs);
    }

    public Supplier<Vector<?>> instanceCreationRelatedTypeAnnotation() throws Exception {
        List<?> l = new @RVTypeAnnotation ArrayList<>();
        process(l);
        return (@RVTypeAnnotation Vector::new);
    }

    public Supplier<Object> methodCallRelatedTypeAnnotation() throws Exception {
        return (@RVTypeAnnotation("receiver class") System::lineSeparator);
    }

    public @RVTypeAnnotation("return type") Function<@RVTypeAnnotation("first parameter of generic type") Object,  @RVTypeAnnotation("second parameter of generic type") Integer> methodSignatureRelatedTypeAnnotations() throws Exception {
        List<Object> l = new ArrayList<>();
        return (l::indexOf);
    }

    @SuppressWarnings("all")
    public <X extends @RVTypeAnnotation Serializable & @RVTypeAnnotation("annotation of second type of intersection type") Cloneable> void typeCheckRelatedTypeAnnotations(X x) {
        // annotated type test
        if(x instanceof @RVTypeAnnotation("annotated instanceof") List) {
            List<X> l = (@RVTypeAnnotation("annotated type cast") List< @RVTypeAnnotation("annotated generic type variable in (unsafe) type cast")X>) x;
            process(l);
        }

        // annotated type cast
        if(x instanceof List) {
            Object l = (Serializable & @RVTypeAnnotation("annotation of second type of a case to an intersection type") Cloneable) x;
            process(l);
        }
    }

    public String doIt(File file) throws Exception{
        try (
            @RVTypeAnnotation("resource variable") BufferedReader br =
                new BufferedReader(new FileReader(file))
        ) {
            return br.readLine();
        }
    }

    public String doArray(File[] file) throws Exception{
        @RVTypeAnnotation("the array")File[] newFiles =  file.clone();

        Object[]@RVTypeAnnotation("an inner array")[] os = new @RVTypeAnnotation("a new array") Object[10]@RVTypeAnnotation("a new inner array")[5];
        process(os);

        return newFiles[0].toString();
    }
}

```

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.metals.SemanticdbDefinition$.foreachWithReturnMtags(SemanticdbDefinition.scala:99)
	scala.meta.internal.metals.Indexer.indexSourceFile(Indexer.scala:546)
	scala.meta.internal.metals.Indexer.$anonfun$indexWorkspaceSources$7(Indexer.scala:366)
	scala.meta.internal.metals.Indexer.$anonfun$indexWorkspaceSources$7$adapted(Indexer.scala:361)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:630)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:628)
	scala.collection.AbstractIterator.foreach(Iterator.scala:1313)
	scala.collection.parallel.ParIterableLike$Foreach.leaf(ParIterableLike.scala:938)
	scala.collection.parallel.Task.$anonfun$tryLeaf$1(Tasks.scala:52)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.util.control.Breaks$$anon$1.catchBreak(Breaks.scala:97)
	scala.collection.parallel.Task.tryLeaf(Tasks.scala:55)
	scala.collection.parallel.Task.tryLeaf$(Tasks.scala:49)
	scala.collection.parallel.ParIterableLike$Foreach.tryLeaf(ParIterableLike.scala:935)
	scala.collection.parallel.AdaptiveWorkStealingTasks$AWSTWrappedTask.internal(Tasks.scala:159)
	scala.collection.parallel.AdaptiveWorkStealingTasks$AWSTWrappedTask.internal$(Tasks.scala:156)
	scala.collection.parallel.AdaptiveWorkStealingForkJoinTasks$AWSFJTWrappedTask.internal(Tasks.scala:304)
	scala.collection.parallel.AdaptiveWorkStealingTasks$AWSTWrappedTask.compute(Tasks.scala:149)
	scala.collection.parallel.AdaptiveWorkStealingTasks$AWSTWrappedTask.compute$(Tasks.scala:148)
	scala.collection.parallel.AdaptiveWorkStealingForkJoinTasks$AWSFJTWrappedTask.compute(Tasks.scala:304)
	java.base/java.util.concurrent.RecursiveAction.exec(RecursiveAction.java:194)
	java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:511)
	java.base/java.util.concurrent.ForkJoinPool.helpJoin(ForkJoinPool.java:2304)
	java.base/java.util.concurrent.ForkJoinTask.awaitDone(ForkJoinTask.java:499)
	java.base/java.util.concurrent.ForkJoinTask.join(ForkJoinTask.java:666)
	scala.collection.parallel.ForkJoinTasks$FJTWrappedTask.sync(Tasks.scala:243)
	scala.collection.parallel.ForkJoinTasks$FJTWrappedTask.sync$(Tasks.scala:243)
	scala.collection.parallel.AdaptiveWorkStealingForkJoinTasks$AWSFJTWrappedTask.sync(Tasks.scala:304)
	scala.collection.parallel.AdaptiveWorkStealingTasks$AWSTWrappedTask.internal(Tasks.scala:173)
	scala.collection.parallel.AdaptiveWorkStealingTasks$AWSTWrappedTask.internal$(Tasks.scala:156)
	scala.collection.parallel.AdaptiveWorkStealingForkJoinTasks$AWSFJTWrappedTask.internal(Tasks.scala:304)
	scala.collection.parallel.AdaptiveWorkStealingTasks$AWSTWrappedTask.compute(Tasks.scala:149)
	scala.collection.parallel.AdaptiveWorkStealingTasks$AWSTWrappedTask.compute$(Tasks.scala:148)
	scala.collection.parallel.AdaptiveWorkStealingForkJoinTasks$AWSFJTWrappedTask.compute(Tasks.scala:304)
	java.base/java.util.concurrent.RecursiveAction.exec(RecursiveAction.java:194)
	java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:511)
	java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1450)
	java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:2019)
	java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:187)
```
#### Short summary: 

QDox parse error in file:///C:/Users/david/Documents/Uni/BA/opal/OPAL/bi/src/test/fixtures-java/projects/type_annotations/RVTypeAnnotationUser.java