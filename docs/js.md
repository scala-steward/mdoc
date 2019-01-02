---
id: js
title: Scala.js
---

Use the `mdoc:js` modifier to compile a code fence with Scala.js and evaluate it
at HTML load time instead of at markdown generation time.

```scala mdoc:js
import org.scalajs.dom.window._
var n = 0
setInterval(() => {
  n += 1
  node.innerHTML = s"tick: $n"
}, 1000)
```
