---
id: js
title: Scala.js
---

# Scala.js

This is a Scala.js guide.

## Imports

Add this import.

```scala mdoc:js:shared
import org.scalajs.dom.window._
def name = "a"
```

## `setInterval`

The `setInterval` method runs a function at every interval.

```scala mdoc:js
var n = 0
setInterval(() => {
  node.innerHTML = s"ticks: $n"
  n += 1
}, 1000)
```

## `setTimeout`

The `setTimeout` method runs a function after a given delay.

```scala mdoc:js
Loading...
---
println(name)
var n = 0
def loop(): Unit = {
  setTimeout(() => {
    n += 1
    node.innerHTML = s"ticks: $n"
    loop()
  }, 2000)
}
loop()
```
