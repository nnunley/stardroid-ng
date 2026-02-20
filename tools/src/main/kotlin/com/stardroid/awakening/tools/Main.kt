package com.stardroid.awakening.tools

fun main(args: Array<String>) {
    val typeIndex = args.indexOf("--type")
    if (typeIndex == -1 || typeIndex + 1 >= args.size) {
        System.err.println("Usage: --type <constellations|stars|messier>")
        System.exit(1)
    }
    when (args[typeIndex + 1]) {
        "constellations" -> ConstellationConverter().convert()
        else -> {
            System.err.println("Unknown type: ${args[typeIndex + 1]}")
            System.exit(1)
        }
    }
}
