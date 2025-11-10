package uni.dj;

trait MavenLogger {
  def info(msg: String): Unit
  def debug(msg: String): Unit
  def warn(msg: String): Unit
  def error(msg: String): Unit
}