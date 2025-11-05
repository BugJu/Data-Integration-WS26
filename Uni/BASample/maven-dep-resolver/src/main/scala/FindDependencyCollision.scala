package uni.dj;

import org.eclipse.aether.graph.Dependency

import java.io.File
import scala.collection.mutable.ListBuffer

object FindDependencyCollision {
  def findDependencyCollision(directDependenciesWithoutVersioning: ListBuffer[Dependency],
                              libraryDependencies: ListBuffer[Dependency],
                              logger: MavenLogger): Unit = {
    directDependenciesWithoutVersioning.map(d => d.getScope == "compile")
    directDependenciesWithoutVersioning.foreach(d => logger.info(d.getScope))
  }

}
