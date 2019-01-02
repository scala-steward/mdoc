---
id: js
title: Scala.js
---

Use the `mdoc:js` modifier to write dynamic and interactive documentation with
Scala.js.

```scala mdoc:js
Loading...
---
org.scalajs.dom.window.setInterval(() => {
  val date = new scala.scalajs.js.Date()
  val time = s"${date.getHours}h${date.getMinutes}m${date.getSeconds}s"
  node.innerHTML = s"-> Current time is $time <-"
}, 1000)
```

Code fences with the `mdoc:js` modifier compile to JavaScript and evaluate at
HTML load time instead of at markdown generation time.

Each `mdoc:js` code fence has access to a variable `node`, which is an empty DOM
element.

## Installation

The `mdoc:js` modifier requires custom installation steps.

### sbt-mdoc

First, install sbt-mdoc using the
[regular installation instructions](installation.md#sbt).

Next, update the `mdocJS` setting to point to a Scala.js project that has
`scalajs-dom` as a library dependency.

```diff
// build.sbt
lazy val jsapp = project
  .settings(
+   libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.6"
  )
  .enablePlugins(ScalaJSPlugin)
lazy val docs = project
  .in(file("myproject-docs"))
  .settings(
+   mdocJS := Some(jsapp)
  )
  .enablePlugins(MdocPlugin)
```

### Command-line

First add a dependency on the `org.scalameta:mdoc-js` library.

```diff
 coursier launch \
     org.scalameta:mdoc_@SCALA_BINARY_VERSION@:@VERSION@ \
+    org.scalameta:mdoc-js_@SCALA_BINARY_VERSION@:@VERSION@
```

This dependency enables the `mdoc:js` modifier which requires the site variables
`js-classpath` and `js-scalacOptions`.

```diff
 coursier launch \
     org.scalameta:mdoc_@SCALA_BINARY_VERSION@:@VERSION@ \
     org.scalameta:mdoc-js_@SCALA_BINARY_VERSION@:@VERSION@ -- \
+  --site.js-classpath CLASSPATH_OF_SCALAJS_PROJECT
+  --site.js-scalacOption OPTIONS_OF_SCALAJS_PROJECT
```

- `js-scalacOptions` must contain `-Xplugin:path/to/scalajs-compiler.jar` to
  enable the Scala.js compiler. - `js-classpath` value must include a dependency
  on the library `org.scala-js:scalajs-dom`

## Modifiers

The following modifiers can be combined with `mdoc:js` code fences to customize
the rendered output.

### `:shared`

By default, each code fence is isolated from other code fences. Use the
`:shared` modifier to reuse imports or variables between code fences.

```scala mdoc:js:shared:invisible
import org.scalajs.dom.window.setInterval
import scala.scalajs.js.Date
```

````scala mdoc:mdoc
```scala mdoc:js:shared
import org.scalajs.dom.window.setInterval
import scala.scalajs.js.Date
```
```scala mdoc:js
setInterval(() => {
  node.innerHTML = new Date().toString()
}, 1000)
```
````

```scala mdoc:js
Loading <code>:shared</code> example...
---
setInterval(() => {
  val date = new Date().toString()
  node.innerHTML = s"Shared date $date"
}, 1000)
```

Without `:shared`, the example above results in a compile error.

````scala mdoc:mdoc:crash
```scala mdoc:js
import scala.scalajs.js.Date
```
```scala mdoc:js
new Date()
```
````

### `:invisible`

By default, the code example is rendered on the page. Use `:invisible` to hide
the code example so that only the empty div is generated.

````scala mdoc:mdoc
```scala mdoc:js:invisible
var n = 0
org.scalajs.dom.window.setInterval(() => {
  n += 1
  node.innerHTML = s"Invisible tick: $n"
}, 1000)
```
````

```scala mdoc:js:invisible
Loading <code>:invisible</code> example...
---
var n = 0
setInterval(() => {
  n += 1
  node.innerHTML = s"Invisible tick: ${n}"
}, 1000)
```

## Custom loading HTML

By default, an empty div is generated for each `mdoc:js` code fence and this DOM
element is available as the variable `node`. Update the code fence with a `---`
separator to customize the HTML inside the div node.

````scala mdoc:mdoc
```scala mdoc:js
<p>I am a custom <code>loader</code></p>
---
println(node.innerHTML)
```
````

```scala mdoc:js
<p>I am a custom <code>loader</code></p>
---
println(node.innerHTML)
```

Replace the node's `innerHTML` to make the HTML disappear once the document has
loaded.

```scala mdoc:js
Loading...
---
node.innerHTML = "I am loaded"
```

## Generated code
