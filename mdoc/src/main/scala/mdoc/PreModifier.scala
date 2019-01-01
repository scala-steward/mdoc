package mdoc

import java.util.ServiceLoader
import mdoc.internal.cli.Settings
import metaconfig.ConfDecoder
import metaconfig.ConfEncoder
import metaconfig.ConfError
import metaconfig.generic.Surface
import scala.meta.inputs.Input
import scala.meta.io.AbsolutePath
import scala.collection.JavaConverters._
import scala.meta.io.RelativePath

trait PreModifier {
  val name: String
  def process(ctx: PreModifierContext): String
  def postProcess(ctx: PostProcessContext): String = ""
}

object PreModifier {
  def default(): List[PreModifier] = default(this.getClass.getClassLoader)
  def default(classLoader: ClassLoader): List[PreModifier] =
    ServiceLoader.load(classOf[PreModifier], classLoader).iterator().asScala.toList
  implicit val surface: Surface[Settings] = new Surface(Nil)
  implicit val decoder: ConfDecoder[PreModifier] =
    ConfDecoder.instanceF[PreModifier](_ => ConfError.message("unsupported").notOk)
  implicit val encoder: ConfEncoder[PreModifier] =
    ConfEncoder.StringEncoder.contramap(mod => s"<${mod.name}>")
}

final class PostProcessContext private[mdoc] (
    val reporter: Reporter,
    val relativePath: RelativePath,
    private[mdoc] val settings: Settings
) {
  def inputFile: AbsolutePath = inDirectory.resolve(relativePath)
  def outputFile: AbsolutePath = outDirectory.resolve(relativePath)
  def inDirectory: AbsolutePath = settings.in
  def outDirectory: AbsolutePath = settings.out
}

final class PreModifierContext private[mdoc] (
    val info: String,
    val originalCode: Input,
    val reporter: Reporter,
    val relativePath: RelativePath,
    private[mdoc] val settings: Settings
) {
  def inputFile: AbsolutePath = inDirectory.resolve(relativePath)
  def outputFile: AbsolutePath = outDirectory.resolve(relativePath)
  def inDirectory: AbsolutePath = settings.in
  def outDirectory: AbsolutePath = settings.out
}
