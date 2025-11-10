file:///C:/Users/david/Documents/Uni/Uni/BASample/maven-dep-resolver/src/main/scala/OpalFindUnreachableDependencies.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:
	 -scala/collection/JavaConverters.path.toString.endsWith.
	 -scala/collection/JavaConverters.path.toString.endsWith#
	 -scala/collection/JavaConverters.path.toString.endsWith().
	 -path/toString/endsWith.
	 -path/toString/endsWith#
	 -path/toString/endsWith().
	 -scala/Predef.path.toString.endsWith.
	 -scala/Predef.path.toString.endsWith#
	 -scala/Predef.path.toString.endsWith().
offset: 639
uri: file:///C:/Users/david/Documents/Uni/Uni/BASample/maven-dep-resolver/src/main/scala/OpalFindUnreachableDependencies.scala
text:
```scala
package uni.dj

import org.opalj.br.analyses.Project
import org.opalj.tac.cg.CHACallGraphKey
import org.opalj.br.reader.Java17Framework
import java.io.File
import java.nio.file.{FileSystems, Files}
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

object OpalFindUnreachableDependencies {
  def findUnreachableDependencies(
      classFileDirectory: String
  ): ListBuffer[String] = {
    val classes = ListBuffer[File]()
    val classFiles = ListBuf
      .walk(FileSystems.getDefault.getPath(classFileDirectory))
      .iterator()
      .asScala
      .filter(path => path.toString.e@@ndsWith(".class"))
      .toSeq
    classFiles.foreach(path => classes += new File(path.toString))
    return classes.toList
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 