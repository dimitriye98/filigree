package com.dimitriye.filigree

import com.dimitriye.filigree.model.MappingSetModelFactoryImpl
import okio.buffer
import okio.source

import org.cadixdev.lorenz.MappingSet
import org.cadixdev.lorenz.io.proguard.ProGuardReader

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

data class LatestMinecraftVersions(val release: String, val snapshot: String)
private data class Version(val id: String, val url: String)
private data class Manifest(val latest: LatestMinecraftVersions, val versions: List<Version>)
private data class Download(val url: String)
private data class Downloads(val client_mappings: Download, val server_mappings: Download)
private data class ManifestEntry(val downloads: Downloads)

private val manifestURL = URL("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json")

private val manifestAdapter by lazy { moshi.adapter(Manifest::class.java) }
private val manifestEntryAdapter by lazy { moshi.adapter(ManifestEntry::class.java) }

private val manifest by lazy {
	manifestURL.openStream().use {
		it.source().buffer().let(manifestAdapter::fromJson) !!
	}
}

val latestVersions by lazy { manifest.latest }

/**
 * Finds the mapping downloads for a given version of minecraft
 */
private fun scrapeMojangManifest(version: String): Downloads {
	val versionEntry = manifest.versions
		.parallelStream()
		.filter { it.id == version }
		.findFirst()
		.orElse(null) // Unwrap Java optional to nullable
		?.url
		?.let(::URL)

		?: throw IllegalArgumentException("Manifest contains no such version $version")

	return versionEntry.openStream()
		.use {
			it.source()
				.buffer()
				.let(manifestEntryAdapter::fromJson)!!
		}
		.downloads
}

private fun readProGuardFromURL(url: URL): MappingSet {
	return url.openStream()
		.let { InputStreamReader(it) }
		.let { BufferedReader(it) }
		.use { ProGuardReader(it).read(MappingSet.create(MappingSetModelFactoryImpl())) }
}

fun fetchAndCompileMojangMappings(version: String): MappingSet {
	val downloads = scrapeMojangManifest(version)

	val clientMappings = downloads.client_mappings.url.let(::URL).let(::readProGuardFromURL)
	val serverMappings = downloads.server_mappings.url.let(::URL).let(::readProGuardFromURL)

	return clientMappings.merge(serverMappings)
}
