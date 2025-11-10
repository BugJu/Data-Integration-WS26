file:///C:/Users/david/Documents/Uni/Uni/BASample/maven-dep-resolver/src/main/scala/OpalFindUnreachableDependencies.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:
	 -scala/collection/JavaConverters.Path#
	 -Path#
	 -scala/Predef.Path#
offset: 332
uri: file:///C:/Users/david/Documents/Uni/Uni/BASample/maven-dep-resolver/src/main/scala/OpalFindUnreachableDependencies.scala
text:
```scala
import org.opalj.br.analyses.Project
import org.opalj.tac.cg.CHACallGraphKey
import org.opalj.br.reader.Java17Framework
import java.io.File
import java.nio.file.{FileSystems, Files}
import scala.collection.JavaConverters._

class OpalFindUnreachableDependencies {

  def findUnreachableDependencies(classFileDirectory: Path@@): Files = {
    val classFiles = Files
      .walk(classFileDirectory)
      .iterator()
      .asScala
      .filter(path => path.toString.endsWith(".class"))
      .toSeq
    return classFiles
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 