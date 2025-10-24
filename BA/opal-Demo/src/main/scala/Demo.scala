import org.opalj.br.analyses.Project

object Demo {


  // Demo to illustrate how to iterate classes (fqn), fields, methods, instructions, subtypes, supertypes, etc.
  def main(args: Array[String]): Unit = {
    val jarFile = new java.io.File("pdfbox-2.0.24.jar")

    val p = Project(jarFile)

    println(p.classFilesCount)

    p
      .allProjectClassFiles
      .foreach{ cf =>
        println(cf.fqn)
        cf.fields.foreach{ f =>
          println(s"\t - ${f.name} : ${f.fieldType.toString}")
        }
      }

    p.allMethods.foreach{ m =>
      println(s"${m.classFile.fqn} ${m.name} ${m.descriptor.toJava}")

      if(m.body.isDefined){
        val body = m.body.get

        body.instructions.foreach{ instr =>
          if(instr != null){
            println(s"\t - ${instr.toString}")
          }
        }
      }
    }

    val ch = p.classHierarchy

    p.allProjectClassFiles.foreach{ cf =>
      println(cf.thisType.fqn)
      ch.supertypes(cf.thisType).foreach{ superType =>
        println(s"\t - ${superType.fqn}")
      }
    }





 }
}
