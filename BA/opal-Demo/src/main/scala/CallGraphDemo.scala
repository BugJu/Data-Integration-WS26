import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.opalj.BaseConfig
import org.opalj.ai.domain.l2.DefaultPerformInvocationsDomainWithCFGAndDefUse
import org.opalj.ai.fpcf.properties.AIDomainFactoryKey
import org.opalj.br.analyses.Project
import org.opalj.br.analyses.Project.JavaClassFileReader
import org.opalj.br.fpcf.PropertyStoreKey
import org.opalj.fpcf.PropertyStore
import org.opalj.log.GlobalLogContext
import org.opalj.tac.cg.RTACallGraphKey

import java.io.File
import java.net.URL

object CallGraphDemo {
  def main(args: Array[String]) = {

    implicit val config: Config =
      BaseConfig.withValue(
        "org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis",
        ConfigValueFactory.fromAnyRef("org.opalj.br.analyses.cg.LibraryEntryPointsFinder")
      ).withValue(
        "org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis",
        ConfigValueFactory.fromAnyRef("org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder")
      )

    val theFile = new File("pdfbox-2.0.24.jar")
    val project = Project(theFile, GlobalLogContext, config)


    val performInvocationsDomain = classOf[DefaultPerformInvocationsDomainWithCFGAndDefUse[_]]

    project.updateProjectInformationKeyInitializationData(AIDomainFactoryKey) {
      case None               ⇒ Set(performInvocationsDomain)
      case Some(requirements) ⇒ requirements + performInvocationsDomain
    }

    val cg = project.get(RTACallGraphKey)

    println(s"Call graph has ${cg.numEdges} edge(s)")
    cg.reachableMethods.filter(c => c.method.name == "transform").foreach(c => {
      println(s"${c.method.declaringClassType.fqn}.${c.method.name}")
      cg.calleesOf(c.method).foreach(p => {
        p._2.foreach(u => {
          println(s"--> ${u.method}")
        })
      })
    })

  }
}
