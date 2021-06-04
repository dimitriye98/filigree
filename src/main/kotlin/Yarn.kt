package com.dimitriye.filigree

import com.dimitriye.filigree.model.MappingSetModelFactoryImpl
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import net.fabricmc.lorenztiny.TinyMappingsJoiner
import net.fabricmc.mapping.tree.TinyMappingFactory
import net.fabricmc.mapping.tree.TinyTree
import okio.buffer
import okio.source
import org.cadixdev.lorenz.MappingSet
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

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

private fun yarnURL(minecraftVersion: String, yarnBuild: String): URL {
	return URL(
		"https://maven.modmuss50.me/" +
			"net/fabricmc/yarn/$minecraftVersion+build.$yarnBuild/yarn-$minecraftVersion+build.$yarnBuild-v2.jar"
	)
}

private fun intermediaryURL(version: String): URL {
	return URL(
		"https://maven.modmuss50.me/" +
			"net/fabricmc/intermediary/$version/intermediary-$version-v2.jar"
	)
}

private fun readTinyFromZippedURLAsTinyTree(url: URL, path: String): TinyTree {
	return streamFileFromZippedURL(url, path)
		.let { InputStreamReader(it) }
		.let { BufferedReader(it) }
		.use { TinyMappingFactory.load(it) }
}

fun fetchAndCompileYarnMappings(minecraftVersion: String, yarnBuild: String): MappingSet {
	val intermediary = readTinyFromZippedURLAsTinyTree(
		intermediaryURL(minecraftVersion),
		"mappings/mappings.tiny"
	)
	val yarn = readTinyFromZippedURLAsTinyTree(
		yarnURL(minecraftVersion, yarnBuild),
		"mappings/mappings.tiny"
	)

	return TinyMappingsJoiner(
		intermediary, "official",
		yarn, "named",
		"intermediary"
	).read(MappingSet.create(MappingSetModelFactoryImpl()))
}
