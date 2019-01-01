package mdoc.modifiers

class JsMods(info: String) {
  private val mods = info.split(":").toSet
  def isShared: Boolean = mods("shared")
  def isInvisible: Boolean = mods("invisible")
}
