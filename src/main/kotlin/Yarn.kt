package com.dimitriye.filigree

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import net.fabricmc.lorenztiny.TinyMappingsJoiner
import net.fabricmc.mapping.tree.TinyMappingFactory
import net.fabricmc.mapping.tree.TinyTree
import okio.buffer
import okio.source
import org.cadixdev.lorenz.MappingSet
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.IllegalArgumentException
import java.net.URL
import java.util.zip.ZipInputStream

private val yarnManifestURL = URL("https://maven.modmuss50.me/net/fabricmc/yarn/versions.json")

private val yarnManifestAdapter: JsonAdapter<Map<String, List<String>>> = Types.newParameterizedType(
	Map::class.java,
	String::class.java,
	Types.newParameterizedType(List::class.java, String::class.java)
).let(moshi::adapter)

private val yarnManifest: Map<String, List<String>> by lazy {
	yarnManifestURL.openStream().use {
		it.source().buffer().let(yarnManifestAdapter::fromJson) !!
	}
}

fun latestYarnBuild(version: String): String {
	return yarnManifest[version]?.last()
		?: throw NoSuchElementException("No yarn builds for Minecraft version $version")
}

fun yarnURL(minecraftVersion: String, yarnBuild: String): URL {
	return URL(
		"https://maven.modmuss50.me/" +
			"net/fabricmc/yarn/$minecraftVersion+build.$yarnBuild/yarn-$minecraftVersion+build.$yarnBuild-v2.jar"
	)
}

fun intermediaryURL(version: String): URL {
	return URL(
		"https://maven.modmuss50.me/" +
			"net/fabricmc/intermediary/$version/intermediary-$version-v2.jar"
	)
}

fun readTinyFromZippedURLAsTinyTree(url: URL, path: String): TinyTree {
	url.openStream().use {
		BufferedInputStream(it).use {
			ZipInputStream(it).use {
				var entry = it.nextEntry
				while (entry != null) {
					if (entry.name == path) {
						BufferedReader(InputStreamReader(it)).use {
							return TinyMappingFactory.load(it)
						}
					}
					entry = it.nextEntry
				}
			}
		}
	}

	throw IllegalArgumentException("URL $url does not contain file $path")
}

val readTiny = { url: URL -> readTinyFromZippedURLAsTinyTree(url, "mappings/mappings.tiny") }
fun fetchAndCompileYarnMappings(minecraftVersion: String, yarnBuild: String): MappingSet {
	val intermediary = intermediaryURL(minecraftVersion)
		.let(readTiny)

	val yarn = yarnURL(minecraftVersion, yarnBuild)
		.let(readTiny)

	return TinyMappingsJoiner(
		intermediary, "official",
		yarn, "named",
		"intermediary"
	).read()
}