package com.dimitriye.filigree

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

import okio.buffer
import okio.source

import org.cadixdev.lorenz.MappingSet
import org.cadixdev.lorenz.io.proguard.ProGuardReader

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

data class Version(val id: String, val url: String)
data class Manifest(val versions: List<Version>)
data class Download(val url: String)
data class Downloads(val client_mappings: Download, val server_mappings: Download)
data class ManifestEntry(val downloads: Downloads)

val manifestURL = URL("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json")

fun scrapeMojangManifest(version: String): Downloads {
	val moshi = Moshi.Builder()
		.addLast(KotlinJsonAdapterFactory())
		.build()

	val manifestAdapter = moshi.adapter(Manifest::class.java)
	val manifestEntryAdapter = moshi.adapter(ManifestEntry::class.java)

	val manifest = manifestURL.openStream().use {
		it.source().buffer().let(manifestAdapter::fromJson) !!
	}

	val versionEntry = manifest.versions
		.parallelStream()
		.filter{ it.id == version }
		.findFirst()
		.orElse(null)
		?.url
		?.let(::URL)

		?: throw IllegalArgumentException("Manifest contains no such version $version")

	val downloads = versionEntry.openStream()
		.use {
			it.source().buffer().let(manifestEntryAdapter::fromJson) !!
		}
		.downloads

	return downloads
}

fun readProGuardFromURL(url: URL): MappingSet {
	return url.openStream().use {
		InputStreamReader(it).use {
			BufferedReader(it).use {
				ProGuardReader(it).read()
			}
		}
	}
}

fun fetchAndCompileMojangMappings(version: String): MappingSet {
	val downloads = scrapeMojangManifest(version)

	val clientMappings = downloads.client_mappings.url.let(::URL).let(::readProGuardFromURL)
	val serverMappings = downloads.server_mappings.url.let(::URL).let(::readProGuardFromURL)

	return clientMappings.merge(serverMappings)
}