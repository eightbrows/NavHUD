package io.github.eightbrows.navhud.core.io

/**
 * 選んだファイルが、読み込もうとした種類と違う（20261103-D01）。WP リストとして軌跡を選んだら actual = TRACK、
 * 軌跡として WP リストを選んだら actual = WAYPOINTS。fileName は画面に出すファイルの名前。読み込みはしない。
 */
class FileKindException(val actual: FileKind, val fileName: String) : Exception("file kind: $actual ($fileName)")
