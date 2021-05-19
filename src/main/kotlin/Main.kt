package com.dimitriye.filigree

import org.cadixdev.mercury.Mercury
import org.cadixdev.mercury.remapper.MercuryRemapper

import java.io.File
import java.nio.file.Path
import kotlin.collections.ArrayList

fun readClassPathFromFile(file: File): List<Path> {
	val out = ArrayList<Path>()
	file.forEachLine {
		out.add(Path.of(it))
	}
	return out
}

fun main(args: Array<String>) {
	val argsSlice: List<String>
	val version: String
	if (args[0] == "-v") {
		version = args[1]
		argsSlice = args.slice(2..args.size)
	} else {
		version = "1.16.5"
		argsSlice = args.asList()
	}

	val moj = fetchAndCompileMojangMappings(version) // MOJ->OBF
//	val mcp = fetchMCPMappings("1.16.5").reverse() // MCP->OBF
//	val mcpMerged = moj.merge(mcp)

	val yarn = fetchAndCompileYarnMappings(version, "9") // OBF->YARN
	val mojToYarn = moj.merge(yarn)

	val mercury = Mercury()

	readClassPathFromFile(File("classpath.txt"))
		.let(mercury.classPath::addAll)

	mercury.processors.add(MercuryRemapper.create(mojToYarn))

	mercury.rewrite(Path.of(args[0]), Path.of(args[1]))
}