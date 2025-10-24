import org.opalj.br.analyses.Project

object objekt {

  def main(args: Array[String]): Unit = {
    val jarFile = new java.io.File(s"src//main//scala//${args(0)}")
    val p = Project(jarFile)
    p.allProjectClassFiles.foreach{ cf =>
      println(s"Klasse: $cf")
      cf.methodsWithBody.foreach{ method =>
        println(s"---$method---")
        method.body.get.instructions.foreach{ instr =>
          if(instr != null){
            println(instr)
          }
        }
        println()
      }
    }
  }
}
