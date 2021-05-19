package com.dimitriye.filigree

import net.fabricmc.lorenztiny.TinyMappingsJoiner
import net.fabricmc.mapping.tree.TinyMappingFactory
import net.fabricmc.mapping.tree.TinyTree
import org.cadixdev.lorenz.MappingSet
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.IllegalArgumentException
import java.net.URL
import java.util.zip.ZipInputStream

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