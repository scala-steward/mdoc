package mdoc.modifiers

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import mdoc.OnLoadContext
import mdoc.PostProcessContext
import mdoc.PreModifierContext
import mdoc.internal.io.ConsoleReporter
import mdoc.internal.livereload.Resources
import mdoc.internal.markdown.CodeBuilder
import mdoc.internal.markdown.Gensym
import mdoc.internal.markdown.MarkdownCompiler
import mdoc.internal.pos.TokenEditDistance
import org.scalajs.core.tools.io.IRFileCache
import org.scalajs.core.tools.io.IRFileCache.VirtualRelativeIRFile
import org.scalajs.core.tools.io.MemVirtualSerializedScalaJSIRFile
import org.scalajs.core.tools.io.VirtualScalaJSIRFile
import org.scalajs.core.tools.io.WritableMemVirtualJSFile
import org.scalajs.core.tools.linker.Linker
import org.scalajs.core.tools.linker.StandardLinker
import org.scalajs.core.tools.logging.Level
import org.scalajs.core.tools.logging.Logger
import org.scalajs.core.tools.sem.Semantics
import scala.collection.mutable.ListBuffer
import scala.meta.Term
import scala.meta.inputs.Input
import scala.meta.io.Classpath
import scala.reflect.io.VirtualDirectory

class JsModifier extends mdoc.PreModifier {
  override val name = "js"

  val target = new VirtualDirectory("(memory)", None)
  var mountNode = "node"
  var linker: Linker = _
  var compiler: MarkdownCompiler = _
  var virtualIrFiles: Seq[VirtualRelativeIRFile] = _
  var classpathHash: Int = 0
  var reporter: mdoc.Reporter = new ConsoleReporter(System.out)
  var gensym = new Gensym()
  var minLevel: Level = Level.Info
  val sjsLogger: Logger = new Logger {
    override def log(level: Level, message: => String): Unit = {
      if (level >= minLevel) {
        if (level == Level.Warn) reporter.info(message)
        else if (level == Level.Error) reporter.info(message)
        else reporter.info(message)
      }
    }
    override def success(message: => String): Unit =
      reporter.info(message)
    override def trace(t: => Throwable): Unit =
      reporter.error(t)
  }

  private val runs = ListBuffer.empty[String]
  private val inputs = ListBuffer.empty[Input]

  def reset(): Unit = {
    runs.clear()
    inputs.clear()
    gensym.reset()
  }

  override def onLoad(ctx: OnLoadContext): Unit = {
    val props = ctx.site
    val classpath = props("js.classpath")
    val scalacOptions = props("js.scalacOptions")
    val newClasspathHash = (classpath, scalacOptions).hashCode()
    // Reuse the  linker and compiler when the classpath+scalacOptions haven't changed
    // to speed up unit tests by nearly 2x.
    if (classpathHash != newClasspathHash) {
      linker = StandardLinker(
        StandardLinker
          .Config()
          .withSemantics(Semantics.Defaults)
          .withSourceMap(false)
          .withClosureCompilerIfAvailable(false)
      )
      classpathHash = newClasspathHash
      compiler = new MarkdownCompiler(classpath, scalacOptions, target)
      virtualIrFiles = {
        val irCache = new IRFileCache
        val irContainer =
          IRFileCache.IRContainer.fromClasspath(Classpath(classpath).entries.map(_.toFile))
        val cache = irCache.newCache
        cache.cached(irContainer)
      }
    }
    mountNode = props.getOrElse("js.mountNode", mountNode)
    reporter = ctx.reporter
    minLevel = props.get("js.level") match {
      case None => Level.Info
      case Some("info") => Level.Info
      case Some("warn") => Level.Warn
      case Some("error") => Level.Error
      case Some("debug") => Level.Debug
      case Some(unknown) =>
        reporter.warning(s"unknown 'js.level': $unknown")
        Level.Info
    }
  }

  override def postProcess(ctx: PostProcessContext): String = {
    if (runs.isEmpty) {
      reset()
      ""
    } else {
      val code = new CodeBuilder()
      val wrapped = code
        .println("object mdocjs {")
        .foreach(runs)(code.println)
        .println("}")
        .toString
      val input = Input.VirtualFile(ctx.relativePath.toString(), wrapped)
      val edit = TokenEditDistance.fromInputs(inputs, input)
      val oldErrors = ctx.reporter.errorCount
      compiler.compileSources(input, ctx.reporter, edit)
      val hasErrors = ctx.reporter.errorCount > oldErrors
      reset()
      val sjsir = for {
        x <- target.toList
        if x.name.endsWith(".sjsir")
      } yield {
        val f = new MemVirtualSerializedScalaJSIRFile(x.path)
        f.content = x.toByteArray
        f: VirtualScalaJSIRFile
      }
      if (sjsir.isEmpty) {
        if (!hasErrors) {
          ctx.reporter.error("Scala.js compilation failed")
        }
        ""
      } else {
        val output = WritableMemVirtualJSFile("output.js")
        linker.link(virtualIrFiles ++ sjsir, Nil, output, sjsLogger)
        val outfile = ctx.outputFile.resolveSibling(_ + ".js")
        val filename = outfile.toNIO.getFileName.toString
        Files.createDirectories(outfile.toNIO.getParent)
        Files.write(
          outfile.toNIO,
          output.content.getBytes(StandardCharsets.UTF_8)
        )
        val mdocjs = Resources.readPath("/mdoc.js")
        Files.write(
          outfile.toNIO.resolveSibling("mdoc.js"),
          mdocjs.getBytes(StandardCharsets.UTF_8)
        )
        new CodeBuilder()
          .println(s"""<script type="text/javascript" src="$filename" defer></script>""")
          .println(s"""<script type="text/javascript" src="mdoc.js" defer></script>""")
          .toString
      }
    }
  }
  override def process(ctx: PreModifierContext): String = {
    val separator = "\n---\n"
    val mods = new JsMods(ctx.info)
    val text = ctx.originalCode.text
    val separatorIndex = text.indexOf(separator)
    val (body, input) =
      if (separatorIndex < 0) {
        ("", ctx.originalCode)
      } else {
        val sliced = Input.Slice(
          ctx.originalCode,
          separatorIndex + separator.length,
          ctx.originalCode.chars.length
        )
        (
          text.substring(0, separatorIndex),
          sliced
        )
      }
    val run = gensym.fresh("run")
    inputs += input
    val id = s"mdoc-js-$run"
    val mountNodeParam = Term.Name(mountNode)
    val code: String =
      if (mods.isShared) {
        input.text
      } else {
        new CodeBuilder()
          .println(s""" @_root_.scala.scalajs.js.annotation.JSExportTopLevel("$id") """)
          .println(s"""def $run($mountNodeParam: _root_.org.scalajs.dom.raw.Element): Unit = {""")
          .println(input.text)
          .println("}")
          .toString
      }
    runs += code
    new CodeBuilder()
      .printlnIf(!mods.isInvisible, s"```scala\n${input.text}\n```")
      .printlnIf(!mods.isShared, s"""<div id="$id" data-mdoc-js>$body</div>""")
      .toString
  }
}
