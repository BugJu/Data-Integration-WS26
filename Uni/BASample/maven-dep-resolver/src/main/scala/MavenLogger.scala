package uni.dj;

/*
    Trait for logging messages in both Java and Scala components.
 */
trait MavenLogger {
  /*
      Logs an info message.
   */
  def info(msg: String): Unit

  /*
      Logs a debug message.
   */
  def debug(msg: String): Unit

  /*
      Logs a warning message.
   */
  def warn(msg: String): Unit

  /*
      Logs an error message.
   */
  def error(msg: String): Unit
}