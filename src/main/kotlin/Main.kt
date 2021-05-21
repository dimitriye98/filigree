package com.dimitriye.filigree

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.cadixdev.mercury.Mercury
import org.cadixdev.mercury.remapper.MercuryRemapper

import java.io.File
import java.nio.file.Path
import kotlin.collections.ArrayList

val moshi by lazy {
	Moshi.Builder()
		.addLast(KotlinJsonAdapterFactory())
		.build()
}

fun readClassPathFromFile(file: File): List<Path> {
	val out = ArrayList<Path>()
	file.forEachLine {
		out.add(Path.of(it))
	}
	return out
}

fun main(args: Array<String>) {
	val options = Options()

	Option.builder("m")
		.hasArg()
		.longOpt("mc-version")
		.desc("the minecraft version to map against, defaults to latest release")
		.build()
		.let( options::addOption )

	Option.builder("y")
		.hasArg()
		.longOpt("yarn-build")
		.desc("the build of yarn to remap to, defaults to latest")
		.build()
		.let( options::addOption )

	Option.builder("o")
		.hasArg()
		.longOpt("output-dir")
		.desc("the output directory, defaults to [SOURCE_DIRECTORY]_remapped")
		.type(File::class.java)
		.build()
		.let( options::addOption )

	Option.builder("c")
		.hasArg()
		.longOpt("classpath")
		.desc("adds a classpath")
		.type(File::class.java)
		.build()
		.let( options::addOption )

	Option.builder("C")
		.hasArg()
		.longOpt("classpaths")
		.desc("imports a list of classpaths from a file")
		.type(File::class.java)
		.build()
		.let( options::addOption )

	Option.builder("f")
		.longOpt("force")
		.desc("force remap even if output path already exists")
		.build()
		.let( options::addOption )

	Option.builder("h")
		.longOpt("help")
		.desc("display this message")
		.build()
		.let( options::addOption )

	val cli = DefaultParser().parse(options, args, true)
	if (cli.hasOption("help") || cli.args.size != 1) {
		val help = HelpFormatter()
		help.printHelp("filigree [OPTION]... [SOURCE_DIRECTORY]", options)
		return
	}

	val version = cli.getOptionValue("version") ?: latestVersions.release
	val yarnBuild = cli.getOptionValue("yarn-build") ?: latestYarnBuild(version)

	val inputPath = Path.of(cli.args[0])
	val outputPath = cli.getOptionValue("output-dir")?.let(Path::of)
		?: (inputPath.parent ?: Path.of("")).resolve(inputPath.fileName.toString() + "_remapped")

	if (!cli.hasOption("force") && outputPath.toFile().exists()) {
		val outputPathErr = cli.getOptionValue("output-dir")
			?: Path.of("").toAbsolutePath().relativize(outputPath)
		System.err.println("$outputPathErr already exists, if this is intentional, run again with -f")
		System.exit(1)
	}

	val classPaths = ArrayList<Path>()

	cli.getOptionValues("classpath")
		?.map(Path::of)
		?.let(classPaths::addAll)

	cli.getOptionValues("classpaths")
		?.map(::File)
		?.flatMap(::readClassPathFromFile)
		?.let(classPaths::addAll)

	// Forge does a weird thing when using official Mojang mappings
	// where official mappings are used for fields and methods
	// but MCP mappings are used for class names
	// The solution to this is kinda fugly but works:
	// We proceed as follows:
	// MCP->OBF->MOJ->OBF->YARN
	val mcp = fetchMCPMappings(version).reverse() // MCP->OBF
	val moj = fetchAndCompileMojangMappings(version) // MOJ->OBF
	val yarn = fetchAndCompileYarnMappings(version, yarnBuild) // OBF->YARN
	val chain = mcp.merge(moj.reverse()).merge(moj).merge(yarn)

	val mercury = Mercury()

	mercury.classPath.addAll(classPaths)

	mercury.processors.add(MercuryRemapper.create(chain))

	mercury.rewrite(inputPath, outputPath)
}